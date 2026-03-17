package com.bose.expensetracker.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bose.expensetracker.data.local.dao.PendingSmsDao
import com.bose.expensetracker.data.local.dao.ProcessedSmsDao
import com.bose.expensetracker.data.local.entity.PendingSmsEntity
import com.bose.expensetracker.data.local.entity.ProcessedSmsEntity
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.util.NotificationHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class SmsActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsActionEntryPoint {
        fun pendingSmsDao(): PendingSmsDao
        fun processedSmsDao(): ProcessedSmsDao
        fun expenseRepository(): ExpenseRepository
        fun notificationHelper(): NotificationHelper
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingSmsId = intent.getStringExtra(EXTRA_PENDING_SMS_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        Log.d(TAG, "Action received: ${intent.action}, pendingId=$pendingSmsId")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsActionEntryPoint::class.java
        )

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pendingSmsDao = entryPoint.pendingSmsDao()
                val pending = pendingSmsDao.getById(pendingSmsId)
                if (pending == null) {
                    Log.w(TAG, "Pending SMS not found: $pendingSmsId")
                    return@launch
                }

                when (intent.action) {
                    ACTION_CONFIRM_SMS -> {
                        Log.d(TAG, "Confirming SMS: $pendingSmsId")
                        val now = System.currentTimeMillis()
                        val expense = Expense(
                            id = UUID.randomUUID().toString(),
                            householdId = pending.householdId,
                            amount = pending.amount,
                            categoryId = pending.categoryId,
                            categoryName = pending.categoryName,
                            date = pending.receivedTimestamp,
                            notes = buildNotes(pending),
                            addedBy = pending.userId,
                            addedByName = pending.userName,
                            createdAt = now,
                            updatedAt = now
                        )
                        val result = entryPoint.expenseRepository().addExpense(expense)
                        if (result.isSuccess) {
                            entryPoint.processedSmsDao().insert(
                                ProcessedSmsEntity(pending.smsHash, expense.id, now)
                            )
                            pendingSmsDao.updateStatus(pendingSmsId, PendingSmsEntity.STATUS_CONFIRMED)
                            Log.d(TAG, "Expense created: ${expense.id}")
                        } else {
                            Log.e(TAG, "Failed to create expense: ${result.exceptionOrNull()?.message}")
                        }
                    }
                    ACTION_DISMISS_SMS -> {
                        Log.d(TAG, "Dismissing SMS: $pendingSmsId")
                        pendingSmsDao.updateStatus(pendingSmsId, PendingSmsEntity.STATUS_DISMISSED)
                    }
                }

                if (notificationId != -1) {
                    entryPoint.notificationHelper().cancelNotification(notificationId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling SMS action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildNotes(pending: PendingSmsEntity): String {
        return buildString {
            append("SMS Import")
            pending.merchant?.let { append(": $it") }
            pending.cardOrAccount?.let { append(" (XX$it)") }
            append(" [${pending.sender}]")
        }
    }

    companion object {
        const val ACTION_CONFIRM_SMS = "com.bose.expensetracker.ACTION_CONFIRM_SMS"
        const val ACTION_DISMISS_SMS = "com.bose.expensetracker.ACTION_DISMISS_SMS"
        const val EXTRA_PENDING_SMS_ID = "extra_pending_sms_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val TAG = "SmsActionReceiver"
    }
}
