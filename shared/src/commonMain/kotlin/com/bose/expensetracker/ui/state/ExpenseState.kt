package com.bose.expensetracker.ui.state

import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.model.Expense
import kotlinx.datetime.Clock

data class ExpenseListUiState(
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val personFilter: String? = null,
    val categoryFilter: String? = null,
    val error: String? = null
)

data class AddEditExpenseUiState(
    val amount: String = "",
    val selectedCategory: Category? = null,
    val categories: List<Category> = emptyList(),
    val date: Long = Clock.System.now().toEpochMilliseconds(),
    val notes: String = "",
    val addedByName: String = "",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)
