package com.bose.expensetracker.ui.screen.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.User
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
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

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val user: User? = null,
    val household: Household? = null,
    val phoneAuthState: PhoneAuthState = PhoneAuthState.Idle
)

sealed class PhoneAuthState {
    object Idle : PhoneAuthState()
    object CodeSent : PhoneAuthState()
    object AutoVerifying : PhoneAuthState()
}

sealed class AuthEvent {
    object NavigateToDashboard : AuthEvent()
    object NavigateToHouseholdSetup : AuthEvent()
    object NavigateToLogin : AuthEvent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signInWithEmail(email, password)
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false, user = user) }
                    checkHouseholdAndNavigate(user)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signUpWithEmail(email, password, displayName)
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false, user = user) }
                    _events.emit(AuthEvent.NavigateToHouseholdSetup)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signInWithGoogle(idToken)
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false, user = user) }
                    checkHouseholdAndNavigate(user)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun createHousehold(name: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            householdRepository.createHousehold(name, userId)
                .onSuccess { household ->
                    _uiState.update { it.copy(isLoading = false, household = household) }
                    categoryRepository.seedPresetCategories(household.id)
                    _events.emit(AuthEvent.NavigateToDashboard)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun joinHousehold(inviteCode: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            householdRepository.joinHousehold(inviteCode, userId)
                .onSuccess { household ->
                    _uiState.update { it.copy(isLoading = false, household = household) }
                    _events.emit(AuthEvent.NavigateToDashboard)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update { AuthUiState() }
            _events.emit(AuthEvent.NavigateToLogin)
        }
    }

    // Phone Auth
    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var currentPhoneNumber: String? = null

    private val phoneAuthCallbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-verification (e.g., instant verification or auto-retrieval)
            _uiState.update { it.copy(phoneAuthState = PhoneAuthState.AutoVerifying) }
            signInWithPhoneCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            _uiState.update { it.copy(isLoading = false, error = e.message, phoneAuthState = PhoneAuthState.Idle) }
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            storedVerificationId = verificationId
            resendToken = token
            _uiState.update { it.copy(isLoading = false, phoneAuthState = PhoneAuthState.CodeSent) }
        }
    }

    fun sendPhoneVerificationCode(phoneNumber: String, activity: Activity) {
        currentPhoneNumber = phoneNumber
        _uiState.update { it.copy(isLoading = true, error = null) }
        authRepository.sendPhoneVerificationCode(phoneNumber, activity, phoneAuthCallbacks)
    }

    fun resendVerificationCode(activity: Activity) {
        val phone = currentPhoneNumber ?: return
        val token = resendToken ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        authRepository.resendPhoneVerificationCode(phone, activity, token, phoneAuthCallbacks)
    }

    fun verifyPhoneCode(code: String) {
        val verificationId = storedVerificationId
        if (verificationId == null) {
            _uiState.update { it.copy(error = "Verification session expired. Please request a new code.") }
            return
        }
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithPhoneCredential(credential)
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signInWithPhoneCredential(credential)
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false, user = user, phoneAuthState = PhoneAuthState.Idle) }
                    storedVerificationId = null
                    resendToken = null
                    currentPhoneNumber = null
                    checkHouseholdAndNavigate(user)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message, phoneAuthState = PhoneAuthState.CodeSent) }
                }
        }
    }

    fun resetPhoneAuth() {
        storedVerificationId = null
        resendToken = null
        currentPhoneNumber = null
        _uiState.update { it.copy(phoneAuthState = PhoneAuthState.Idle, error = null) }
    }

    fun handleGoogleSignInError(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun checkHouseholdAndNavigate(user: User) {
        if (user.householdId != null) {
            _events.emit(AuthEvent.NavigateToDashboard)
        } else {
            _events.emit(AuthEvent.NavigateToHouseholdSetup)
        }
    }
}
