package com.bose.expensetracker.domain.model

data class SpendingInsight(
    val title: String,
    val description: String,
    val type: InsightType,
    val relatedCategory: String?,
    val percentageChange: Double?
)

enum class InsightType {
    TREND_UP,
    TREND_DOWN,
    SUGGESTION,
    ANOMALY
}
