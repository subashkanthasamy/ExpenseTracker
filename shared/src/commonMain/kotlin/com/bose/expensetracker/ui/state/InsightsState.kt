package com.bose.expensetracker.ui.state

import com.bose.expensetracker.domain.model.SpendingInsight

enum class SummaryPeriod(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year")
}

data class PeriodSummary(
    val totalSpent: Double = 0.0,
    val previousPeriodSpent: Double = 0.0,
    val percentChange: Double = 0.0,
    val topCategory: String = "",
    val topCategoryAmount: Double = 0.0,
    val averageDailySpend: Double = 0.0,
    val daysInPeriod: Int = 1
)

data class InsightsUiState(
    val insights: List<SpendingInsight> = emptyList(),
    val dailySpending: Map<String, Double> = emptyMap(),
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val selectedPeriod: SummaryPeriod = SummaryPeriod.MONTH,
    val periodSummary: PeriodSummary = PeriodSummary(),
    val isLoading: Boolean = true
)
