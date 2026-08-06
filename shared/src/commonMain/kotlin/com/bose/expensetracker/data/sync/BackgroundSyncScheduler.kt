package com.bose.expensetracker.data.sync

interface BackgroundSyncScheduler {
    fun schedulePeriodicSync(intervalMinutes: Long = 15)
    fun scheduleRecurringExpenseCheck()
    fun cancelAll()
}
