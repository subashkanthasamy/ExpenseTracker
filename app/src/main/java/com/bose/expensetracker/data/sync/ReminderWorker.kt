package com.bose.expensetracker.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bose.expensetracker.data.local.dao.BudgetDao
import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.local.dao.ReminderDao
import com.bose.expensetracker.data.local.entity.ReminderEntity
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.bose.expensetracker.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val reminderDao: ReminderDao,
    private val notificationHelper: NotificationHelper,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ReminderWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val userId = authRepository.getCurrentUserId() ?: return Result.success()
            val reminders = reminderDao.getEnabledReminders(userId)
            if (reminders.isEmpty()) return Result.success()

            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)
            val currentDay = now.get(Calendar.DAY_OF_MONTH)

            for (reminder in reminders) {
                when (reminder.type) {
                    ReminderEntity.TYPE_DAILY -> handleDailyReminder(reminder, currentHour, currentMinute)
                    ReminderEntity.TYPE_BILL -> handleBillReminder(reminder, currentHour, currentDay)
                    ReminderEntity.TYPE_BUDGET -> handleBudgetReminder(reminder, userId)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Reminder check failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun handleDailyReminder(reminder: ReminderEntity, currentHour: Int, currentMinute: Int) {
        // Fire if within 30 minutes of scheduled time
        val scheduledMinutes = reminder.hour * 60 + reminder.minute
        val currentMinutes = currentHour * 60 + currentMinute
        val diff = currentMinutes - scheduledMinutes

        if (diff in 0..30) {
            Log.d(TAG, "Firing daily reminder: ${reminder.title}")
            notificationHelper.showDailyReminderNotification()
        }
    }

    private fun handleBillReminder(reminder: ReminderEntity, currentHour: Int, currentDay: Int) {
        // Fire on the due day or the day before, at the scheduled hour
        val dueDay = reminder.dueDay
        if (dueDay <= 0) return

        val isDueDay = currentDay == dueDay
        val isDayBefore = currentDay == dueDay - 1 || (dueDay == 1 && currentDay >= 28)

        if ((isDueDay || isDayBefore) && currentHour >= reminder.hour) {
            Log.d(TAG, "Firing bill reminder: ${reminder.title}")
            notificationHelper.showBillDueNotification(reminder.title, reminder.amount)
        }
    }

    private suspend fun handleBudgetReminder(reminder: ReminderEntity, userId: String) {
        val hId = householdRepository.getUserHouseholdId(userId) ?: return

        val now = Calendar.getInstance()
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val monthEnd = now.timeInMillis

        val spending = expenseDao.getCategorySpending(hId, monthStart, monthEnd)
        val spendingMap = spending.associate { it.categoryId to it.total }

        val budgets = budgetDao.getBudgetsSuspend(hId)
        for (budget in budgets) {
            val spent = spendingMap[budget.categoryId] ?: 0.0
            if (spent >= budget.monthlyLimit * 0.9) {
                Log.d(TAG, "Budget alert: ${budget.categoryName} spent=$spent limit=${budget.monthlyLimit}")
                notificationHelper.showBudgetAlertNotification(spent, budget.monthlyLimit)
            }
        }
    }
}
