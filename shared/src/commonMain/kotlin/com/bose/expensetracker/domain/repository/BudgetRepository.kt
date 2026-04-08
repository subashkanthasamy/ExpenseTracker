package com.bose.expensetracker.domain.repository

import com.bose.expensetracker.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getBudgetsWithSpending(householdId: String): Flow<List<Budget>>
    suspend fun addBudget(budget: Budget): Result<Unit>
    suspend fun deleteBudget(id: String): Result<Unit>
}
