package com.bose.expensetracker.domain.usecase.smsimport

import android.util.Log
import com.bose.expensetracker.data.local.dao.PendingSmsDao
import com.bose.expensetracker.data.local.dao.ProcessedSmsDao
import com.bose.expensetracker.data.local.entity.PendingSmsEntity
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.bose.expensetracker.util.NotificationHelper
import kotlinx.coroutines.flow.firstOrNull
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessSmsUseCase @Inject constructor(
    private val parser: SmsTransactionParser,
    private val categoryMatcher: SmsCategoryMatcher,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val processedSmsDao: ProcessedSmsDao,
    private val pendingSmsDao: PendingSmsDao,
    private val notificationHelper: NotificationHelper
) {
    suspend fun process(sender: String, body: String, receivedTimestamp: Long): Boolean {
        val uid = authRepository.getCurrentUserId()
        if (uid == null) {
            Log.d(TAG, "No user logged in")
            return false
        }

        val householdId = householdRepository.getUserHouseholdId(uid)
        if (householdId == null) {
            Log.d(TAG, "No active household for user $uid")
            return false
        }

        val hash = computeHash(sender, body, receivedTimestamp)
        if (processedSmsDao.exists(hash) || pendingSmsDao.exists(hash)) {
            Log.d(TAG, "SMS already processed or pending (duplicate hash)")
            return false
        }

        val transaction = parser.parse(sender, body, receivedTimestamp)
        if (transaction == null) {
            Log.d(TAG, "SMS not recognized as financial transaction")
            return false
        }
        Log.d(TAG, "Parsed: amount=${transaction.amount}, merchant=${transaction.merchant}, type=${transaction.transactionType}")

        val categoryName = categoryMatcher.matchCategory(transaction.merchant, body)
        val categories = categoryRepository.getCategories(householdId).firstOrNull() ?: emptyList()
        val category = categories.find { it.name.equals(categoryName, ignoreCase = true) }
        Log.d(TAG, "Category: $categoryName (found=${category != null})")

        val userName = authRepository.getCurrentUserDisplayName() ?: "User"
        val pendingId = UUID.randomUUID().toString()
        val notificationId = notificationHelper.generateNotificationId(pendingId)

        val pendingEntity = PendingSmsEntity(
            id = pendingId,
            smsHash = hash,
            sender = sender,
            body = body,
            amount = transaction.amount,
            merchant = transaction.merchant,
            categoryId = category?.id ?: "",
            categoryName = category?.name ?: categoryName,
            cardOrAccount = transaction.cardOrAccount,
            householdId = householdId,
            userId = uid,
            userName = userName,
            receivedTimestamp = receivedTimestamp,
            notificationId = notificationId
        )

        pendingSmsDao.insert(pendingEntity)
        Log.d(TAG, "Pending SMS created: $pendingId")

        notificationHelper.showPendingSmsNotification(
            pendingSmsId = pendingId,
            merchant = transaction.merchant ?: sender,
            amount = transaction.amount,
            categoryName = category?.name ?: categoryName,
            notificationId = notificationId
        )

        return true
    }

    private fun computeHash(sender: String, body: String, timestamp: Long): String {
        val input = "$sender|$body|$timestamp"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ProcessSmsUseCase"
    }
}
