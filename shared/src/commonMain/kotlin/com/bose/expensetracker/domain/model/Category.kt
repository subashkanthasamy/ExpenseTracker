package com.bose.expensetracker.domain.model

data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val isPreset: Boolean,
    val householdId: String
)
