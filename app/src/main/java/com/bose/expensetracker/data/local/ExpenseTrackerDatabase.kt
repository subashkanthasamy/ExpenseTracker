package com.bose.expensetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bose.expensetracker.data.local.dao.AssetDao
import com.bose.expensetracker.data.local.dao.BudgetDao
import com.bose.expensetracker.data.local.dao.RecurringExpenseDao
import com.bose.expensetracker.data.local.dao.SavingsGoalDao
import com.bose.expensetracker.data.local.dao.CategoryDao
import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.local.dao.LiabilityDao
import com.bose.expensetracker.data.local.dao.PendingSmsDao
import com.bose.expensetracker.data.local.dao.ProcessedSmsDao
import com.bose.expensetracker.data.local.dao.ReminderDao
import com.bose.expensetracker.data.local.entity.AssetEntity
import com.bose.expensetracker.data.local.entity.BudgetEntity
import com.bose.expensetracker.data.local.entity.RecurringExpenseEntity
import com.bose.expensetracker.data.local.entity.SavingsGoalEntity
import com.bose.expensetracker.data.local.entity.CategoryEntity
import com.bose.expensetracker.data.local.entity.ExpenseEntity
import com.bose.expensetracker.data.local.entity.LiabilityEntity
import com.bose.expensetracker.data.local.entity.PendingSmsEntity
import com.bose.expensetracker.data.local.entity.ProcessedSmsEntity
import com.bose.expensetracker.data.local.entity.ReminderEntity

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        AssetEntity::class,
        LiabilityEntity::class,
        ProcessedSmsEntity::class,
        ReminderEntity::class,
        PendingSmsEntity::class,
        BudgetEntity::class,
        RecurringExpenseEntity::class,
        SavingsGoalEntity::class
],
    version = 7,
    exportSchema = true
)
abstract class ExpenseTrackerDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun assetDao(): AssetDao
    abstract fun liabilityDao(): LiabilityDao
    abstract fun processedSmsDao(): ProcessedSmsDao
    abstract fun reminderDao(): ReminderDao
    abstract fun pendingSmsDao(): PendingSmsDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun savingsGoalDao(): SavingsGoalDao
}
