package com.bose.expensetracker.data.repository

import android.app.Activity
import com.bose.expensetracker.data.preferences.SandboxConstants
import com.bose.expensetracker.data.preferences.SandboxPreferences
import com.bose.expensetracker.data.remote.AuthDataSource
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.User
import com.bose.expensetracker.domain.repository.AuthRepository
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authDataSource: AuthDataSource,
    private val firestoreDataSource: FirestoreDataSource,
    private val sandboxPreferences: SandboxPreferences
) : AuthRepository {

    override val currentUser: Flow<User?> = authDataSource.currentUser.map { firebaseUser ->
        if (firebaseUser == null) return@map null
        val existing = firestoreDataSource.getUser(firebaseUser.uid)
        if (existing != null) {
            existing
        } else {
            // User doc missing — create it
            val user = User(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: "",
                householdIds = emptyList(),
                activeHouseholdId = null
            )
            try { firestoreDataSource.createUser(user) } catch (_: Exception) { }
            user
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<User> =
        runCatching {
            val firebaseUser = authDataSource.signInWithEmail(email, password)
            val existingUser = firestoreDataSource.getUser(firebaseUser.uid)
            if (existingUser != null) {
                existingUser
            } else {
                // User doc missing in Firestore — create it now
                val user = User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    displayName = firebaseUser.displayName ?: "",
                    householdIds = emptyList(),
                activeHouseholdId = null
                )
                firestoreDataSource.createUser(user)
                user
            }
        }

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User> =
        runCatching {
            val firebaseUser = authDataSource.signUpWithEmail(email, password, displayName)
            val user = User(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = displayName,
                householdIds = emptyList(),
                activeHouseholdId = null
            )
            firestoreDataSource.createUser(user)
            user
        }

    override suspend fun signInWithGoogle(idToken: String): Result<User> =
        runCatching {
            val firebaseUser = authDataSource.signInWithGoogle(idToken)
            val existingUser = firestoreDataSource.getUser(firebaseUser.uid)
            if (existingUser != null) {
                existingUser
            } else {
                val user = User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    displayName = firebaseUser.displayName ?: "",
                    householdIds = emptyList(),
                activeHouseholdId = null
                )
                firestoreDataSource.createUser(user)
                user
            }
        }

    override fun sendPhoneVerificationCode(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        authDataSource.sendPhoneVerificationCode(phoneNumber, activity, callbacks)
    }

    override fun resendPhoneVerificationCode(
        phoneNumber: String,
        activity: Activity,
        token: PhoneAuthProvider.ForceResendingToken,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        authDataSource.resendPhoneVerificationCode(phoneNumber, activity, token, callbacks)
    }

    override suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Result<User> =
        runCatching {
            val firebaseUser = authDataSource.signInWithPhoneCredential(credential)
            val existingUser = firestoreDataSource.getUser(firebaseUser.uid)
            if (existingUser != null) {
                existingUser
            } else {
                val user = User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    displayName = firebaseUser.displayName ?: firebaseUser.phoneNumber ?: "",
                    householdIds = emptyList(),
                activeHouseholdId = null
                )
                firestoreDataSource.createUser(user)
                user
            }
        }

    override suspend fun signOut() {
        authDataSource.signOut()
    }

    override fun getCurrentUserId(): String? {
        if (sandboxPreferences.isSandboxCached) return SandboxConstants.SANDBOX_USER_ID
        return authDataSource.getCurrentUser()?.uid
    }

    override fun getCurrentUserDisplayName(): String? {
        if (sandboxPreferences.isSandboxCached) return SandboxConstants.SANDBOX_DISPLAY_NAME
        val user = authDataSource.getCurrentUser() ?: return null
        return user.displayName ?: user.phoneNumber ?: user.email
    }
}
