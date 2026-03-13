package com.bose.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val isPreset: Boolean,
    val householdId: String,
    val syncStatus: Int = SyncStatus.SYNCED
)
