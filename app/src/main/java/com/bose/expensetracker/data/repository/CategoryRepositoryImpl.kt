package com.bose.expensetracker.data.repository

import com.bose.expensetracker.data.local.dao.CategoryDao
import com.bose.expensetracker.data.local.entity.SyncStatus
import com.bose.expensetracker.data.mapper.toDomain
import com.bose.expensetracker.data.mapper.toEntity
import com.bose.expensetracker.data.preferences.SandboxPreferences
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val firestoreDataSource: FirestoreDataSource,
    private val sandboxPreferences: SandboxPreferences
) : CategoryRepository {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var syncJob: Job? = null
    private var syncRefCount = 0
    private var currentSyncHouseholdId: String? = null

    override fun getCategories(householdId: String): Flow<List<Category>> =
        categoryDao.getAllCategories(householdId).map { entities ->
            entities.map { it.toDomain() }.distinctBy { it.name }
        }

    override suspend fun getCategoryById(id: String): Category? =
        categoryDao.getCategoryById(id)?.toDomain()

    override suspend fun addCategory(category: Category): Result<Unit> = runCatching {
        if (sandboxPreferences.isSandboxCached) {
            categoryDao.insert(category.toEntity(SyncStatus.SYNCED))
            return@runCatching
        }
        categoryDao.insert(category.toEntity(SyncStatus.PENDING_CREATE))
        try {
            firestoreDataSource.addCategory(category.householdId, category)
            categoryDao.updateSyncStatus(category.id, SyncStatus.SYNCED)
        } catch (_: Exception) { }
    }

    override suspend fun updateCategory(category: Category): Result<Unit> = runCatching {
        if (sandboxPreferences.isSandboxCached) {
            categoryDao.update(category.toEntity(SyncStatus.SYNCED))
            return@runCatching
        }
        categoryDao.update(category.toEntity(SyncStatus.PENDING_UPDATE))
        try {
            firestoreDataSource.updateCategory(category.householdId, category)
            categoryDao.updateSyncStatus(category.id, SyncStatus.SYNCED)
        } catch (_: Exception) { }
    }

    override suspend fun deleteCategory(id: String): Result<Unit> = runCatching {
        val category = categoryDao.getCategoryById(id) ?: return@runCatching
        if (sandboxPreferences.isSandboxCached) {
            categoryDao.deleteById(id)
            return@runCatching
        }
        categoryDao.update(category.copy(syncStatus = SyncStatus.PENDING_DELETE))
        try {
            firestoreDataSource.deleteCategory(category.householdId, id)
            categoryDao.deleteById(id)
        } catch (_: Exception) { }
    }

    private fun presetId(householdId: String, name: String): String =
        "preset_${householdId}_${name.lowercase().replace("/", "_").replace(" ", "_")}"

    override suspend fun seedPresetCategories(householdId: String) {
        val existing = categoryDao.getAllCategoriesOnce(householdId)

        // Clean up duplicates: keep first occurrence per name, delete the rest
        existing.groupBy { it.name }.forEach { (_, dupes) ->
            if (dupes.size > 1) {
                dupes.drop(1).forEach { dup ->
                    categoryDao.deleteById(dup.id)
                    try { firestoreDataSource.deleteCategory(householdId, dup.id) } catch (_: Exception) { }
                }
            }
        }

        // Guard: don't re-seed if presets already exist
        if (existing.any { it.isPreset }) return

        val presets = listOf(
            Category(presetId(householdId, "Food"), "Food", "restaurant", 0xFF4CAF50, true, householdId),
            Category(presetId(householdId, "Groceries"), "Groceries", "shopping_cart", 0xFF8BC34A, true, householdId),
            Category(presetId(householdId, "Transport"), "Transport", "directions_car", 0xFF2196F3, true, householdId),
            Category(presetId(householdId, "Rent/Home Loan"), "Rent/Home Loan", "home", 0xFFFF9800, true, householdId),
            Category(presetId(householdId, "Bills"), "Bills", "receipt_long", 0xFFF44336, true, householdId),
            Category(presetId(householdId, "Family"), "Family", "family_restroom", 0xFFE91E63, true, householdId),
            Category(presetId(householdId, "Entertainment"), "Entertainment", "movie", 0xFF9C27B0, true, householdId),
            Category(presetId(householdId, "Misc"), "Misc", "more_horiz", 0xFF607D8B, true, householdId)
        )
        presets.forEach { category ->
            categoryDao.insert(category.toEntity(SyncStatus.PENDING_CREATE))
            try {
                firestoreDataSource.addCategory(householdId, category)
                categoryDao.updateSyncStatus(category.id, SyncStatus.SYNCED)
            } catch (_: Exception) { }
        }
    }

    override suspend fun syncPendingCategories() {
        val pending = categoryDao.getPendingSyncCategories()
        for (entity in pending) {
            try {
                when (entity.syncStatus) {
                    SyncStatus.PENDING_CREATE -> {
                        firestoreDataSource.addCategory(entity.householdId, entity.toDomain())
                        categoryDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
                    }
                    SyncStatus.PENDING_UPDATE -> {
                        firestoreDataSource.updateCategory(entity.householdId, entity.toDomain())
                        categoryDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
                    }
                    SyncStatus.PENDING_DELETE -> {
                        firestoreDataSource.deleteCategory(entity.householdId, entity.id)
                        categoryDao.deleteById(entity.id)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override fun startRealtimeSync(householdId: String) {
        if (sandboxPreferences.isSandboxCached) return
        if (syncJob?.isActive == true && currentSyncHouseholdId == householdId) {
            syncRefCount++
            return
        }
        syncJob?.cancel()
        currentSyncHouseholdId = householdId
        syncRefCount = 1
        syncJob = scope.launch {
            firestoreDataSource.observeCategories(householdId).collect { categories ->
                if (categories.isEmpty()) return@collect // Don't overwrite local with empty Firestore data
                val pendingIds = categoryDao.getPendingSyncCategories().map { it.id }.toSet()
                val safeToInsert = categories
                    .filter { it.id !in pendingIds }
                    .distinctBy { it.name }
                    .map { it.toEntity(SyncStatus.SYNCED) }
                if (safeToInsert.isNotEmpty()) {
                    categoryDao.insertAll(safeToInsert)
                }
            }
        }
    }

    override fun stopRealtimeSync() {
        syncRefCount--
        if (syncRefCount <= 0) {
            syncJob?.cancel()
            syncJob = null
            currentSyncHouseholdId = null
            syncRefCount = 0
        }
    }
}
