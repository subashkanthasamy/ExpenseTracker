package com.bose.expensetracker.ui.screen.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.domain.model.Budget
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.BudgetRepository
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class BudgetUiState(
    val budgets: List<Budget> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private var householdId: String? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            val hId = householdRepository.getUserHouseholdId(uid) ?: return@launch
            householdId = hId

            launch {
                budgetRepository.getBudgetsWithSpending(hId).collect { budgets ->
                    _uiState.update { it.copy(budgets = budgets, isLoading = false) }
                }
            }
            launch {
                categoryRepository.getCategories(hId).collect { categories ->
                    _uiState.update { it.copy(categories = categories) }
                }
            }
        }
    }

    fun addBudget(category: Category, limit: Double) {
        val hId = householdId ?: return
        viewModelScope.launch {
            val budget = Budget(
                id = UUID.randomUUID().toString(),
                householdId = hId,
                categoryId = category.id,
                categoryName = category.name,
                monthlyLimit = limit
            )
            budgetRepository.addBudget(budget)
        }
    }

    fun deleteBudget(id: String) {
        viewModelScope.launch {
            budgetRepository.deleteBudget(id)
        }
    }
}
