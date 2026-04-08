package com.bose.expensetracker.ui.state

import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.User

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val user: User? = null,
    val household: Household? = null,
    val phoneAuthState: PhoneAuthState = PhoneAuthState.Idle
)

sealed class PhoneAuthState {
    data object Idle : PhoneAuthState()
    data object CodeSent : PhoneAuthState()
    data object AutoVerifying : PhoneAuthState()
}

sealed class AuthEvent {
    data object NavigateToDashboard : AuthEvent()
    data object NavigateToHouseholdSetup : AuthEvent()
    data object NavigateToLogin : AuthEvent()
}
