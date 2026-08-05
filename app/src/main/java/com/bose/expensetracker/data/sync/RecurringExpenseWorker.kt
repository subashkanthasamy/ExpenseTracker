package com.bose.expensetracker.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.bose.expensetracker.domain.usecase.recurring.RecurringScheduleCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID

/**
 * Materialises recurring rules into expenses.
 *
 * Rules now live in Firestore rather than Room, so they sync across devices and household
 * members and survive a reinstall.
 *
 * Which days are due is decided by [RecurringScheduleCalculator] in the shared module, so
 * this worker and the iOS launch-time pass behave identically — important now that both read
 * and write the same `lastGeneratedDate`. It also means missed days are caught up rather
 * than dropped, which is what the old Room-backed worker did whenever Doze deferred it past
 * the due day.
 */
@HiltWorker
class RecurringExpenseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val firestoreDataSource: FirestoreDataSource,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val expenseRepository: ExpenseRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val uid = authRepository.getCurrentUserId() ?: return Result.success()
            val householdId = householdRepository.getUserHouseholdId(uid) ?: return Result.success()

            val now = System.currentTimeMillis()
            var created = 0

            for (rule in firestoreDataSource.getActiveRecurringExpenses(householdId)) {
                val dueDays = RecurringScheduleCalculator.dueDays(rule, todayMillis = now)
                for (day in dueDays) {
                    expenseRepository.addExpense(
                        Expense(
                            id = UUID.randomUUID().toString(),
                            householdId = rule.householdId,
                            amount = rule.amount,
                            categoryId = rule.categoryId,
                            categoryName = rule.categoryName,
                            date = day,
                            notes = if (rule.notes.isBlank()) "Recurring" else "Recurring: ${rule.notes}",
                            addedBy = rule.addedBy,
                            addedByName = rule.addedByName,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    created++
                }
                // Advance the watermark even when nothing was due, so the next run doesn't
                // re-walk the same window.
                firestoreDataSource.updateRecurringLastGenerated(
                    householdId = householdId,
                    id = rule.id,
                    timestamp = dueDays.lastOrNull() ?: now
                )
            }

            if (created > 0) Log.d("RecurringWorker", "Created $created recurring expense(s)")
            Result.success()
        } catch (e: Exception) {
            Log.e("RecurringWorker", "Failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
