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

/**
 * One household member's share of spending over a period.
 *
 * [share] is deliberately part of the model rather than left to each screen: presenting the
 * split as a proportion of the household total is what keeps it a contribution breakdown
 * instead of a leaderboard of raw amounts.
 */
data class PersonSpending(
    val userId: String,
    val name: String,
    val amount: Double,
    /** Fraction of the period's total, 0.0..1.0. */
    val share: Double
)

data class InsightsUiState(
    val insights: List<SpendingInsight> = emptyList(),
    val dailySpending: Map<String, Double> = emptyMap(),
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val personSplit: List<PersonSpending> = emptyList(),
    val selectedPeriod: SummaryPeriod = SummaryPeriod.MONTH,
    val periodSummary: PeriodSummary = PeriodSummary(),
    val isLoading: Boolean = true
)
