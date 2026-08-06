package com.bose.expensetracker.ui.state

import com.bose.expensetracker.domain.model.Expense

data class CategoryBreakdown(
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val color: Long
)

data class HouseholdMember(
    val uid: String,
    val displayName: String
)

data class DashboardUiState(
    val monthTotal: Double = 0.0,
    val lastMonthTotal: Double = 0.0,
    val recentExpenses: List<Expense> = emptyList(),
    val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
    val personFilter: String? = null,
    val members: List<HouseholdMember> = emptyList(),
    val isLoading: Boolean = true,
    val noHousehold: Boolean = false
)
