package com.bose.expensetracker.data.access

import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.bose.expensetracker.domain.usecase.access.HouseholdRole
import com.bose.expensetracker.domain.usecase.access.Permissions
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The caller's role in their active household, for screens that need to know but have no
 * reason to load the household themselves.
 *
 * Cached because nearly every screen asks: resolving it fresh each time would add a household
 * read plus a token read to Budgets, Categories, Savings, Recurring and Net Worth on every
 * open. [invalidate] must be called when the active household changes or membership is
 * edited, otherwise a screen keeps answering with the previous household's role.
 *
 * Advisory only. `firestore.rules` is the enforcement — this decides which controls render.
 */
@Singleton
class SessionRoleProvider @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) {

    private data class Cached(val householdId: String, val uid: String, val role: HouseholdRole)

    @Volatile
    private var cached: Cached? = null

    /** Uid of the signed-in account, or empty. Needed to decide "is this expense mine". */
    fun currentUid(): String = authRepository.getCurrentUserId().orEmpty()

    /**
     * Role in the active household. [HouseholdRole.NONE] when signed out, without a household,
     * or when the lookup fails — callers treat that as "show nothing destructive", which is the
     * safe direction to fail in.
     */
    suspend fun currentRole(): HouseholdRole {
        val uid = authRepository.getCurrentUserId() ?: return HouseholdRole.NONE
        val householdId = householdRepository.getUserHouseholdId(uid) ?: return HouseholdRole.NONE

        cached?.let { if (it.householdId == householdId && it.uid == uid) return it.role }

        val household = householdRepository.getHousehold(householdId).getOrNull()
            ?: return HouseholdRole.NONE

        val role = Permissions.roleOf(household, uid, isAdmin = authRepository.isAdmin())
        cached = Cached(householdId, uid, role)
        return role
    }

    /** Drop the cache — after switching household, joining, leaving or a role change. */
    fun invalidate() {
        cached = null
    }
}
