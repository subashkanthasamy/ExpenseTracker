package com.bose.expensetracker.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bose.expensetracker.R
import com.bose.expensetracker.data.receiver.SmsActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID_SMS_IMPORT = "sms_import_channel"
        const val CHANNEL_ID_REMINDER = "reminder_channel"
        const val CHANNEL_ID_BUDGET = "budget_alert_channel"
        private const val NOTIFICATION_ID_BASE = 1001
        private const val TAG = "NotificationHelper"
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID_SMS_IMPORT,
                "SMS Auto-Import",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "SMS expense confirmations"
            })

            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID_REMINDER,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily expense reminders and bill due alerts"
            })

            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID_BUDGET,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when spending exceeds budget limits"
            })
        }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                return false
            }
        }
        return true
    }

    fun showPendingSmsNotification(
        pendingSmsId: String,
        merchant: String,
        amount: Double,
        categoryName: String,
        notificationId: Int
    ) {
        if (!canNotify()) return

        // Content intent — tapping the notification body opens SMS Report screen
        val contentIntent = Intent(context, com.bose.expensetracker.MainActivity::class.java).apply {
            putExtra("nav_destination", "sms_report")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val baseRequestCode = pendingSmsId.hashCode() and 0x7FFFFFFF
        val contentPending = PendingIntent.getActivity(
            context,
            baseRequestCode,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confirmIntent = Intent(context, SmsActionReceiver::class.java).apply {
            action = SmsActionReceiver.ACTION_CONFIRM_SMS
            putExtra(SmsActionReceiver.EXTRA_PENDING_SMS_ID, pendingSmsId)
            putExtra(SmsActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val confirmPending = PendingIntent.getBroadcast(
            context,
            baseRequestCode + 1,
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, SmsActionReceiver::class.java).apply {
            action = SmsActionReceiver.ACTION_DISMISS_SMS
            putExtra(SmsActionReceiver.EXTRA_PENDING_SMS_ID, pendingSmsId)
            putExtra(SmsActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            baseRequestCode + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SMS_IMPORT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("₹${"%.2f".format(amount)} — $merchant")
            .setContentText("$categoryName • SMS Import")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher_foreground, "Confirm", confirmPending)
            .addAction(R.drawable.ic_launcher_foreground, "Dismiss", dismissPending)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
        Log.d(TAG, "Pending SMS notification shown: ₹$amount — $merchant ($categoryName)")
    }

    fun cancelNotification(notificationId: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(notificationId)
    }

    fun showSmsImportNotification(merchant: String, amount: Double, categoryName: String) {
        if (!canNotify()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SMS_IMPORT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Expense added from SMS")
            .setContentText("₹${"%.2f".format(amount)} — $merchant ($categoryName)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        val nId = NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 10000).toInt()
        manager.notify(nId, notification)
    }

    fun showDailyReminderNotification() {
        if (!canNotify()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Don't forget to log expenses!")
            .setContentText("Tap to add today's expenses")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(2001, notification)
    }

    fun showBudgetAlertNotification(spent: Double, limit: Double) {
        if (!canNotify()) return

        val percentage = ((spent / limit) * 100).toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BUDGET)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Budget Alert! $percentage% spent")
            .setContentText("You've spent ₹${"%.0f".format(spent)} of your ₹${"%.0f".format(limit)} budget")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(2002, notification)
    }

    fun showBillDueNotification(title: String, amount: Double) {
        if (!canNotify()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Bill Due: $title")
            .setContentText("₹${"%.2f".format(amount)} is due soon")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        val nId = 3000 + (title.hashCode().absoluteValue % 1000)
        manager.notify(nId, notification)
    }

    fun generateNotificationId(pendingSmsId: String): Int {
        return 4000 + (pendingSmsId.hashCode().absoluteValue % 10000)
    }
}
