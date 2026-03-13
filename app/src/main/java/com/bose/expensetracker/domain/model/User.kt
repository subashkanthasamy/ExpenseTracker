package com.bose.expensetracker.domain.model

data class User(
    val uid: String,
    val email: String,
    val displayName: String,
    val householdId: String?
)
