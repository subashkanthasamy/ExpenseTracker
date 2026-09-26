package com.bose.expensetracker.domain.repository

import com.bose.expensetracker.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun signInWithEmail(email: String, password: String): Result<User>
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User>
    suspend fun signInWithGoogle(idToken: String): Result<User>

    /**
     * Sends a password-reset email, and reports success even when no account exists.
     *
     * Firebase raises `auth/user-not-found` for an unknown address, which turns this into an
     * oracle for "is this person registered?". The addresses in a family household are
     * guessable, so every client swallows that one error and shows the same neutral
     * confirmation either way — "if an account exists, a link is on its way".
     *
     * A malformed address is still reported: that is a typo the user can fix, not a
     * disclosure. Newer Firebase projects enable email-enumeration protection server-side,
     * which returns success anyway; this keeps the behaviour identical whether it is on or off.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit>
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
