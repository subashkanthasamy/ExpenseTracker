package com.bose.expensetracker.ui.screen.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.data.local.dao.SavingsGoalDao
import com.bose.expensetracker.data.local.entity.SavingsGoalEntity
import com.bose.expensetracker.domain.model.SavingsGoal
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val savingsGoalDao: SavingsGoalDao,
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

            try { savingsGoalDao.getGoals(hId).collect { entities ->
                val goals = entities.map { e ->
                    SavingsGoal(
                        id = e.id,
                        householdId = e.householdId,
                        name = e.name,
                        targetAmount = e.targetAmount,
                        currentAmount = e.currentAmount,
                        icon = e.icon,
                        targetDate = e.targetDate,
                        createdAt = e.createdAt
                    )
                }
                _uiState.update { it.copy(goals = goals, isLoading = false) }
            }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun addGoal(name: String, targetAmount: Double, icon: String, targetDate: Long?) {
        val hId = householdId ?: return
        viewModelScope.launch {
            savingsGoalDao.insert(
                SavingsGoalEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = hId,
                    name = name,
                    targetAmount = targetAmount,
                    icon = icon,
                    targetDate = targetDate
                )
            )
        }
    }

    fun addContribution(goalId: String, amount: Double) {
        viewModelScope.launch {
            savingsGoalDao.addContribution(goalId, amount)
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch {
            savingsGoalDao.deleteById(id)
        }
    }
}
