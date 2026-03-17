package com.bose.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sms")
data class PendingSmsEntity(
    @PrimaryKey val id: String,
    val smsHash: String,
    val sender: String,
    val body: String,
    val amount: Double,
    val merchant: String?,
    val categoryId: String,
    val categoryName: String,
    val cardOrAccount: String?,
    val householdId: String,
    val userId: String,
    val userName: String,
    val receivedTimestamp: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = STATUS_PENDING,
    val notificationId: Int = 0
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_CONFIRMED = "CONFIRMED"
        const val STATUS_DISMISSED = "DISMISSED"
    }
}
