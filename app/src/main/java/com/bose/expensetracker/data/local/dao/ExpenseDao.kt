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
}
