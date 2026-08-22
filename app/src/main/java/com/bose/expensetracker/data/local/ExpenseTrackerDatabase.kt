package com.bose.expensetracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import com.bose.expensetracker.data.local.dao.AssetDao
import com.bose.expensetracker.data.local.dao.BudgetDao
import com.bose.expensetracker.data.local.dao.CategoryDao
import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.local.dao.LiabilityDao
import com.bose.expensetracker.data.local.dao.PendingSmsDao
import com.bose.expensetracker.data.local.dao.ProcessedSmsDao
import com.bose.expensetracker.data.local.dao.RecurringExpenseDao
import com.bose.expensetracker.data.local.dao.ReminderDao
import com.bose.expensetracker.data.local.dao.SavingsGoalDao
import com.bose.expensetracker.data.local.entity.AssetEntity
import com.bose.expensetracker.data.local.entity.BudgetEntity
import com.bose.expensetracker.data.local.entity.CategoryEntity
import com.bose.expensetracker.data.local.entity.ExpenseEntity
import com.bose.expensetracker.data.local.entity.LiabilityEntity
import com.bose.expensetracker.data.local.entity.PendingSmsEntity
import com.bose.expensetracker.data.local.entity.ProcessedSmsEntity
import com.bose.expensetracker.data.local.entity.RecurringExpenseEntity
import com.bose.expensetracker.data.local.entity.ReminderEntity
import com.bose.expensetracker.data.local.entity.SavingsGoalEntity

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
    version = 9,
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

/**
 * Adds `paymentMethod` to `expenses` and to the pending-SMS queue.
 *
 * Written out rather than left to `fallbackToDestructiveMigration()`, which would drop the
 * table. Room is the offline cache and holds rows with `SyncStatus.PENDING_CREATE` — expenses
 * added without a connection and not yet pushed to Firestore. A destructive migration would
 * delete exactly those, and only for users who happened to be offline at upgrade time, which
 * is the kind of data loss that never shows up in testing.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE pending_sms ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Adds `expenses.scope`.
 *
 * A separate step rather than an edit to [MIGRATION_7_8], because v8 is already committed and
 * may be installed. Room chains migrations, so a device on 7 runs 7->8 then 8->9 while one on 8
 * runs only 8->9; folding this into v8 would corrupt the latter.
 *
 * Written out rather than left to `fallbackToDestructiveMigration()`, which drops the table —
 * and with it any row still marked PENDING_CREATE, i.e. expenses added offline and not yet
 * pushed. That is data loss only for users who happened to be offline at upgrade time, which is
 * exactly the kind that never shows up in testing.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN scope TEXT NOT NULL DEFAULT ''")
    }
}
