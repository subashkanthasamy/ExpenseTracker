package com.bose.expensetracker.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.bose.expensetracker.data.preferences.SmsImportPreferences
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.usecase.smsimport.ProcessSmsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsBroadcastReceiverEntryPoint {
        fun processSmsUseCase(): ProcessSmsUseCase
        fun smsImportPreferences(): SmsImportPreferences
        fun authRepository(): AuthRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "Ignoring non-SMS action")
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsBroadcastReceiverEntryPoint::class.java
        )

        val authRepo = entryPoint.authRepository()
        val uid = authRepo.getCurrentUserId()
        if (uid == null) {
            Log.w(TAG, "User not logged in, ignoring SMS")
            return
        }
        Log.d(TAG, "User logged in: $uid")

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.w(TAG, "No messages in intent")
            return
        }
        Log.d(TAG, "Received ${messages.size} SMS message(s)")

        // Group multi-part SMS by sender and concatenate bodies
        val groupedMessages = messages.groupBy { it.originatingAddress ?: "" }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = entryPoint.smsImportPreferences()
                val isEnabled = prefs.isSmsImportEnabled(uid).firstOrNull() ?: false
                Log.d(TAG, "SMS import enabled: $isEnabled")
                if (!isEnabled) return@launch

                val useCase = entryPoint.processSmsUseCase()

                for ((sender, parts) in groupedMessages) {
                    if (sender.isBlank()) continue
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    val timestamp = parts.first().timestampMillis
                    Log.d(TAG, "Processing SMS from '$sender': ${body.take(100)}...")

                    try {
                        val result = useCase.process(sender, body, timestamp)
                        Log.d(TAG, "Process result for '$sender': $result")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing SMS from '$sender'", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in SMS processing", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }
}
