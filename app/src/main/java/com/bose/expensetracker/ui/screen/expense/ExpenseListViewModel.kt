package com.bose.expensetracker.ui.screen.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExpenseListUiState(
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val personFilter: String? = null,
    val categoryFilter: String? = null,
    val error: String? = null
)

@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseListUiState())
    val uiState: StateFlow<ExpenseListUiState> = _uiState.asStateFlow()

    private var householdId: String? = null
    private val _searchQuery = MutableStateFlow("")
    private val _personFilter = MutableStateFlow<String?>(null)
    private val _categoryFilter = MutableStateFlow<String?>(null)

    init {
        loadExpenses()
    }

    private fun loadExpenses() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            householdId = householdRepository.getUserHouseholdId(userId)
            val hId = householdId ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            expenseRepository.startRealtimeSync(hId)

            combine(
                expenseRepository.getExpenses(hId),
                _searchQuery,
                _personFilter,
                _categoryFilter
            ) { expenses, query, person, category ->
                filterExpenses(expenses, query, person, category)
            }.collect { filtered ->
                _uiState.update { state ->
                    state.copy(
                        expenses = filtered,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setPersonFilter(userId: String?) {
        _personFilter.value = userId
        _uiState.update { it.copy(personFilter = userId) }
    }

    fun setCategoryFilter(categoryId: String?) {
        _categoryFilter.value = categoryId
        _uiState.update { it.copy(categoryFilter = categoryId) }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expenseId)
        }
    }

    private fun filterExpenses(
        expenses: List<Expense>,
        query: String,
        personFilter: String?,
        categoryFilter: String?
    ): List<Expense> {
        return expenses.filter { expense ->
            val matchesQuery = query.isBlank() ||
                    expense.notes.contains(query, ignoreCase = true) ||
                    expense.categoryName.contains(query, ignoreCase = true)
            val matchesPerson = personFilter == null || expense.addedBy == personFilter
            val matchesCategory = categoryFilter == null || expense.categoryId == categoryFilter
            matchesQuery && matchesPerson && matchesCategory
        }
    }

    override fun onCleared() {
        super.onCleared()
        expenseRepository.stopRealtimeSync()
    }
}
