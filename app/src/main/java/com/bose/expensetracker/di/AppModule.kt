package com.bose.expensetracker.di

import android.content.Context
import androidx.room.Room
import com.bose.expensetracker.data.local.ExpenseTrackerDatabase
import com.bose.expensetracker.data.local.dao.AssetDao
import com.bose.expensetracker.data.local.dao.CategoryDao
import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.local.dao.LiabilityDao
import com.bose.expensetracker.data.local.dao.ProcessedSmsDao
import com.bose.expensetracker.data.preferences.BiometricPreferences
import com.bose.expensetracker.data.preferences.SmsImportPreferences
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
        BiometricPreferences(context)

    @Provides
    @Singleton
    fun provideSmsImportPreferences(@ApplicationContext context: Context): SmsImportPreferences =
        SmsImportPreferences(context)

    @Provides
    fun provideProcessedSmsDao(db: ExpenseTrackerDatabase): ProcessedSmsDao = db.processedSmsDao()
}
