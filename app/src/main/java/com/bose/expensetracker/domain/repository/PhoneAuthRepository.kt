package com.bose.expensetracker.domain.repository

import android.app.Activity
import com.bose.expensetracker.domain.model.User
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider

/**
 * Android-only companion to the shared [AuthRepository].
 *
 * Firebase phone auth needs an [Activity] and Firebase's [PhoneAuthProvider] callback
 * types, neither of which can be expressed in the shared module's commonMain, so these
 * three operations live here instead of on [AuthRepository].
 */
interface PhoneAuthRepository {
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
}
