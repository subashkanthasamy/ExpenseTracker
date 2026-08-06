package com.bose.expensetracker.domain.model

import kotlinx.datetime.Clock

data class RecurringExpense(
    val id: String,
    val householdId: String,
    val amount: Double,
    val categoryId: String,
    val categoryName: String,
    val notes: String,
    val addedBy: String,
    val addedByName: String,
    val frequency: RecurringFrequency,
    val dayOfWeek: Int? = null,
    val dayOfMonth: Int? = null,
    val monthOfYear: Int? = null,
    val startDate: Long,
    val endDate: Long? = null,
    val lastGeneratedDate: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)

enum class RecurringFrequency(val value: Int, val label: String) {
    DAILY(0, "Daily"),
    WEEKLY(1, "Weekly"),
    MONTHLY(2, "Monthly"),
    YEARLY(3, "Yearly");

    companion object {
        fun fromValue(value: Int) = entries.first { it.value == value }
    }
}
