package com.bose.expensetracker.ui.screen.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.User
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HouseholdUiState(
    val household: Household? = null,
    val households: List<Household> = emptyList(),
    val members: List<User> = emptyList(),
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HouseholdUiState())
    val uiState: StateFlow<HouseholdUiState> = _uiState.asStateFlow()

    private val _householdSwitchedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val householdSwitchedEvent: SharedFlow<Unit> = _householdSwitchedEvent.asSharedFlow()

    init {
        loadHouseholdData()
    }

    private fun loadHouseholdData() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val hId = householdRepository.getUserHouseholdId(uid)
            if (hId == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            householdRepository.getUserHouseholds(uid).onSuccess { allHouseholds ->
                _uiState.update { it.copy(households = allHouseholds) }
            }

            householdRepository.getHousehold(hId).onSuccess { household ->
                _uiState.update { it.copy(household = household) }
            }

            householdRepository.getHouseholdMembers(hId).onSuccess { members ->
                _uiState.update { it.copy(members = members, isLoading = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun switchHousehold(householdId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val uid = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(errorMessage = "Not signed in") }
                return@launch
            }
            householdRepository.setActiveHousehold(uid, householdId).onSuccess {
                _householdSwitchedEvent.emit(Unit)
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = "Switch failed: ${error.message}") }
            }
        }
    }

    fun createNewHousehold(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val uid = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(errorMessage = "Not signed in") }
                return@launch
            }
            householdRepository.createHousehold(name, uid).onSuccess { household ->
                categoryRepository.seedPresetCategories(household.id)
                _householdSwitchedEvent.emit(Unit)
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = "Create failed: ${error.message}") }
            }
        }
    }

    fun joinNewHousehold(inviteCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val uid = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(errorMessage = "Not signed in") }
                return@launch
            }
            householdRepository.joinHousehold(inviteCode, uid).onSuccess {
                _householdSwitchedEvent.emit(Unit)
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = "Join failed: ${error.message}") }
            }
        }
    }

    fun deleteHousehold(householdId: String) {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(errorMessage = "Not signed in") }
                return@launch
            }

            val householdCount = _uiState.value.households.size
            android.util.Log.d("HouseholdVM", "deleteHousehold: id=$householdId, uid=$uid, householdCount=$householdCount")

            if (householdCount <= 1) {
                _uiState.update { it.copy(errorMessage = "Cannot delete your only household. Create or join another one first.") }
                return@launch
            }

            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            householdRepository.deleteHousehold(householdId, uid).onSuccess {
                android.util.Log.d("HouseholdVM", "deleteHousehold success, navigating")
                _householdSwitchedEvent.emit(Unit)
            }.onFailure { error ->
                android.util.Log.e("HouseholdVM", "deleteHousehold failed: ${error.message}", error)
                _uiState.update { it.copy(isDeleting = false, errorMessage = "Delete failed: ${error.message}") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
