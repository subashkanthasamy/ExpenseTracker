package com.bose.expensetracker.ui.screen.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CategoryUiState(
    val presetCategories: List<Category> = emptyList(),
    val customCategories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    private var householdId: String? = null

    init {
        loadCategories()
    }

    private fun loadCategories() {
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

            // Seed preset categories BEFORE starting sync to avoid race condition
            val existing = categoryRepository.getCategories(hId).firstOrNull() ?: emptyList()
            if (existing.none { it.isPreset }) {
                categoryRepository.seedPresetCategories(hId)
            }

            categoryRepository.startRealtimeSync(hId)

            categoryRepository.getCategories(hId).collect { categories ->
                _uiState.update {
                    it.copy(
                        presetCategories = categories.filter { c -> c.isPreset },
                        customCategories = categories.filter { c -> !c.isPreset },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun addCategory(name: String, icon: String, color: Long) {
        viewModelScope.launch {
            val hId = householdId ?: return@launch
            val category = Category(
                id = UUID.randomUUID().toString(),
                name = name,
                icon = icon,
                color = color,
                isPreset = false,
                householdId = hId
            )
            categoryRepository.addCategory(category)
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        categoryRepository.stopRealtimeSync()
    }
}
