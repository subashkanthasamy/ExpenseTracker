package com.bose.expensetracker.data.repository

import com.bose.expensetracker.data.local.dao.CategoryDao
import com.bose.expensetracker.data.local.entity.SyncStatus
import com.bose.expensetracker.data.mapper.toDomain
import com.bose.expensetracker.data.mapper.toEntity
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val firestoreDataSource: FirestoreDataSource
) : CategoryRepository {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var syncJob: Job? = null
    private var syncRefCount = 0
    private var currentSyncHouseholdId: String? = null

    override fun getCategories(householdId: String): Flow<List<Category>> =
        categoryDao.getAllCategories(householdId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getCategoryById(id: String): Category? =
        categoryDao.getCategoryById(id)?.toDomain()

    override suspend fun addCategory(category: Category): Result<Unit> = runCatching {
        categoryDao.insert(category.toEntity(SyncStatus.PENDING_CREATE))
        try {
            firestoreDataSource.addCategory(category.householdId, category)
            categoryDao.updateSyncStatus(category.id, SyncStatus.SYNCED)
        } catch (_: Exception) { }
    }

    override suspend fun updateCategory(category: Category): Result<Unit> = runCatching {
        categoryDao.update(category.toEntity(SyncStatus.PENDING_UPDATE))
        try {
            firestoreDataSource.updateCategory(category.householdId, category)
            categoryDao.updateSyncStatus(category.id, SyncStatus.SYNCED)
        } catch (_: Exception) { }
    }

    override suspend fun deleteCategory(id: String): Result<Unit> = runCatching {
        val category = categoryDao.getCategoryById(id) ?: return@runCatching
        categoryDao.update(category.copy(syncStatus = SyncStatus.PENDING_DELETE))
        try {
            firestoreDataSource.deleteCategory(category.householdId, id)
            categoryDao.deleteById(id)
        } catch (_: Exception) { }
    }

    override suspend fun seedPresetCategories(householdId: String) {
        // Guard: don't re-seed if presets already exist in Room
        val existing = categoryDao.getAllCategoriesOnce(householdId)
        if (existing.any { it.isPreset }) return

        val presets = listOf(
            Category(UUID.randomUUID().toString(), "Food", "restaurant", 0xFF4CAF50, true, householdId),
            Category(UUID.randomUUID().toString(), "Groceries", "shopping_cart", 0xFF8BC34A, true, householdId),
            Category(UUID.randomUUID().toString(), "Transport", "directions_car", 0xFF2196F3, true, householdId),
            Category(UUID.randomUUID().toString(), "Rent/Home Loan", "home", 0xFFFF9800, true, householdId),
            Category(UUID.randomUUID().toString(), "Bills", "receipt_long", 0xFFF44336, true, householdId),
            Category(UUID.randomUUID().toString(), "Family", "family_restroom", 0xFFE91E63, true, householdId),
            Category(UUID.randomUUID().toString(), "Entertainment", "movie", 0xFF9C27B0, true, householdId),
            Category(UUID.randomUUID().toString(), "Misc", "more_horiz", 0xFF607D8B, true, householdId)
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
