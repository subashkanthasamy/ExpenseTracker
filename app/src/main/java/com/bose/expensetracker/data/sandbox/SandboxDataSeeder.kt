package com.bose.expensetracker.data.sandbox

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

    fun seedIfNeeded() {
        // This would check if data already exists and only seed if needed
        seedData()
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
                householdId = "sandbox_household_id"
            ),
            CategoryEntity(
                id = "2",
                name = "Utilities",
                icon = "💡",
                color = 0xFF2196F3,
                isPreset = true,
                householdId = "sandbox_household_id"
            ),
            CategoryEntity(
                id = "3",
                name = "Transportation",
                icon = "🚗",
                color = 0xFFFF9800,
                isPreset = true,
                householdId = "sandbox_household_id"
            ),
            CategoryEntity(
                id = "4",
                name = "Entertainment",
                icon = "🎬",
                color = 0xFF9C27B0,
                isPreset = true,
                householdId = "sandbox_household_id"
            ),
            CategoryEntity(
                id = "5",
                name = "Healthcare",
                icon = "🏥",
                color = 0xFFF44336,
                isPreset = true,
                householdId = "sandbox_household_id"
            ),
            CategoryEntity(
                id = "6",
                name = "Dining Out",
                icon = "🍽️",
                color = 0xFFFF5722,
                isPreset = true,
                householdId = "sandbox_household_id"
            ),
            CategoryEntity(
                id = "7",
                name = "Shopping",
                icon = "🛍️",
                color = 0xFF607D8B,
                isPreset = true,
                householdId = "sandbox_household_id"
            ),
            CategoryEntity(
                id = "8",
                name = "Salary",
                icon = "💰",
                color = 0xFF4CAF50,
                isPreset = true,
                householdId = "sandbox_household_id"
            )
        )
        categoryDao.insertAll(categories)
    }

    private suspend fun seedExpenses() {
        val expenseCount = 100
        val expenses = mutableListOf<ExpenseEntity>()
        val categories = listOf(
            "Groceries", "Utilities", "Transportation", "Entertainment", "Healthcare", "Dining Out", "Shopping", "Salary"
        )

        for (i in 1..expenseCount) {
            val categoryId = Random.nextInt(1, 9).toString()
            val categoryName = categories[categoryId.toInt() - 1]
            
            val expense = ExpenseEntity(
                id = i.toString(),
                householdId = "sandbox_household_id",
                amount = Random.nextInt(10, 500).toDouble(),
                categoryId = categoryId,
                categoryName = categoryName,
                date = System.currentTimeMillis() - Random.nextInt(0, 365) * 24L * 60 * 60 * 1000,
                notes = "Sandbox expense $i",
                addedBy = "sandbox_user_id",
                addedByName = "Demo User",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            expenses.add(expense)
        }

        expenseDao.insertAll(expenses)
    }
}