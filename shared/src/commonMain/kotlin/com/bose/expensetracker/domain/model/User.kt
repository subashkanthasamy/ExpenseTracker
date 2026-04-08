package com.bose.expensetracker.domain.model

data class User(
    val uid: String,
    val email: String,
    val displayName: String,
    val householdIds: List<String> = emptyList(),
    val activeHouseholdId: String? = null
) {
    // Backward compat: mirrors old single householdId behavior
    val householdId: String? get() = activeHouseholdId
}
