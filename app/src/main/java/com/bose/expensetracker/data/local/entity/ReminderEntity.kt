package com.bose.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: Int, // 0=DAILY, 1=BUDGET, 2=BILL
    val title: String,
    val amount: Double = 0.0,
    val hour: Int = 20, // default 8 PM
    val minute: Int = 0,
    val dueDay: Int = 0, // day of month for bills
    val repeatInterval: Int = 0, // 0=DAILY, 1=WEEKLY, 2=MONTHLY
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_DAILY = 0
        const val TYPE_BUDGET = 1
        const val TYPE_BILL = 2

        const val REPEAT_DAILY = 0
        const val REPEAT_WEEKLY = 1
        const val REPEAT_MONTHLY = 2
    }
}
