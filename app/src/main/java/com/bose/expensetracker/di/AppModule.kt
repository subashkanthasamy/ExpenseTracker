package com.bose.expensetracker.di

import android.content.Context
import androidx.room.Room
import com.bose.expensetracker.data.local.ExpenseTrackerDatabase
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
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.data.preferences.BiometricPreferences
import com.bose.expensetracker.data.preferences.BiometricPreferencesImpl
import com.bose.expensetracker.data.preferences.SandboxPreferences
import com.bose.expensetracker.data.preferences.SmsImportPreferences
import com.bose.expensetracker.data.preferences.SmsImportPreferencesImpl
import com.bose.expensetracker.data.preferences.ThemePreferences
import com.bose.expensetracker.data.preferences.ThemePreferencesImpl
import com.bose.expensetracker.data.repository.BudgetRepositoryImpl
import com.bose.expensetracker.data.sandbox.SandboxDataSeeder
import com.bose.expensetracker.data.sync.LocalToFirestoreMigration
import com.bose.expensetracker.domain.repository.BudgetRepository
import com.bose.expensetracker.domain.usecase.smsimport.SmsCategoryMatcher
import com.bose.expensetracker.domain.usecase.smsimport.SmsTransactionParser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ExpenseTrackerDatabase =
        Room.databaseBuilder(
            context,
            ExpenseTrackerDatabase::class.java,
            "expense_tracker.db"
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideExpenseDao(db: ExpenseTrackerDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideCategoryDao(db: ExpenseTrackerDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideAssetDao(db: ExpenseTrackerDatabase): AssetDao = db.assetDao()

    @Provides
    fun provideLiabilityDao(db: ExpenseTrackerDatabase): LiabilityDao = db.liabilityDao()

    @Provides
    @Singleton
    fun provideBiometricPreferences(@ApplicationContext context: Context): BiometricPreferences =
        BiometricPreferencesImpl(context)

    @Provides
    @Singleton
    fun provideSmsImportPreferences(@ApplicationContext context: Context): SmsImportPreferences =
        SmsImportPreferencesImpl(context)

    @Provides
    fun provideProcessedSmsDao(db: ExpenseTrackerDatabase): ProcessedSmsDao = db.processedSmsDao()

    @Provides
    fun provideReminderDao(db: ExpenseTrackerDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun providePendingSmsDao(db: ExpenseTrackerDatabase): PendingSmsDao = db.pendingSmsDao()

    @Provides
    fun provideBudgetDao(db: ExpenseTrackerDatabase): BudgetDao = db.budgetDao()

    @Provides
    @Singleton
    fun provideBudgetRepository(
        firestoreDataSource: FirestoreDataSource,
        expenseDao: ExpenseDao
    ): BudgetRepository =
        BudgetRepositoryImpl(firestoreDataSource, expenseDao)

    @Provides
    fun provideRecurringExpenseDao(db: ExpenseTrackerDatabase): RecurringExpenseDao =
        db.recurringExpenseDao()

    @Provides
    fun provideSavingsGoalDao(db: ExpenseTrackerDatabase): SavingsGoalDao =
        db.savingsGoalDao()

    @Provides
    @Singleton
    fun provideThemePreferences(@ApplicationContext context: Context): ThemePreferences =
        ThemePreferencesImpl(context)

    @Provides
    @Singleton
    fun provideSandboxPreferences(@ApplicationContext context: Context): SandboxPreferences =
        SandboxPreferences(context)

    @Provides
    @Singleton
    fun provideLocalToFirestoreMigration(
        @ApplicationContext context: Context,
        budgetDao: BudgetDao,
        savingsGoalDao: SavingsGoalDao,
        recurringExpenseDao: RecurringExpenseDao,
        firestoreDataSource: FirestoreDataSource
    ): LocalToFirestoreMigration =
        LocalToFirestoreMigration(context, budgetDao, savingsGoalDao, recurringExpenseDao, firestoreDataSource)

    @Provides
    @Singleton
    fun provideSandboxDataSeeder(categoryDao: CategoryDao, expenseDao: ExpenseDao): SandboxDataSeeder =
        SandboxDataSeeder(categoryDao, expenseDao)

    // These live in :shared (pure Kotlin, so no @Inject constructor) — bind explicitly.
    @Provides
    @Singleton
    fun provideSmsTransactionParser(): SmsTransactionParser = SmsTransactionParser()

    @Provides
    @Singleton
    fun provideSmsCategoryMatcher(): SmsCategoryMatcher = SmsCategoryMatcher()
}
