package com.bose.expensetracker.domain.repository

import android.app.Activity
import com.bose.expensetracker.domain.model.User
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun signInWithEmail(email: String, password: String): Result<User>
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User>
    suspend fun signInWithGoogle(idToken: String): Result<User>
    fun sendPhoneVerificationCode(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    )
    fun resendPhoneVerificationCode(
        phoneNumber: String,
        activity: Activity,
        token: PhoneAuthProvider.ForceResendingToken,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    )
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Result<User>
    suspend fun signOut()
    fun getCurrentUserId(): String?
    fun getCurrentUserDisplayName(): String?
}
