package com.bose.expensetracker.ui.screen.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AddEditExpenseUiState(
    val amount: String = "",
    val selectedCategory: Category? = null,
    val categories: List<Category> = emptyList(),
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val addedByName: String = "",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddEditExpenseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    private val expenseId: String? = savedStateHandle["expenseId"]

    private val _uiState = MutableStateFlow(AddEditExpenseUiState())
    val uiState: StateFlow<AddEditExpenseUiState> = _uiState.asStateFlow()

    private val _saveComplete = MutableSharedFlow<Unit>()
    val saveComplete: SharedFlow<Unit> = _saveComplete.asSharedFlow()

    private var householdId: String? = null
    private var userId: String? = null
    private var userDisplayName: String? = null
    private var originalCreatedAt: Long? = null

    init {
        loadData()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadData() {
        viewModelScope.launch {
            userId = authRepository.getCurrentUserId()
            val uid = userId
            android.util.Log.d("AddEditExpenseVM", "loadData: userId=$uid")
            if (uid == null) {
                _uiState.update { it.copy(error = "Not signed in. Please restart the app.") }
                return@launch
            }

            // Try up to 3 times with delay — householdId may not be available immediately
            // after household creation (fire-and-forget Firestore write)
            var hId: String? = null
            for (attempt in 1..3) {
                hId = householdRepository.getUserHouseholdId(uid)
                android.util.Log.d("AddEditExpenseVM", "loadData: attempt=$attempt, householdId=$hId")
                if (hId != null) break
                if (attempt < 3) kotlinx.coroutines.delay(1000L)
            }
            householdId = hId
            if (hId == null) {
                _uiState.update { it.copy(error = "No household found. Please set up a household first.") }
                return@launch
            }

            // Get user display name from auth repository
            userDisplayName = authRepository.getCurrentUserDisplayName() ?: ""
            if (!userDisplayName.isNullOrBlank()) {
                _uiState.update { it.copy(addedByName = userDisplayName!!) }
            }

            // Seed preset categories BEFORE starting sync to avoid race condition
            val existing = categoryRepository.getCategories(hId).firstOrNull() ?: emptyList()
            if (existing.none { it.isPreset }) {
                categoryRepository.seedPresetCategories(hId)
            }

            // Start category realtime sync from Firestore
            categoryRepository.startRealtimeSync(hId)

            // Load existing expense if editing
            if (expenseId != null) {
                val expense = expenseRepository.getExpenseById(expenseId)
                if (expense != null) {
                    originalCreatedAt = expense.createdAt
                    _uiState.update {
                        it.copy(
                            amount = expense.amount.toString(),
                            date = expense.date,
                            notes = expense.notes,
                            addedByName = expense.addedByName,
                            isEditing = true
                        )
                    }
                }
            }

            // Collect categories as a live flow so new categories appear immediately
            android.util.Log.d("AddEditExpenseVM", "Starting category collection for householdId=$hId")
            categoryRepository.getCategories(hId).collect { categories ->
                android.util.Log.d("AddEditExpenseVM", "Categories received: ${categories.size} items: ${categories.map { it.name }}")
                _uiState.update { state ->
                    val selected = state.selectedCategory
                        ?: (if (expenseId != null) {
                            val expense = expenseRepository.getExpenseById(expenseId)
                            categories.find { c -> c.id == expense?.categoryId }
                        } else null)
                    state.copy(categories = categories, selectedCategory = selected)
                }
            }
        }
    }

    fun setAmount(amount: String) {
        _uiState.update { it.copy(amount = amount) }
    }

    fun setCategory(category: Category) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setDate(date: Long) {
        _uiState.update { it.copy(date = date) }
    }

    fun setNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun addCustomCategory(name: String) {
        viewModelScope.launch {
            val hId = householdId ?: return@launch
            val category = com.bose.expensetracker.domain.model.Category(
                id = UUID.randomUUID().toString(),
                name = name,
                icon = "category",
                color = 0xFF607D8B,
                isPreset = false,
                householdId = hId
            )
            categoryRepository.addCategory(category)
            // Auto-select the newly added category
            _uiState.update { it.copy(selectedCategory = category) }
        }
    }

    fun populateFromVoice(amount: Double?, categoryHint: String?, notes: String) {
        _uiState.update { state ->
            val category = if (categoryHint != null) {
                state.categories.find { it.name.contains(categoryHint, ignoreCase = true) }
            } else null
            state.copy(
                amount = amount?.toString() ?: state.amount,
                selectedCategory = category ?: state.selectedCategory,
                notes = notes
            )
        }
    }

    fun populateFromReceipt(amount: Double?, date: Long?) {
        _uiState.update {
            it.copy(
                amount = amount?.toString() ?: it.amount,
                date = date ?: it.date
            )
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val amount = state.amount.toDoubleOrNull()
            if (amount == null) {
                _uiState.update { it.copy(error = "Please enter a valid amount") }
                return@launch
            }
            val category = state.selectedCategory
            if (category == null) {
                _uiState.update { it.copy(error = "Please select a category") }
                return@launch
            }
            val uid = userId
            if (uid == null) {
                _uiState.update { it.copy(error = "Not signed in. Please restart the app.") }
                return@launch
            }
            val hId = householdId
            if (hId == null) {
                _uiState.update { it.copy(error = "No household found. Please set up a household first.") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }

            val now = System.currentTimeMillis()
            val expense = Expense(
                id = expenseId ?: UUID.randomUUID().toString(),
                householdId = hId,
                amount = amount,
                categoryId = category.id,
                categoryName = category.name,
                date = state.date,
                notes = state.notes,
                addedBy = uid,
                addedByName = state.addedByName.ifBlank { userDisplayName ?: "Unknown" },
                createdAt = if (state.isEditing) (originalCreatedAt ?: now) else now,
                updatedAt = now
            )

            val result = if (state.isEditing) {
                expenseRepository.updateExpense(expense)
            } else {
                expenseRepository.addExpense(expense)
            }

            result.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                _saveComplete.emit(Unit)
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }
}
