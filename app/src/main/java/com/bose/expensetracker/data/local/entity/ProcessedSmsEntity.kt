package com.bose.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_sms")
data class ProcessedSmsEntity(
    @PrimaryKey val smsHash: String,
    val expenseId: String,
    val processedAt: Long
)
