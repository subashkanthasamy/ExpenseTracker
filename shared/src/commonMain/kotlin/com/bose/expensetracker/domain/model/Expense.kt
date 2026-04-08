package com.bose.expensetracker.domain.model

data class Expense(
    val id: String,
    val householdId: String,
    val amount: Double,
    val categoryId: String,
    val categoryName: String,
    val date: Long,
    val notes: String,
    val addedBy: String,
    val addedByName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isSynced: Boolean = false
)
