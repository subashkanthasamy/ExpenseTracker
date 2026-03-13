package com.bose.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
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
    val syncStatus: Int = SyncStatus.SYNCED
)

object SyncStatus {
    const val SYNCED = 0
    const val PENDING_CREATE = 1
    const val PENDING_UPDATE = 2
    const val PENDING_DELETE = 3
}
