package com.bose.expensetracker.data.local

import com.bose.expensetracker.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface LocalBudgetDataSource {
    fun getBudgets(householdId: String): Flow<List<Budget>>
    suspend fun insert(budget: Budget)
    suspend fun delete(id: String)
}
