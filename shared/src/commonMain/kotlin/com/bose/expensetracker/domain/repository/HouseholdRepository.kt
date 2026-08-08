package com.bose.expensetracker.domain.repository

import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.User

interface HouseholdRepository {
    suspend fun createHousehold(name: String, userId: String): Result<Household>
    suspend fun joinHousehold(inviteCode: String, userId: String): Result<Household>
    suspend fun getHousehold(householdId: String): Result<Household>
    suspend fun getHouseholdMembers(householdId: String): Result<List<User>>
    suspend fun getUserHouseholdId(userId: String): String?
    suspend fun getUserHouseholds(userId: String): Result<List<Household>>
    suspend fun setActiveHousehold(userId: String, householdId: String): Result<Unit>
    /**
     * Sets [userId]'s role to `member` or `guest`.
     *
     * Owner-only; the security rules reject it from anyone else. The owner has no entry in
     * `roles` — they are identified by `ownerUid` — so this cannot be used to demote them.
     */
    suspend fun updateMemberRole(householdId: String, userId: String, role: String): Result<Unit>

    /**
     * Removes [userId] from the household.
     *
     * Drops them from `memberUids` and `roles` together; the rules reject an update that
     * leaves the two disagreeing. Note this cannot clear the household from the removed
     * user's own profile — only they can write that document — so their client has to notice
     * it lost access.
     */
    suspend fun removeMember(householdId: String, userId: String): Result<Unit>

    suspend fun deleteHousehold(householdId: String, userId: String): Result<Unit>
}
