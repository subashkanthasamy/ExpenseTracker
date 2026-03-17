package com.bose.expensetracker.domain.model

import java.util.Calendar

data class SavingsGoal(
    val id: String,
    val householdId: String,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val icon: String = "\uD83C\uDFAF",
    val targetDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val progress: Double
        get() = if (targetAmount > 0) (currentAmount / targetAmount).coerceIn(0.0, 1.0) else 0.0

    val remaining: Double
        get() = (targetAmount - currentAmount).coerceAtLeast(0.0)

    val monthlyNeeded: Double?
        get() {
            val td = targetDate ?: return null
            val now = System.currentTimeMillis()
            if (td <= now || remaining <= 0) return null
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            val nowMonth = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
            cal.timeInMillis = td
            val targetMonth = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
            val months = (targetMonth - nowMonth).coerceAtLeast(1)
            return remaining / months
        }
}
