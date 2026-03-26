package com.bose.expensetracker.data.sandbox

import com.bose.expensetracker.data.local.dao.AssetDao
import com.bose.expensetracker.data.local.dao.BudgetDao
import com.bose.expensetracker.data.local.dao.CategoryDao
import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.local.dao.LiabilityDao
import com.bose.expensetracker.data.local.dao.RecurringExpenseDao
import com.bose.expensetracker.data.local.dao.ReminderDao
import com.bose.expensetracker.data.local.dao.SavingsGoalDao
import com.bose.expensetracker.data.local.entity.AssetEntity
import com.bose.expensetracker.data.local.entity.BudgetEntity
import com.bose.expensetracker.data.local.entity.CategoryEntity
import com.bose.expensetracker.data.local.entity.ExpenseEntity
import com.bose.expensetracker.data.local.entity.LiabilityEntity
import com.bose.expensetracker.data.local.entity.RecurringExpenseEntity
import com.bose.expensetracker.data.local.entity.ReminderEntity
import com.bose.expensetracker.data.local.entity.SavingsGoalEntity
import com.bose.expensetracker.data.local.entity.SyncStatus
import com.bose.expensetracker.data.preferences.SandboxConstants
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SandboxDataSeeder @Inject constructor(
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val assetDao: AssetDao,
    private val liabilityDao: LiabilityDao,
    private val budgetDao: BudgetDao,
    private val recurringExpenseDao: RecurringExpenseDao,
    private val reminderDao: ReminderDao,
    private val savingsGoalDao: SavingsGoalDao
) {
    private val hId = SandboxConstants.SANDBOX_HOUSEHOLD_ID
    private val userId = SandboxConstants.SANDBOX_USER_ID
    private val userName = SandboxConstants.SANDBOX_DISPLAY_NAME

    suspend fun seedIfNeeded() {
        val existing = categoryDao.getAllCategoriesOnce(hId)
        if (existing.isNotEmpty()) return

        seedCategories()
        seedExpenses()
        seedBudgets()
        seedAssets()
        seedLiabilities()
        seedRecurringExpenses()
        seedReminders()
        seedSavingsGoals()
    }

    suspend fun clearSandboxData() {
        expenseDao.deleteAllForHousehold(hId)
        categoryDao.deleteAllForHousehold(hId)
        assetDao.deleteAllForHousehold(hId)
        liabilityDao.deleteAllForHousehold(hId)
    }

    // ── Categories ──────────────────────────────────────────────────────

    private suspend fun seedCategories() {
        val categories = listOf(
            cat("Food & Dining", "restaurant", 0xFFFF9800),
            cat("Transport", "directions_car", 0xFF2196F3),
            cat("Shopping", "shopping_cart", 0xFFE91E63),
            cat("Bills & Utilities", "payments", 0xFFFF5722),
            cat("Health", "health_and_safety", 0xFFF44336),
            cat("Entertainment", "movie", 0xFF9C27B0),
            cat("Education", "school", 0xFF3F51B5),
            cat("Other", "receipt", 0xFF607D8B)
        )
        categories.forEach { categoryDao.insert(it) }
    }

    // ── Expenses ────────────────────────────────────────────────────────

    private suspend fun seedExpenses() {
        val cats = categoryDao.getAllCategoriesOnce(hId)
        val catMap = cats.associateBy { it.name }

        val now = Calendar.getInstance()
        val thisMonth = now.get(Calendar.MONTH)
        val thisYear = now.get(Calendar.YEAR)

        val lastMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val lastMonth = lastMonthCal.get(Calendar.MONTH)
        val lastMonthYear = lastMonthCal.get(Calendar.YEAR)

        val twoMonthsAgoCal = Calendar.getInstance().apply { add(Calendar.MONTH, -2) }
        val twoMonthsAgo = twoMonthsAgoCal.get(Calendar.MONTH)
        val twoMonthsAgoYear = twoMonthsAgoCal.get(Calendar.YEAR)

        val maxDay = now.get(Calendar.DAY_OF_MONTH)

        val sampleExpenses = listOf(
            // This month
            exp(catMap["Food & Dining"]!!, 1200.0, thisYear, thisMonth, (maxDay).coerceAtMost(25), 12, 45, "Zomato order"),
            exp(catMap["Transport"]!!, 450.0, thisYear, thisMonth, (maxDay).coerceAtMost(25), 9, 18, "Ola Cab to office"),
            exp(catMap["Shopping"]!!, 3200.0, thisYear, thisMonth, (maxDay).coerceAtMost(24), 16, 30, "Amazon - headphones"),
            exp(catMap["Bills & Utilities"]!!, 1500.0, thisYear, thisMonth, 20.coerceAtMost(maxDay), 11, 0, "Electricity bill"),
            exp(catMap["Health"]!!, 800.0, thisYear, thisMonth, 19.coerceAtMost(maxDay), 15, 22, "Apollo Pharmacy"),
            exp(catMap["Food & Dining"]!!, 650.0, thisYear, thisMonth, 18.coerceAtMost(maxDay), 20, 10, "Swiggy dinner"),
            exp(catMap["Entertainment"]!!, 500.0, thisYear, thisMonth, 17.coerceAtMost(maxDay), 19, 0, "Netflix + Spotify"),
            exp(catMap["Transport"]!!, 350.0, thisYear, thisMonth, 15.coerceAtMost(maxDay), 8, 30, "Metro card recharge"),
            exp(catMap["Shopping"]!!, 1800.0, thisYear, thisMonth, 12.coerceAtMost(maxDay), 14, 0, "Flipkart - shoes"),
            exp(catMap["Food & Dining"]!!, 980.0, thisYear, thisMonth, 10.coerceAtMost(maxDay), 13, 15, "Family restaurant"),
            exp(catMap["Education"]!!, 2500.0, thisYear, thisMonth, 8.coerceAtMost(maxDay), 10, 0, "Udemy course"),
            exp(catMap["Other"]!!, 400.0, thisYear, thisMonth, 5.coerceAtMost(maxDay), 17, 45, "Stationery"),
            exp(catMap["Food & Dining"]!!, 320.0, thisYear, thisMonth, 3.coerceAtMost(maxDay), 8, 0, "Milk & bread"),
            exp(catMap["Bills & Utilities"]!!, 799.0, thisYear, thisMonth, 2.coerceAtMost(maxDay), 10, 30, "Mobile recharge"),
            exp(catMap["Transport"]!!, 200.0, thisYear, thisMonth, 1.coerceAtMost(maxDay), 7, 45, "Auto rickshaw"),

            // Last month
            exp(catMap["Food & Dining"]!!, 1500.0, lastMonthYear, lastMonth, 28, 12, 0, "Groceries - BigBasket"),
            exp(catMap["Transport"]!!, 550.0, lastMonthYear, lastMonth, 25, 9, 0, "Uber to airport"),
            exp(catMap["Shopping"]!!, 2800.0, lastMonthYear, lastMonth, 22, 15, 0, "Myntra - clothes"),
            exp(catMap["Bills & Utilities"]!!, 1400.0, lastMonthYear, lastMonth, 18, 11, 0, "Internet bill - Airtel"),
            exp(catMap["Health"]!!, 1200.0, lastMonthYear, lastMonth, 15, 10, 0, "Doctor consultation"),
            exp(catMap["Food & Dining"]!!, 750.0, lastMonthYear, lastMonth, 12, 20, 0, "Dine out - BBQ Nation"),
            exp(catMap["Entertainment"]!!, 1200.0, lastMonthYear, lastMonth, 10, 18, 30, "PVR movie tickets"),
            exp(catMap["Education"]!!, 1500.0, lastMonthYear, lastMonth, 8, 9, 0, "Books - Flipkart"),
            exp(catMap["Shopping"]!!, 4500.0, lastMonthYear, lastMonth, 5, 14, 0, "Croma - charger & cable"),
            exp(catMap["Transport"]!!, 180.0, lastMonthYear, lastMonth, 3, 8, 0, "Bus pass"),
            exp(catMap["Bills & Utilities"]!!, 2200.0, lastMonthYear, lastMonth, 1, 10, 0, "Water + gas bill"),

            // Two months ago
            exp(catMap["Food & Dining"]!!, 1800.0, twoMonthsAgoYear, twoMonthsAgo, 27, 13, 0, "Weekly groceries"),
            exp(catMap["Shopping"]!!, 5500.0, twoMonthsAgoYear, twoMonthsAgo, 20, 16, 0, "Amazon - backpack"),
            exp(catMap["Health"]!!, 3500.0, twoMonthsAgoYear, twoMonthsAgo, 15, 11, 0, "Lab tests"),
            exp(catMap["Bills & Utilities"]!!, 1600.0, twoMonthsAgoYear, twoMonthsAgo, 10, 10, 0, "Electricity bill"),
            exp(catMap["Transport"]!!, 700.0, twoMonthsAgoYear, twoMonthsAgo, 8, 9, 0, "Rapido bike"),
            exp(catMap["Entertainment"]!!, 350.0, twoMonthsAgoYear, twoMonthsAgo, 5, 21, 0, "YouTube Premium"),
            exp(catMap["Food & Dining"]!!, 450.0, twoMonthsAgoYear, twoMonthsAgo, 2, 19, 30, "Chai & snacks")
        )

        sampleExpenses.forEach { expenseDao.insert(it) }
    }

    // ── Budgets ─────────────────────────────────────────────────────────

    private suspend fun seedBudgets() {
        val cats = categoryDao.getAllCategoriesOnce(hId)
        val catMap = cats.associateBy { it.name }
        val now = System.currentTimeMillis()

        val budgets = listOf(
            BudgetEntity(id = uid(), householdId = hId, categoryId = catMap["Food & Dining"]!!.id, categoryName = "Food & Dining", monthlyLimit = 5000.0, createdAt = now, updatedAt = now),
            BudgetEntity(id = uid(), householdId = hId, categoryId = catMap["Transport"]!!.id, categoryName = "Transport", monthlyLimit = 2000.0, createdAt = now, updatedAt = now),
            BudgetEntity(id = uid(), householdId = hId, categoryId = catMap["Shopping"]!!.id, categoryName = "Shopping", monthlyLimit = 5000.0, createdAt = now, updatedAt = now),
            BudgetEntity(id = uid(), householdId = hId, categoryId = catMap["Bills & Utilities"]!!.id, categoryName = "Bills & Utilities", monthlyLimit = 3000.0, createdAt = now, updatedAt = now),
            BudgetEntity(id = uid(), householdId = hId, categoryId = catMap["Entertainment"]!!.id, categoryName = "Entertainment", monthlyLimit = 1500.0, createdAt = now, updatedAt = now),
            BudgetEntity(id = uid(), householdId = hId, categoryId = catMap["Health"]!!.id, categoryName = "Health", monthlyLimit = 2000.0, createdAt = now, updatedAt = now)
        )

        budgets.forEach { budgetDao.insert(it) }
    }

    // ── Assets ──────────────────────────────────────────────────────────

    private suspend fun seedAssets() {
        val now = System.currentTimeMillis()

        val assets = listOf(
            AssetEntity(id = uid(), householdId = hId, name = "Savings Account - SBI", value = 125000.0, type = "Cash", date = now, addedBy = userId),
            AssetEntity(id = uid(), householdId = hId, name = "Salary Account - HDFC", value = 45000.0, type = "Cash", date = now, addedBy = userId),
            AssetEntity(id = uid(), householdId = hId, name = "Fixed Deposit", value = 200000.0, type = "Investment", date = now, addedBy = userId),
            AssetEntity(id = uid(), householdId = hId, name = "Mutual Funds - SIP", value = 85000.0, type = "Investment", date = now, addedBy = userId),
            AssetEntity(id = uid(), householdId = hId, name = "Gold - Digital", value = 50000.0, type = "Investment", date = now, addedBy = userId),
            AssetEntity(id = uid(), householdId = hId, name = "Emergency Fund", value = 75000.0, type = "Cash", date = now, addedBy = userId)
        )

        assets.forEach { assetDao.insert(it) }
    }

    // ── Liabilities ─────────────────────────────────────────────────────

    private suspend fun seedLiabilities() {
        val now = System.currentTimeMillis()

        val liabilities = listOf(
            LiabilityEntity(id = uid(), householdId = hId, name = "Education Loan", amount = 350000.0, type = "Loan", date = now, addedBy = userId),
            LiabilityEntity(id = uid(), householdId = hId, name = "Credit Card - HDFC", amount = 15000.0, type = "Credit Card", date = now, addedBy = userId),
            LiabilityEntity(id = uid(), householdId = hId, name = "Personal Loan - Bajaj", amount = 80000.0, type = "Loan", date = now, addedBy = userId),
            LiabilityEntity(id = uid(), householdId = hId, name = "Credit Card - ICICI", amount = 8500.0, type = "Credit Card", date = now, addedBy = userId)
        )

        liabilities.forEach { liabilityDao.insert(it) }
    }

    // ── Recurring Expenses ──────────────────────────────────────────────

    private suspend fun seedRecurringExpenses() {
        val cats = categoryDao.getAllCategoriesOnce(hId)
        val catMap = cats.associateBy { it.name }

        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val recurring = listOf(
            RecurringExpenseEntity(
                id = uid(), householdId = hId,
                amount = 199.0, categoryId = catMap["Entertainment"]!!.id, categoryName = "Entertainment",
                notes = "Netflix subscription", addedBy = userId, addedByName = userName,
                frequency = RecurringExpenseEntity.FREQ_MONTHLY, dayOfMonth = 5,
                startDate = startOfMonth, isActive = true
            ),
            RecurringExpenseEntity(
                id = uid(), householdId = hId,
                amount = 119.0, categoryId = catMap["Entertainment"]!!.id, categoryName = "Entertainment",
                notes = "Spotify subscription", addedBy = userId, addedByName = userName,
                frequency = RecurringExpenseEntity.FREQ_MONTHLY, dayOfMonth = 10,
                startDate = startOfMonth, isActive = true
            ),
            RecurringExpenseEntity(
                id = uid(), householdId = hId,
                amount = 499.0, categoryId = catMap["Bills & Utilities"]!!.id, categoryName = "Bills & Utilities",
                notes = "Airtel broadband", addedBy = userId, addedByName = userName,
                frequency = RecurringExpenseEntity.FREQ_MONTHLY, dayOfMonth = 15,
                startDate = startOfMonth, isActive = true
            ),
            RecurringExpenseEntity(
                id = uid(), householdId = hId,
                amount = 799.0, categoryId = catMap["Bills & Utilities"]!!.id, categoryName = "Bills & Utilities",
                notes = "Mobile recharge - Jio", addedBy = userId, addedByName = userName,
                frequency = RecurringExpenseEntity.FREQ_MONTHLY, dayOfMonth = 1,
                startDate = startOfMonth, isActive = true
            ),
            RecurringExpenseEntity(
                id = uid(), householdId = hId,
                amount = 300.0, categoryId = catMap["Food & Dining"]!!.id, categoryName = "Food & Dining",
                notes = "Milk subscription", addedBy = userId, addedByName = userName,
                frequency = RecurringExpenseEntity.FREQ_WEEKLY, dayOfWeek = Calendar.MONDAY,
                startDate = startOfMonth, isActive = true
            ),
            RecurringExpenseEntity(
                id = uid(), householdId = hId,
                amount = 5000.0, categoryId = catMap["Education"]!!.id, categoryName = "Education",
                notes = "Gym membership", addedBy = userId, addedByName = userName,
                frequency = RecurringExpenseEntity.FREQ_MONTHLY, dayOfMonth = 1,
                startDate = startOfMonth, isActive = true
            )
        )

        recurring.forEach { recurringExpenseDao.insert(it) }
    }

    // ── Reminders ───────────────────────────────────────────────────────

    private suspend fun seedReminders() {
        val reminders = listOf(
            ReminderEntity(
                id = uid(), userId = userId,
                type = ReminderEntity.TYPE_DAILY,
                title = "Log today's expenses",
                hour = 21, minute = 0,
                repeatInterval = ReminderEntity.REPEAT_DAILY,
                isEnabled = true
            ),
            ReminderEntity(
                id = uid(), userId = userId,
                type = ReminderEntity.TYPE_BILL,
                title = "Electricity bill due",
                amount = 1500.0,
                hour = 10, minute = 0,
                dueDay = 20,
                repeatInterval = ReminderEntity.REPEAT_MONTHLY,
                isEnabled = true
            ),
            ReminderEntity(
                id = uid(), userId = userId,
                type = ReminderEntity.TYPE_BILL,
                title = "Credit card payment",
                amount = 15000.0,
                hour = 9, minute = 0,
                dueDay = 5,
                repeatInterval = ReminderEntity.REPEAT_MONTHLY,
                isEnabled = true
            ),
            ReminderEntity(
                id = uid(), userId = userId,
                type = ReminderEntity.TYPE_BUDGET,
                title = "Weekly budget check",
                hour = 18, minute = 0,
                repeatInterval = ReminderEntity.REPEAT_WEEKLY,
                isEnabled = true
            )
        )

        reminders.forEach { reminderDao.insert(it) }
    }

    // ── Savings Goals ───────────────────────────────────────────────────

    private suspend fun seedSavingsGoals() {
        val now = System.currentTimeMillis()

        val threeMonthsLater = Calendar.getInstance().apply { add(Calendar.MONTH, 3) }.timeInMillis
        val sixMonthsLater = Calendar.getInstance().apply { add(Calendar.MONTH, 6) }.timeInMillis
        val oneYearLater = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis

        val goals = listOf(
            SavingsGoalEntity(
                id = uid(), householdId = hId,
                name = "Emergency Fund",
                targetAmount = 100000.0, currentAmount = 75000.0,
                icon = "\uD83D\uDEE1\uFE0F",
                targetDate = threeMonthsLater,
                createdAt = now, updatedAt = now
            ),
            SavingsGoalEntity(
                id = uid(), householdId = hId,
                name = "New Laptop",
                targetAmount = 80000.0, currentAmount = 32000.0,
                icon = "\uD83D\uDCBB",
                targetDate = sixMonthsLater,
                createdAt = now, updatedAt = now
            ),
            SavingsGoalEntity(
                id = uid(), householdId = hId,
                name = "Vacation - Goa Trip",
                targetAmount = 50000.0, currentAmount = 18000.0,
                icon = "\u2708\uFE0F",
                targetDate = threeMonthsLater,
                createdAt = now, updatedAt = now
            ),
            SavingsGoalEntity(
                id = uid(), householdId = hId,
                name = "Bike Down Payment",
                targetAmount = 200000.0, currentAmount = 55000.0,
                icon = "\uD83C\uDFCD\uFE0F",
                targetDate = oneYearLater,
                createdAt = now, updatedAt = now
            )
        )

        goals.forEach { savingsGoalDao.insert(it) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun uid() = UUID.randomUUID().toString()

    private fun cat(name: String, icon: String, color: Long): CategoryEntity {
        return CategoryEntity(
            id = uid(),
            name = name,
            icon = icon,
            color = color,
            isPreset = true,
            householdId = hId,
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun exp(
        category: CategoryEntity,
        amount: Double,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        notes: String
    ): ExpenseEntity {
        val cal = Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val ts = cal.timeInMillis
        return ExpenseEntity(
            id = uid(),
            householdId = hId,
            amount = amount,
            categoryId = category.id,
            categoryName = category.name,
            date = ts,
            notes = notes,
            addedBy = userId,
            addedByName = userName,
            createdAt = ts,
            updatedAt = ts,
            syncStatus = SyncStatus.SYNCED
        )
    }
}
