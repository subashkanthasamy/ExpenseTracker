package com.bose.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_expenses")
data class RecurringExpenseEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val amount: Double,
    val categoryId: String,
    val categoryName: String,
    val notes: String,
    val addedBy: String,
    val addedByName: String,
    val frequency: Int,
    val dayOfWeek: Int? = null,
    val dayOfMonth: Int? = null,
    val monthOfYear: Int? = null,
    val startDate: Long,
    val endDate: Long? = null,
    val lastGeneratedDate: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val FREQ_DAILY = 0
        const val FREQ_WEEKLY = 1
        const val FREQ_MONTHLY = 2
        const val FREQ_YEARLY = 3
    }
}
