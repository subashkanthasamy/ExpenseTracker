package com.bose.expensetracker.data.sync

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.bose.expensetracker.data.local.dao.BudgetDao
import com.bose.expensetracker.data.local.dao.RecurringExpenseDao
import com.bose.expensetracker.data.local.dao.SavingsGoalDao
import com.bose.expensetracker.data.preferences.dataStore
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.Budget
import com.bose.expensetracker.domain.model.RecurringExpense
import com.bose.expensetracker.domain.model.RecurringFrequency
import com.bose.expensetracker.domain.model.SavingsGoal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time upload of budgets, savings goals and recurring rules from Room to Firestore.
 *
 * These three used to be stored only in Room, so an existing user's data lives locally. Now
 * that the app reads them from Firestore, without this migration their budgets and goals
 * would simply appear to vanish after updating.
 *
 * Writes are keyed by the existing row id, so running twice is harmless, and a flag stops it
 * running again once it has succeeded.
 */
@Singleton
class LocalToFirestoreMigration @Inject constructor(
    private val context: Context,
    private val budgetDao: BudgetDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val recurringExpenseDao: RecurringExpenseDao,
    private val firestoreDataSource: FirestoreDataSource
) {

    private val migratedKey = booleanPreferencesKey("migrated_local_finance_data_v1")

    suspend fun runIfNeeded(householdId: String) {
        try {
            val alreadyDone = context.dataStore.data.first()[migratedKey] ?: false
            if (alreadyDone) return

            var uploaded = 0

            budgetDao.getBudgets(householdId).first().forEach { entity ->
                firestoreDataSource.upsertBudget(
                    Budget(
                        id = entity.id,
                        householdId = entity.householdId,
                        categoryId = entity.categoryId,
                        categoryName = entity.categoryName,
                        monthlyLimit = entity.monthlyLimit
                    )
                )
                uploaded++
            }

            savingsGoalDao.getGoals(householdId).first().forEach { entity ->
                firestoreDataSource.upsertSavingsGoal(
                    SavingsGoal(
                        id = entity.id,
                        householdId = entity.householdId,
                        name = entity.name,
                        targetAmount = entity.targetAmount,
                        currentAmount = entity.currentAmount,
                        icon = entity.icon,
                        targetDate = entity.targetDate,
                        createdAt = entity.createdAt
                    )
                )
                uploaded++
            }

            recurringExpenseDao.getAll(householdId).first().forEach { entity ->
                firestoreDataSource.upsertRecurringExpense(
                    RecurringExpense(
                        id = entity.id,
                        householdId = entity.householdId,
                        amount = entity.amount,
                        categoryId = entity.categoryId,
                        categoryName = entity.categoryName,
                        notes = entity.notes,
                        addedBy = entity.addedBy,
                        addedByName = entity.addedByName,
                        frequency = RecurringFrequency.entries
                            .getOrElse(entity.frequency) { RecurringFrequency.MONTHLY },
                        dayOfWeek = entity.dayOfWeek,
                        dayOfMonth = entity.dayOfMonth,
                        monthOfYear = entity.monthOfYear,
                        startDate = entity.startDate,
                        endDate = entity.endDate,
                        lastGeneratedDate = entity.lastGeneratedDate,
                        isActive = entity.isActive,
                        createdAt = entity.createdAt
                    )
                )
                uploaded++
            }

            context.dataStore.edit { it[migratedKey] = true }
            Log.d("LocalMigration", "Uploaded $uploaded local finance records to Firestore")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Leave the flag unset so it is retried next launch rather than losing data.
            Log.e("LocalMigration", "Migration failed; will retry next launch", e)
        }
    }
}
