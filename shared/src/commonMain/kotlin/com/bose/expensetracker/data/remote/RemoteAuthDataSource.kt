package com.bose.expensetracker.data.remote

import com.bose.expensetracker.domain.model.User
import kotlinx.coroutines.flow.Flow

interface RemoteAuthDataSource {
    val currentUser: Flow<User?>
    suspend fun signInWithEmail(email: String, password: String): Result<User>
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User>
    suspend fun signInWithGoogle(idToken: String): Result<User>
    suspend fun signOut()
    fun getCurrentUserId(): String?
    fun getCurrentUserDisplayName(): String?
}
