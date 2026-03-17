package com.bose.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bose.expensetracker.data.local.entity.RecurringExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {

    @Query("SELECT * FROM recurring_expenses WHERE householdId = :householdId ORDER BY createdAt DESC")
    fun getAll(householdId: String): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses WHERE isActive = 1")
    suspend fun getAllActive(): List<RecurringExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecurringExpenseEntity)

    @Update
    suspend fun update(entity: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE recurring_expenses SET lastGeneratedDate = :date WHERE id = :id")
    suspend fun updateLastGenerated(id: String, date: Long)

    @Query("UPDATE recurring_expenses SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)
}
