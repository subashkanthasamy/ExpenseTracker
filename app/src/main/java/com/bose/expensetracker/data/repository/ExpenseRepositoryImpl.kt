package com.bose.expensetracker.data.repository

import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.local.entity.SyncStatus
import com.bose.expensetracker.data.mapper.toDomain
import com.bose.expensetracker.data.mapper.toEntity
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val firestoreDataSource: FirestoreDataSource
) : ExpenseRepository {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var syncJob: Job? = null
    private var syncRefCount = 0
    private var currentSyncHouseholdId: String? = null

    override fun getExpenses(householdId: String): Flow<List<Expense>> =
        expenseDao.getAllExpenses(householdId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getExpensesByUser(householdId: String, userId: String): Flow<List<Expense>> =
        expenseDao.getExpensesByUser(householdId, userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getExpensesByCategory(householdId: String, categoryId: String): Flow<List<Expense>> =
        expenseDao.getExpensesByCategory(householdId, categoryId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getExpensesByDateRange(householdId: String, startDate: Long, endDate: Long): Flow<List<Expense>> =
        expenseDao.getExpensesByDateRange(householdId, startDate, endDate).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getExpenseById(id: String): Expense? =
        expenseDao.getExpenseById(id)?.toDomain()

    override suspend fun addExpense(expense: Expense): Result<Unit> = runCatching {
        expenseDao.insert(expense.toEntity(SyncStatus.PENDING_CREATE))
        try {
            firestoreDataSource.addExpense(expense.householdId, expense)
            expenseDao.updateSyncStatus(expense.id, SyncStatus.SYNCED)
        } catch (_: Exception) {
            // Will sync later
        }
    }

    override suspend fun updateExpense(expense: Expense): Result<Unit> = runCatching {
        expenseDao.update(expense.toEntity(SyncStatus.PENDING_UPDATE))
        try {
            firestoreDataSource.updateExpense(expense.householdId, expense)
            expenseDao.updateSyncStatus(expense.id, SyncStatus.SYNCED)
        } catch (_: Exception) {
            // Will sync later
        }
    }

    override suspend fun deleteExpense(id: String): Result<Unit> = runCatching {
        val expense = expenseDao.getExpenseById(id) ?: return@runCatching
        expenseDao.update(expense.copy(syncStatus = SyncStatus.PENDING_DELETE))
        try {
            firestoreDataSource.deleteExpense(expense.householdId, id)
            expenseDao.deleteById(id)
        } catch (_: Exception) {
            // Will sync later
        }
    }

    override suspend fun deleteAllExpenses(householdId: String): Result<Unit> = runCatching {
        try {
            firestoreDataSource.deleteAllExpenses(householdId)
        } catch (e: Exception) {
            android.util.Log.w("ExpenseRepoImpl", "Firestore deleteAll failed (continuing locally): ${e.message}")
        }
        expenseDao.deleteAllForHousehold(householdId)
    }

    override suspend fun syncPendingExpenses() {
        val pending = expenseDao.getPendingSyncExpenses()
        for (entity in pending) {
            try {
                when (entity.syncStatus) {
                    SyncStatus.PENDING_CREATE -> {
                        firestoreDataSource.addExpense(entity.householdId, entity.toDomain())
                        expenseDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
                    }
                    SyncStatus.PENDING_UPDATE -> {
                        firestoreDataSource.updateExpense(entity.householdId, entity.toDomain())
                        expenseDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
                    }
                    SyncStatus.PENDING_DELETE -> {
                        firestoreDataSource.deleteExpense(entity.householdId, entity.id)
                        expenseDao.deleteById(entity.id)
                    }
                }
            } catch (_: Exception) {
                // Will retry next sync
            }
        }
    }

    override fun startRealtimeSync(householdId: String) {
        if (householdId == com.bose.expensetracker.data.preferences.SandboxConstants.SANDBOX_HOUSEHOLD_ID) return
        // If already syncing the same household, just increment ref count
        if (syncJob?.isActive == true && currentSyncHouseholdId == householdId) {
            syncRefCount++
            return
        }
        syncJob?.cancel()
        currentSyncHouseholdId = householdId
        syncRefCount = 1
        syncJob = scope.launch {
            firestoreDataSource.observeExpenses(householdId).collect { expenses ->
                val pendingIds = expenseDao.getPendingSyncExpenses().map { it.id }.toSet()
                val safeToInsert = expenses
                    .filter { it.id !in pendingIds }
                    .map { it.toEntity(SyncStatus.SYNCED) }
                if (safeToInsert.isNotEmpty()) {
                    expenseDao.insertAll(safeToInsert)
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
