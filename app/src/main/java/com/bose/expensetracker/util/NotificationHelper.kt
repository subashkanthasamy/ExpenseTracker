package com.bose.expensetracker.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bose.expensetracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID_SMS_IMPORT = "sms_import_channel"
        private const val NOTIFICATION_ID_BASE = 1001
        private const val TAG = "NotificationHelper"
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SMS_IMPORT,
                "SMS Auto-Import",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for expenses auto-imported from SMS"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showSmsImportNotification(merchant: String, amount: Double, categoryName: String) {
        // Check notification permission for API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted, skipping notification")
                return
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SMS_IMPORT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Expense added from SMS")
            .setContentText("₹${"%.2f".format(amount)} — $merchant ($categoryName)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        val notificationId = NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 10000).toInt()
        manager.notify(notificationId, notification)
        Log.d(TAG, "Notification shown: ₹$amount — $merchant ($categoryName)")
    }
}
