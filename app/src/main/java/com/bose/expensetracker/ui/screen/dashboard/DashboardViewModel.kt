package com.bose.expensetracker.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class CategoryBreakdown(
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val color: Long
)

data class HouseholdMember(
    val uid: String,
    val displayName: String
)

data class DashboardUiState(
    val monthTotal: Double = 0.0,
    val lastMonthTotal: Double = 0.0,
    val recentExpenses: List<Expense> = emptyList(),
    val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
    val personFilter: String? = null,
    val members: List<HouseholdMember> = emptyList(),
    val isLoading: Boolean = true,
    val noHousehold: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _personFilter = MutableStateFlow<String?>(null)
    private var householdId: String? = null

    init {
        loadDashboard()
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            // Retry up to 3 times — householdId may not be available immediately
            var hId: String? = null
            for (attempt in 1..3) {
                hId = householdRepository.getUserHouseholdId(userId)
                if (hId != null) break
                if (attempt < 3) kotlinx.coroutines.delay(1000L)
            }
            householdId = hId
            if (hId == null) {
                _uiState.update { it.copy(isLoading = false, noHousehold = true) }
                return@launch
            }

            // Load household members
            householdRepository.getHouseholdMembers(hId).onSuccess { users ->
                _uiState.update { state ->
                    state.copy(members = users.map { HouseholdMember(it.uid, it.displayName) })
                }
            }

            // Seed preset categories BEFORE starting sync to avoid race condition
            val existingCats = categoryRepository.getCategories(hId).firstOrNull() ?: emptyList()
            if (existingCats.none { it.isPreset }) {
                categoryRepository.seedPresetCategories(hId)
            }

            expenseRepository.startRealtimeSync(hId)
            categoryRepository.startRealtimeSync(hId)

            // Combine expenses with categories and person filter
            combine(
                expenseRepository.getExpenses(hId),
                categoryRepository.getCategories(hId),
                _personFilter
            ) { expenses, categories, filter ->
                Triple(expenses, categories, filter)
            }.collect { (allExpenses, categories, filter) ->
                val categoryColorMap = categories.associate { it.name to it.color }

                val now = Calendar.getInstance()
                val currentMonth = now.get(Calendar.MONTH)
                val currentYear = now.get(Calendar.YEAR)

                val lastMonthCal = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1)
                }
                val lastMonth = lastMonthCal.get(Calendar.MONTH)
                val lastMonthYear = lastMonthCal.get(Calendar.YEAR)

                val expenseCal = Calendar.getInstance()

                // Apply person filter
                val filteredExpenses = if (filter != null) {
                    allExpenses.filter { it.addedBy == filter }
                } else {
                    allExpenses
                }

                val thisMonthExpenses = filteredExpenses.filter { expense ->
                    expenseCal.timeInMillis = expense.date
                    expenseCal.get(Calendar.MONTH) == currentMonth && expenseCal.get(Calendar.YEAR) == currentYear
                }

                val lastMonthExpenses = filteredExpenses.filter { expense ->
                    expenseCal.timeInMillis = expense.date
                    expenseCal.get(Calendar.MONTH) == lastMonth && expenseCal.get(Calendar.YEAR) == lastMonthYear
                }

                val monthTotal = thisMonthExpenses.sumOf { it.amount }
                val defaultColors = listOf(
                    0xFF4CAF50, 0xFF2196F3, 0xFFE91E63, 0xFFFF9800,
                    0xFF9C27B0, 0xFFF44336, 0xFF3F51B5, 0xFFFF5722,
                    0xFF009688, 0xFF607D8B
                )
                var colorIndex = 0

                val breakdown = thisMonthExpenses
                    .groupBy { it.categoryName }
                    .map { (name, expenses) ->
                        val total = expenses.sumOf { it.amount }
                        val color = categoryColorMap[name] ?: defaultColors[colorIndex++ % defaultColors.size]
                        CategoryBreakdown(
                            categoryName = name,
                            amount = total,
                            percentage = if (monthTotal > 0) (total / monthTotal).toFloat() else 0f,
                            color = color
                        )
                    }
                    .sortedByDescending { it.amount }

                _uiState.update {
                    it.copy(
                        monthTotal = monthTotal,
                        lastMonthTotal = lastMonthExpenses.sumOf { e -> e.amount },
                        recentExpenses = filteredExpenses.sortedByDescending { e -> e.date }.take(10),
                        categoryBreakdown = breakdown,
                        personFilter = filter,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setPersonFilter(userId: String?) {
        _personFilter.value = userId
    }

    override fun onCleared() {
        super.onCleared()
        expenseRepository.stopRealtimeSync()
        categoryRepository.stopRealtimeSync()
    }
}
