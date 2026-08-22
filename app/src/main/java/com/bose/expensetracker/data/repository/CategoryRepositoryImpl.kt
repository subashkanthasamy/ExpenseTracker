package com.bose.expensetracker.data.repository

import com.bose.expensetracker.data.local.dao.CategoryDao
import com.bose.expensetracker.data.local.entity.SyncStatus
import com.bose.expensetracker.data.mapper.toDomain
import com.bose.expensetracker.data.mapper.toEntity
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.model.CategoryPresets
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
    private val firestoreDataSource: FirestoreDataSource
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

    /**
     * Seeds the categories a household is missing, once per catalogue version.
     *
     * Previously all-or-nothing — `if (existing.any { it.isPreset }) return` — so a household
     * that had ever been seeded never saw a newly added category. That mattered: a third of one
     * real household's expenses had piled into "Misc" for want of a better bucket.
     *
     * Two things keep this from misbehaving:
     *
     * Matching is by **name, including custom categories**, so a category the owner created by
     * hand (this household had a hand-made "Medical") is recognised rather than duplicated
     * under a preset id.
     *
     * The **version stamp** makes it one-shot per bump. Gap-filling on name alone would
     * resurrect any preset the owner deleted on every single app open — seeding fighting the
     * user, which is worse than a missing category.
     *
     * Owner-only: the security rules reject category writes from members, so on a member's
     * device this is a no-op (silent on iOS, where those writes are wrapped in `try?`).
     */
    override suspend fun seedPresetCategories(householdId: String) {
        val existing = categoryDao.getAllCategoriesOnce(householdId)

        // Clean up duplicates: keep first occurrence per name, delete the rest. Still worth
        // doing — households seeded by both platforms before the id schemes were unified have
        // two documents per category.
        existing.groupBy { it.name }.forEach { (_, dupes) ->
            if (dupes.size > 1) {
                dupes.drop(1).forEach { dup ->
                    categoryDao.deleteById(dup.id)
                    try { firestoreDataSource.deleteCategory(householdId, dup.id) } catch (_: Exception) { }
                }
            }
        }

        val household = firestoreDataSource.getHousehold(householdId)
        if (household != null && household.presetVersion >= CategoryPresets.VERSION) return

        val haveNames = existing.map { it.name.trim().lowercase() }.toSet()
        val missing = CategoryPresets.all.filter { it.name.trim().lowercase() !in haveNames }

        missing.forEach { preset ->
            val category = Category(
                id = CategoryPresets.idFor(householdId, preset.name),
                name = preset.name,
                icon = preset.iconKey,
                color = preset.color,
                isPreset = true,
                householdId = householdId
            )
            categoryDao.insert(category.toEntity(SyncStatus.PENDING_CREATE))
            try {
                firestoreDataSource.addCategory(householdId, category)
                categoryDao.updateSyncStatus(category.id, SyncStatus.SYNCED)
            } catch (_: Exception) { }
        }

        // Stamped even when nothing was missing, so the check above short-circuits next launch.
        try {
            firestoreDataSource.setHouseholdPresetVersion(householdId, CategoryPresets.VERSION)
        } catch (_: Exception) {
            // Owner-only write. A member reaching here simply tries again next time.
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
        if (householdId == com.bose.expensetracker.data.preferences.SandboxConstants.SANDBOX_HOUSEHOLD_ID) return
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
