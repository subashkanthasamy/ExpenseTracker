package com.bose.expensetracker.data.repository

import com.bose.expensetracker.data.local.dao.BudgetDao
import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.local.entity.BudgetEntity
import com.bose.expensetracker.domain.model.Budget
import com.bose.expensetracker.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val expenseDao: ExpenseDao
) : BudgetRepository {

    override fun getBudgetsWithSpending(householdId: String): Flow<List<Budget>> {
        return budgetDao.getBudgets(householdId).map { budgets ->
            val (monthStart, monthEnd) = currentMonthRange()
            val spending = expenseDao.getCategorySpending(householdId, monthStart, monthEnd)
            val spendingMap = spending.associate { it.categoryId to it.total }

            budgets.map { entity ->
                Budget(
                    id = entity.id,
                    householdId = entity.householdId,
                    categoryId = entity.categoryId,
                    categoryName = entity.categoryName,
                    monthlyLimit = entity.monthlyLimit,
                    spent = spendingMap[entity.categoryId] ?: 0.0
                )
            }
        }
    }

    override suspend fun addBudget(budget: Budget): Result<Unit> = runCatching {
        budgetDao.insert(
            BudgetEntity(
                id = budget.id,
                householdId = budget.householdId,
                categoryId = budget.categoryId,
                categoryName = budget.categoryName,
                monthlyLimit = budget.monthlyLimit
            )
        )
    }

    override suspend fun deleteBudget(id: String): Result<Unit> = runCatching {
        budgetDao.deleteById(id)
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
