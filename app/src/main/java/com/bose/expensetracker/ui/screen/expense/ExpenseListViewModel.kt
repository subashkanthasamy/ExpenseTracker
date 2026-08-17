package com.bose.expensetracker.ui.screen.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.data.access.SessionRoleProvider
import com.bose.expensetracker.domain.model.PaymentMethod
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.bose.expensetracker.domain.usecase.access.HouseholdRole
import com.bose.expensetracker.domain.usecase.filter.ExpenseFilter
import com.bose.expensetracker.ui.state.DateRangeFilter
import com.bose.expensetracker.ui.state.ExpenseFilterCriteria
import com.bose.expensetracker.ui.state.ExpenseListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Caller identity and role, combined into the list state. */
private data class ExpenseAccess(
    val uid: String = "",
    val role: HouseholdRole = HouseholdRole.NONE
)

@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val sessionRoleProvider: SessionRoleProvider,
    private val expenseRepository: ExpenseRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseListUiState())
    val uiState: StateFlow<ExpenseListUiState> = _uiState.asStateFlow()

    /**
     * The only home for filter state. It used to live both here (as three separate flows) and
     * as mirror fields on the UiState that nothing read, so a filter could be "set" without
     * ever reaching the list.
     */
    private val _criteria = MutableStateFlow(ExpenseFilterCriteria())

    private val _access = MutableStateFlow(ExpenseAccess())

    private var householdId: String? = null
    private var collectJob: Job? = null

    init {
        loadExpenses()
        loadAccess()
    }

    /**
     * Who the caller is and what they may change. Separate from the expense load so a role
     * lookup failure hides controls rather than emptying the list.
     */
    private fun loadAccess() {
        viewModelScope.launch {
            _access.value = ExpenseAccess(
                uid = sessionRoleProvider.currentUid(),
                role = sessionRoleProvider.currentRole()
            )
        }
    }

    /**
     * Public and re-callable: if the user or household can't be resolved we return before the
     * `combine` below exists, and until it does every filter write goes to a flow with no
     * subscriber. The screen can retry instead of being stuck with dead filters.
     */
    fun loadExpenses() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
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
                _criteria,
                _access
            ) { expenses, criteria, access ->
                // Options come from the unfiltered list so chips don't disappear as you use them.
                ExpenseListUiState(
                    expenses = ExpenseFilter.apply(
                        expenses = expenses,
                        criteria = criteria,
                        nowMillis = System.currentTimeMillis()
                    ),
                    totalCount = expenses.size,
                    categoryOptions = ExpenseFilter.categoryOptions(expenses),
                    personOptions = ExpenseFilter.personOptions(expenses),
                    criteria = criteria,
                    currentUid = access.uid,
                    role = access.role,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setSearchQuery(query: String) = _criteria.update { it.copy(searchQuery = query) }

    fun setPersonFilter(userId: String?) = _criteria.update { it.copy(personFilter = userId) }

    fun setCategoryFilter(categoryId: String?) =
        _criteria.update { it.copy(categoryFilter = categoryId) }

    fun setDateRange(range: DateRangeFilter) = _criteria.update { it.copy(dateRange = range) }

    fun setPaymentMethodFilter(method: PaymentMethod?) =
        _criteria.update { it.copy(paymentMethodFilter = method) }

    fun clearFilters() {
        _criteria.value = ExpenseFilterCriteria()
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expenseId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        expenseRepository.stopRealtimeSync()
    }
}
