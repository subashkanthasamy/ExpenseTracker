package com.bose.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val value: Double,
    val type: String,
    val date: Long,
    val addedBy: String,
    val syncStatus: Int = SyncStatus.SYNCED
)
