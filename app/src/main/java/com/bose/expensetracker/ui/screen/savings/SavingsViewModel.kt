package com.bose.expensetracker.ui.screen.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.SavingsGoal
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SavingsUiState(
    val goals: List<SavingsGoal> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SavingsViewModel @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsUiState())
    val uiState: StateFlow<SavingsUiState> = _uiState.asStateFlow()

    private var householdId: String? = null

    init { loadData() }

    private fun loadData() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            val hId = householdRepository.getUserHouseholdId(uid) ?: return@launch
            householdId = hId

            try {
                firestoreDataSource.observeSavingsGoals(hId).collect { goals ->
                    _uiState.update { it.copy(goals = goals, isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun addGoal(name: String, targetAmount: Double, icon: String, targetDate: Long?) {
        val hId = householdId ?: return
        viewModelScope.launch {
            firestoreDataSource.upsertSavingsGoal(
                SavingsGoal(
                    id = UUID.randomUUID().toString(),
                    householdId = hId,
                    name = name,
                    targetAmount = targetAmount,
                    icon = icon,
                    targetDate = targetDate,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun addContribution(goalId: String, amount: Double) {
        viewModelScope.launch {
            // Firestore has no in-place increment through this data source, so apply the
            // delta to the goal we already have in state.
            val goal = _uiState.value.goals.firstOrNull { it.id == goalId } ?: return@launch
            firestoreDataSource.upsertSavingsGoal(
                goal.copy(currentAmount = goal.currentAmount + amount)
            )
        }
    }

    fun deleteGoal(id: String) {
        val hId = householdId ?: return
        viewModelScope.launch {
            firestoreDataSource.deleteSavingsGoal(hId, id)
        }
    }
}
