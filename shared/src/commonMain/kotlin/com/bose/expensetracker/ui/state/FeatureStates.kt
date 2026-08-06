package com.bose.expensetracker.ui.state

import com.bose.expensetracker.domain.model.Asset
import com.bose.expensetracker.domain.model.Budget
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.Liability
import com.bose.expensetracker.domain.model.RecurringExpense
import com.bose.expensetracker.domain.model.SavingsGoal
import com.bose.expensetracker.domain.model.User
import com.bose.expensetracker.data.preferences.ThemePreferences

// Budget
data class BudgetUiState(
    val budgets: List<Budget> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

// Category
data class CategoryUiState(
    val presetCategories: List<Category> = emptyList(),
    val customCategories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

// Net Worth
data class NetWorthHistoryEntry(
    val label: String,
    val value: Double
)

data class NetWorthUiState(
    val totalAssets: Double = 0.0,
    val totalLiabilities: Double = 0.0,
    val netWorth: Double = 0.0,
    val assets: List<Asset> = emptyList(),
    val liabilities: List<Liability> = emptyList(),
    val history: List<NetWorthHistoryEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

// Household
data class HouseholdUiState(
    val household: Household? = null,
    val households: List<Household> = emptyList(),
    val members: List<User> = emptyList(),
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null
)

// Settings
data class SettingsUiState(
    val user: User? = null,
    val household: Household? = null,
    val households: List<Household> = emptyList(),
    val members: List<User> = emptyList(),
    val biometricEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val dummyDataMessage: String? = null,
    val isResetting: Boolean = false,
    val resetMessage: String? = null,
    val smsImportEnabled: Boolean = false,
    val themeMode: Int = ThemePreferences.THEME_SYSTEM,
    val errorMessage: String? = null
)

// Savings
data class SavingsUiState(
    val goals: List<SavingsGoal> = emptyList(),
    val isLoading: Boolean = true
)

// Recurring
data class RecurringUiState(
    val items: List<RecurringExpense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

// Financial Coach
data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val inlineStats: List<InlineStat>? = null
)

data class InlineStat(
    val emoji: String,
    val label: String,
    val value: String,
    val isPositive: Boolean
)

data class CoachUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val financialScore: Int = 78,
    val suggestions: List<String> = listOf(
        "Can I save this month?",
        "My financial score",
        "Invest"
    )
)
