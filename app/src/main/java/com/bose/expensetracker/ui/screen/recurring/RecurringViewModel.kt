package com.bose.expensetracker.ui.screen.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.model.RecurringExpense
import com.bose.expensetracker.ui.state.RecurringUiState
import kotlinx.coroutines.CancellationException
import com.bose.expensetracker.domain.model.RecurringFrequency
import com.bose.expensetracker.domain.repository.AuthRepository
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

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecurringUiState())
    val uiState: StateFlow<RecurringUiState> = _uiState.asStateFlow()

    private var householdId: String? = null
    private var userId: String? = null
    private var userName: String? = null

    init { loadData() }

    private fun loadData() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            val hId = householdRepository.getUserHouseholdId(uid) ?: return@launch
            householdId = hId
            userId = uid
            userName = authRepository.getCurrentUserDisplayName() ?: "User"

            launch {
                try {
                    firestoreDataSource.observeRecurringExpenses(hId).collect { items ->
                        _uiState.update { it.copy(items = items, isLoading = false) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            launch {
                try {
                    categoryRepository.getCategories(hId).collect { cats ->
                        _uiState.update { it.copy(categories = cats) }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    fun addRecurring(
        category: Category,
        amount: Double,
        notes: String,
        frequency: RecurringFrequency,
        dayOfWeek: Int?,
        dayOfMonth: Int?,
        monthOfYear: Int?
    ) {
        val hId = householdId ?: return
        val uid = userId ?: return
        val uName = userName ?: return
        viewModelScope.launch {
            firestoreDataSource.upsertRecurringExpense(
                RecurringExpense(
                    id = UUID.randomUUID().toString(),
                    householdId = hId,
                    amount = amount,
                    categoryId = category.id,
                    categoryName = category.name,
                    notes = notes,
                    addedBy = uid,
                    addedByName = uName,
                    frequency = frequency,
                    dayOfWeek = dayOfWeek,
                    dayOfMonth = dayOfMonth,
                    monthOfYear = monthOfYear,
                    startDate = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleActive(item: RecurringExpense) {
        viewModelScope.launch {
            firestoreDataSource.upsertRecurringExpense(item.copy(isActive = !item.isActive))
        }
    }

    fun delete(id: String) {
        val hId = householdId ?: return
        viewModelScope.launch {
            firestoreDataSource.deleteRecurringExpense(hId, id)
        }
    }
}
