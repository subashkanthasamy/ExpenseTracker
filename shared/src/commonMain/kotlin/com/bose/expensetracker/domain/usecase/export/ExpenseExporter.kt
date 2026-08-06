package com.bose.expensetracker.domain.usecase.export

import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.firstOrNull

interface ExpenseExporter {
    suspend fun exportCsv(
        householdId: String,
        startDate: Long? = null,
        endDate: Long? = null,
        userId: String? = null
    ): ByteArray

    suspend fun exportPdf(
        householdId: String,
        startDate: Long? = null,
        endDate: Long? = null,
        userId: String? = null
    ): ByteArray
}

suspend fun getFilteredExpenses(
    expenseRepository: ExpenseRepository,
    householdId: String,
    startDate: Long?,
    endDate: Long?,
    userId: String?
): List<Expense> {
    val flow = if (startDate != null && endDate != null) {
        expenseRepository.getExpensesByDateRange(householdId, startDate, endDate)
    } else if (userId != null) {
        expenseRepository.getExpensesByUser(householdId, userId)
    } else {
        expenseRepository.getExpenses(householdId)
    }
    return flow.firstOrNull() ?: emptyList()
}
