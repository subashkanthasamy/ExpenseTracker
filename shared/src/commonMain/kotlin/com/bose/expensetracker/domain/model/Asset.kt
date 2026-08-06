package com.bose.expensetracker.domain.model

data class Asset(
    val id: String,
    val householdId: String,
    val name: String,
    val value: Double,
    val type: String,
    val date: Long,
    val addedBy: String
)
