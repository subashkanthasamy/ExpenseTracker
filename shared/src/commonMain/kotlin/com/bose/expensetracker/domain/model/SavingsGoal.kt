package com.bose.expensetracker.domain.model

import kotlinx.datetime.Clock

data class SavingsGoal(
    val id: String,
    val householdId: String,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val icon: String = "🏯",
    val targetDate: Long? = null,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    val progress: Double
        get() = if (targetAmount > 0) (currentAmount / targetAmount).coerceIn(0.0, 1.0) else 0.0

    val remaining: Double
        get() = (targetAmount - currentAmount).coerceAtLeast(0.0)

    val monthlyNeeded: Double?
        get() {
            val td = targetDate ?: return null
            val now = Clock.System.now().toEpochMilliseconds()
            if (td <= now || remaining <= 0) return null
            val daysRemaining = ((td - now) / 86_400_000L).coerceAtLeast(1)
            val monthsRemaining = (daysRemaining / 30.44).coerceAtLeast(1.0)
            return remaining / monthsRemaining
        }
}
