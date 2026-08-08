package com.bose.expensetracker.domain.repository

import com.bose.expensetracker.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun signInWithEmail(email: String, password: String): Result<User>
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User>
    suspend fun signInWithGoogle(idToken: String): Result<User>
    suspend fun signOut()
    fun getCurrentUserId(): String?
    fun getCurrentUserDisplayName(): String?

    /**
     * Whether this account carries the global `admin` custom claim.
     *
     * Read from the ID token, never from a document the account could write — a role field on
     * `users/{uid}` would be self-grantable, since the rules let a user write their own
     * profile. Claims are set server-side with the Admin SDK and reach the client on the next
     * token refresh.
     *
     * Advisory only: this decides which buttons appear. `firestore.rules` re-checks the same
     * claim and is what actually stops an admin action.
     */
    suspend fun isAdmin(): Boolean
}
