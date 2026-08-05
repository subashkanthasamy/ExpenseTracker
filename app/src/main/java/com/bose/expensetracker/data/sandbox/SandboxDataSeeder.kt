package com.bose.expensetracker.data.sandbox

import com.bose.expensetracker.data.preferences.SandboxConstants
import com.bose.expensetracker.data.local.dao.CategoryDao
import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.local.entity.CategoryEntity
import com.bose.expensetracker.data.local.entity.ExpenseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class SandboxDataSeeder(
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun seedData() {
        scope.launch {
            // Seed categories
            seedCategories()
            // Seed expenses
            seedExpenses()
        }
    }

    /**
     * Seeds only when the sandbox household has no expenses yet. Previously this always
     * seeded, so every entry into demo mode duplicated the whole data set.
     */
    fun seedIfNeeded() {
        scope.launch {
            if (expenseDao.countExpenses(SandboxConstants.SANDBOX_HOUSEHOLD_ID) == 0) {
                seedCategories()
                seedExpenses()
            }
        }
    }

    fun clearSandboxData() {
        // This would clear existing sandbox data
        // For now let's just implement a basic approach
        scope.launch {
            // In a real app, we'd want to delete specific sandbox data
            // For now, we're leaving this as a placeholder
        }
    }

    private suspend fun seedCategories() {
        val categories = listOf(
            CategoryEntity(
                id = "1",
                name = "Groceries",
                icon = "🛒",
                color = 0xFF4CAF50,
                isPreset = true,
                householdId = SandboxConstants.SANDBOX_HOUSEHOLD_ID
            ),
            CategoryEntity(
                id = "2",
                name = "Utilities",
                icon = "💡",
                color = 0xFF2196F3,
                isPreset = true,
                householdId = SandboxConstants.SANDBOX_HOUSEHOLD_ID
            ),
            CategoryEntity(
                id = "3",
                name = "Transportation",
                icon = "🚗",
                color = 0xFFFF9800,
                isPreset = true,
                householdId = SandboxConstants.SANDBOX_HOUSEHOLD_ID
            ),
            CategoryEntity(
                id = "4",
                name = "Entertainment",
                icon = "🎬",
                color = 0xFF9C27B0,
                isPreset = true,
                householdId = SandboxConstants.SANDBOX_HOUSEHOLD_ID
            ),
            CategoryEntity(
                id = "5",
                name = "Healthcare",
                icon = "🏥",
                color = 0xFFF44336,
                isPreset = true,
                householdId = SandboxConstants.SANDBOX_HOUSEHOLD_ID
            ),
            CategoryEntity(
                id = "6",
                name = "Dining Out",
                icon = "🍽️",
                color = 0xFFFF5722,
                isPreset = true,
                householdId = SandboxConstants.SANDBOX_HOUSEHOLD_ID
            ),
            CategoryEntity(
                id = "7",
                name = "Shopping",
                icon = "🛍️",
                color = 0xFF607D8B,
                isPreset = true,
                householdId = SandboxConstants.SANDBOX_HOUSEHOLD_ID
            ),
            CategoryEntity(
                id = "8",
                name = "Salary",
                icon = "💰",
                color = 0xFF4CAF50,
                isPreset = true,
                householdId = SandboxConstants.SANDBOX_HOUSEHOLD_ID
            )
        )
        categoryDao.insertAll(categories)
    }

    private suspend fun seedExpenses() {
        // Categories 1..7 are spending categories; 8 is Salary, which is income and must
        // never be used for an expense (it previously was, so demo data showed a negative
        // "Salary" entry).
        val spendingCategories = listOf(
            1 to "Groceries",
            2 to "Utilities",
            3 to "Transportation",
            4 to "Entertainment",
            5 to "Healthcare",
            6 to "Dining Out",
            7 to "Shopping"
        )

        // Plausible notes per category, so demo data reads like real spending rather than
        // "Sandbox expense 42".
        val notesByCategory = mapOf(
            "Groceries" to listOf("Big Bazaar", "Weekly vegetables", "More Supermarket", "Milk and eggs"),
            "Utilities" to listOf("Electricity bill", "Broadband", "Water bill", "Gas cylinder"),
            "Transportation" to listOf("Petrol", "Uber to office", "Metro card top-up", "Car service"),
            "Entertainment" to listOf("Netflix", "Cinema tickets", "Spotify", "Weekend outing"),
            "Healthcare" to listOf("Pharmacy", "Doctor consultation", "Lab test", "Dental checkup"),
            "Dining Out" to listOf("Swiggy order", "Cafe Coffee Day", "Team lunch", "Dinner out"),
            "Shopping" to listOf("Amazon order", "Clothes", "Footwear", "Home supplies")
        )

        // Typical spend bands, so totals look sensible instead of uniformly random.
        val amountRanges = mapOf(
            "Groceries" to (400..2500),
            "Utilities" to (500..3000),
            "Transportation" to (100..1200),
            "Entertainment" to (200..1500),
            "Healthcare" to (250..2000),
            "Dining Out" to (150..1800),
            "Shopping" to (500..4000)
        )

        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val expenses = mutableListOf<ExpenseEntity>()

        // Weighted towards the last month so the dashboard, weekly trend and insights all
        // have something to show; the rest spread over the past year for history.
        repeat(120) { index ->
            val (categoryId, categoryName) = spendingCategories.random()
            val range = amountRanges.getValue(categoryName)
            val daysAgo = if (index < 45) Random.nextInt(0, 30) else Random.nextInt(30, 365)

            expenses += ExpenseEntity(
                id = "sandbox_expense_${index + 1}",
                householdId = SandboxConstants.SANDBOX_HOUSEHOLD_ID,
                amount = Random.nextInt(range.first, range.last).toDouble(),
                categoryId = categoryId.toString(),
                categoryName = categoryName,
                date = now - daysAgo * day,
                notes = notesByCategory.getValue(categoryName).random(),
                addedBy = SandboxConstants.SANDBOX_USER_ID,
                addedByName = SandboxConstants.SANDBOX_DISPLAY_NAME,
                createdAt = now,
                updatedAt = now
            )
        }

        expenseDao.insertAll(expenses)
    }
}