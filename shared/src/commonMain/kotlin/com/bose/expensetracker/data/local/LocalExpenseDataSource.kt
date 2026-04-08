package com.bose.expensetracker.data.local

import com.bose.expensetracker.domain.model.Expense
import kotlinx.coroutines.flow.Flow

interface LocalExpenseDataSource {
    fun getExpenses(householdId: String): Flow<List<Expense>>
    fun getExpensesByUser(householdId: String, userId: String): Flow<List<Expense>>
    fun getExpensesByCategory(householdId: String, categoryId: String): Flow<List<Expense>>
    fun getExpensesByDateRange(householdId: String, startDate: Long, endDate: Long): Flow<List<Expense>>
    suspend fun getExpenseById(id: String): Expense?
    suspend fun insert(expense: Expense)
    suspend fun update(expense: Expense)
    suspend fun delete(id: String)
    suspend fun deleteAll(householdId: String)
    suspend fun getPendingSync(): List<Expense>
    suspend fun markSynced(id: String)
    fun getCategorySpending(householdId: String, startDate: Long, endDate: Long): Flow<Map<String, Double>>
}
