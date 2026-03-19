package com.bose.expensetracker.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bose.expensetracker.data.local.dao.RecurringExpenseDao
import com.bose.expensetracker.data.local.entity.RecurringExpenseEntity
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.ExpenseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.UUID

@HiltWorker
class RecurringExpenseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val recurringExpenseDao: RecurringExpenseDao,
    private val expenseRepository: ExpenseRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val activeRecurrences = recurringExpenseDao.getAllActive()
            val today = Calendar.getInstance()
            val todayStart = today.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            for (recurring in activeRecurrences) {
                if (recurring.endDate != null && todayStart > recurring.endDate) continue
                if (todayStart < recurring.startDate) continue

                val lastGen = recurring.lastGeneratedDate ?: (recurring.startDate - 86400000L)
                if (lastGen >= todayStart) continue

                if (isDue(recurring, today)) {
                    val now = System.currentTimeMillis()
                    val expense = Expense(
                        id = UUID.randomUUID().toString(),
                        householdId = recurring.householdId,
                        amount = recurring.amount,
                        categoryId = recurring.categoryId,
                        categoryName = recurring.categoryName,
                        date = now,
                        notes = "Recurring: ${recurring.notes}",
                        addedBy = recurring.addedBy,
                        addedByName = recurring.addedByName,
                        createdAt = now,
                        updatedAt = now
                    )
                    expenseRepository.addExpense(expense)
                    recurringExpenseDao.updateLastGenerated(recurring.id, todayStart)
                    Log.d("RecurringWorker", "Created expense: ${recurring.categoryName} ₹${recurring.amount}")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("RecurringWorker", "Failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun isDue(recurring: RecurringExpenseEntity, today: Calendar): Boolean {
        return when (recurring.frequency) {
            RecurringExpenseEntity.FREQ_DAILY -> true
            RecurringExpenseEntity.FREQ_WEEKLY -> {
                val dow = recurring.dayOfWeek ?: Calendar.MONDAY
                today.get(Calendar.DAY_OF_WEEK) == dow
            }
            RecurringExpenseEntity.FREQ_MONTHLY -> {
                val dom = recurring.dayOfMonth ?: 1
                val todayDom = today.get(Calendar.DAY_OF_MONTH)
                val lastDayOfMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)
                todayDom == dom || (dom > lastDayOfMonth && todayDom == lastDayOfMonth)
            }
            RecurringExpenseEntity.FREQ_YEARLY -> {
                val dom = recurring.dayOfMonth ?: 1
                val moy = recurring.monthOfYear ?: 1
                val todayDom = today.get(Calendar.DAY_OF_MONTH)
                val lastDayOfMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)
                val dayMatch = todayDom == dom || (dom > lastDayOfMonth && todayDom == lastDayOfMonth)
                dayMatch && (today.get(Calendar.MONTH) + 1) == moy
            }
            else -> false
        }
    }
}
