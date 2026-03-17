package com.bose.expensetracker.domain.usecase.smsimport

import android.util.Log
import com.bose.expensetracker.data.local.dao.ProcessedSmsDao
import com.bose.expensetracker.data.local.entity.ProcessedSmsEntity
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
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
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val processedSmsDao: ProcessedSmsDao,
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
        if (processedSmsDao.exists(hash)) {
            Log.d(TAG, "SMS already processed (duplicate hash)")
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
        val now = System.currentTimeMillis()
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            householdId = householdId,
            amount = transaction.amount,
            categoryId = category?.id ?: "",
            categoryName = category?.name ?: categoryName,
            date = receivedTimestamp,
            notes = buildNotes(transaction, sender),
            addedBy = uid,
            addedByName = userName,
            createdAt = now,
            updatedAt = now
        )

        val result = expenseRepository.addExpense(expense)
        if (result.isSuccess) {
            processedSmsDao.insert(ProcessedSmsEntity(hash, expense.id, now))
            notificationHelper.showSmsImportNotification(
                transaction.merchant ?: sender,
                transaction.amount,
                category?.name ?: categoryName
            )
            Log.d(TAG, "Expense created: ${expense.id}, amount=${expense.amount}")
            return true
        } else {
            Log.e(TAG, "Failed to add expense: ${result.exceptionOrNull()?.message}")
        }
        return false
    }

    private fun computeHash(sender: String, body: String, timestamp: Long): String {
        val input = "$sender|$body|$timestamp"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun buildNotes(transaction: ParsedTransaction, sender: String): String {
        return buildString {
            append("SMS Import")
            transaction.merchant?.let { append(": $it") }
            transaction.cardOrAccount?.let { append(" (XX$it)") }
            append(" [$sender]")
        }
    }

    companion object {
        private const val TAG = "ProcessSmsUseCase"
    }
}
