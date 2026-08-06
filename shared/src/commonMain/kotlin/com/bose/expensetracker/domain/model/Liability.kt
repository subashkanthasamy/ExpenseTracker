package com.bose.expensetracker.domain.model

data class Liability(
    val id: String,
    val householdId: String,
    val name: String,
    val amount: Double,
    val type: String,
    val date: Long,
    val addedBy: String
)
