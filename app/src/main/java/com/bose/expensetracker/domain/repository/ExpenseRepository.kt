package com.bose.expensetracker.domain.repository

import com.bose.expensetracker.domain.model.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getExpenses(householdId: String): Flow<List<Expense>>
    fun getExpensesByUser(householdId: String, userId: String): Flow<List<Expense>>
    fun getExpensesByCategory(householdId: String, categoryId: String): Flow<List<Expense>>
    fun getExpensesByDateRange(householdId: String, startDate: Long, endDate: Long): Flow<List<Expense>>
    suspend fun getExpenseById(id: String): Expense?
    suspend fun addExpense(expense: Expense): Result<Unit>
    suspend fun updateExpense(expense: Expense): Result<Unit>
    suspend fun deleteExpense(id: String): Result<Unit>
    suspend fun syncPendingExpenses()
    fun startRealtimeSync(householdId: String)
    fun stopRealtimeSync()
}
