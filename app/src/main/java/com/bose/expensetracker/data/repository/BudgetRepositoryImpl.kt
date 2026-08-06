package com.bose.expensetracker.data.repository

import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.Budget
import com.bose.expensetracker.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Budget limits live in Firestore so they sync across devices and household members and
 * survive a reinstall — they were previously Room-only, which meant neither.
 *
 * Spend totals still come from the local expense cache: expenses are already synced through
 * Firestore into Room, and summing locally keeps the flow reactive without extra reads.
 */
@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val expenseDao: ExpenseDao
) : BudgetRepository {

    override fun getBudgetsWithSpending(householdId: String): Flow<List<Budget>> {
        return firestoreDataSource.observeBudgets(householdId).map { budgets ->
            val (monthStart, monthEnd) = currentMonthRange()
            val spendingMap = expenseDao.getCategorySpending(householdId, monthStart, monthEnd)
                .associate { it.categoryId to it.total }

            budgets.map { budget ->
                budget.copy(spent = spendingMap[budget.categoryId] ?: 0.0)
            }
        }
    }

    override suspend fun addBudget(budget: Budget): Result<Unit> = runCatching {
        firestoreDataSource.upsertBudget(budget)
    }

    override suspend fun deleteBudget(householdId: String, id: String): Result<Unit> = runCatching {
        firestoreDataSource.deleteBudget(householdId, id)
    }

    private fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis
        return start to end
    }
}
