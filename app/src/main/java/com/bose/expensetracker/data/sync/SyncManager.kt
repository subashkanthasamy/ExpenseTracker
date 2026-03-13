package com.bose.expensetracker.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.domain.repository.NetWorthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val netWorthRepository: NetWorthRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            expenseRepository.syncPendingExpenses()
            categoryRepository.syncPendingCategories()
            netWorthRepository.syncPending()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object SyncManager {
    private const val SYNC_WORK_NAME = "expense_tracker_sync"

    fun schedulePeriodicSync(context: Context) {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
