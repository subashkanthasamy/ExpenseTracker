package com.bose.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bose.expensetracker.data.local.entity.ExpenseEntity
import com.bose.expensetracker.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE householdId = :householdId AND syncStatus != ${SyncStatus.PENDING_DELETE} ORDER BY date DESC")
    fun getAllExpenses(householdId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE householdId = :householdId AND addedBy = :userId AND syncStatus != ${SyncStatus.PENDING_DELETE} ORDER BY date DESC")
    fun getExpensesByUser(householdId: String, userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE householdId = :householdId AND categoryId = :categoryId AND syncStatus != ${SyncStatus.PENDING_DELETE} ORDER BY date DESC")
    fun getExpensesByCategory(householdId: String, categoryId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE householdId = :householdId AND date BETWEEN :startDate AND :endDate AND syncStatus != ${SyncStatus.PENDING_DELETE} ORDER BY date DESC")
    fun getExpensesByDateRange(householdId: String, startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>

    /** One-shot count, used by the sandbox seeder to avoid re-seeding demo data. */
    @Query("SELECT COUNT(*) FROM expenses WHERE householdId = :householdId")
    suspend fun countExpenses(householdId: String): Int

    @Query("SELECT * FROM expenses WHERE syncStatus != ${SyncStatus.SYNCED}")
    suspend fun getPendingSyncExpenses(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("UPDATE expenses SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: Int)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM expenses WHERE householdId = :householdId")
    suspend fun deleteAllForHousehold(householdId: String)

    /**
     * Drops cached rows the server no longer serves us.
     *
     * The realtime sync used to only insert, so a row that became invisible — someone marked
     * their expense personal, or our role was reduced — stayed in the cache and kept rendering.
     * The UI reads Room, so that was a stale read of data we are no longer allowed to see.
     *
     * Restricted to SYNCED rows: anything PENDING_* has local work that has not reached the
     * server yet and must survive.
     */
    @Query(
        "DELETE FROM expenses WHERE householdId = :householdId " +
            "AND syncStatus = ${SyncStatus.SYNCED} AND id NOT IN (:servedIds)"
    )
    suspend fun deleteSyncedNotIn(householdId: String, servedIds: List<String>)

    /**
     * Per-category spend for a period, **shared rows only**.
     *
     * A budget is a shared commitment, so personal spending must not consume it — and since a
     * member cannot see peers' personal rows, counting them would make the same budget read
     * differently depending on who is looking.
     *
     * `scope = ''` is included deliberately: that is what rows written before scopes existed
     * carry, and `ExpenseScope.fromWire` maps a blank to SHARED. Filtering on `= 'shared'` alone
     * would silently drop every legacy expense from budget totals.
     */
    @Query(
        "SELECT categoryId, SUM(amount) as total FROM expenses " +
            "WHERE householdId = :householdId AND date BETWEEN :startDate AND :endDate " +
            "AND syncStatus != ${SyncStatus.PENDING_DELETE} " +
            "AND (scope = 'shared' OR scope = '') GROUP BY categoryId"
    )
    suspend fun getCategorySpending(householdId: String, startDate: Long, endDate: Long): List<CategorySpending>
}

data class CategorySpending(
    val categoryId: String,
    val total: Double
)
