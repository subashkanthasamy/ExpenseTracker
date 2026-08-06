package com.bose.expensetracker.domain.model

data class Budget(
    val id: String,
    val householdId: String,
    val categoryId: String,
    val categoryName: String,
    val monthlyLimit: Double,
    val spent: Double = 0.0
) {
    val percentage: Double
        get() = if (monthlyLimit > 0) (spent / monthlyLimit * 100) else 0.0

    val status: BudgetStatus
        get() = when {
            percentage >= 100 -> BudgetStatus.EXCEEDED
            percentage >= 80 -> BudgetStatus.WARNING
            else -> BudgetStatus.OK
        }
}

enum class BudgetStatus { OK, WARNING, EXCEEDED }
