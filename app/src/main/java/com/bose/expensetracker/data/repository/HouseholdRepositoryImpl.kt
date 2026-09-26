package com.bose.expensetracker.data.repository

import com.bose.expensetracker.data.local.dao.AssetDao
import com.bose.expensetracker.data.local.dao.CategoryDao
import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.local.dao.LiabilityDao
import com.bose.expensetracker.data.preferences.SandboxConstants
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.User
import com.bose.expensetracker.domain.repository.HouseholdRepository
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseholdRepositoryImpl @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val assetDao: AssetDao,
    private val liabilityDao: LiabilityDao
) : HouseholdRepository {

    override suspend fun createHousehold(name: String, userId: String): Result<Household> =
        runCatching {
            val household = Household(
                id = UUID.randomUUID().toString(),
                name = name,
                memberUids = listOf(userId),
                ownerUid = userId,
                inviteCode = generateInviteCode(),
                createdAt = System.currentTimeMillis()
            )
            firestoreDataSource.createHousehold(household)
            firestoreDataSource.updateUserHouseholdId(userId, household.id)
            household
        }

    override suspend fun joinHousehold(inviteCode: String, userId: String): Result<Household> =
        runCatching {
            val target = firestoreDataSource.resolveInviteCode(inviteCode)
                ?: throw Exception("That invite code doesn't match a household. Check it and try again.")
            // arrayUnion, so joining needs no read access to the household — which the
            // security rules no longer grant to non-members.
            firestoreDataSource.addMemberToHousehold(target.householdId, userId)
            firestoreDataSource.updateUserHouseholdId(userId, target.householdId)
            // Readable now that we are a member.
            firestoreDataSource.getHousehold(target.householdId)
                ?: Household(
                    id = target.householdId,
                    name = target.householdName,
                    memberUids = listOf(userId),
                    inviteCode = inviteCode,
                    createdAt = System.currentTimeMillis()
                )
        }

    override suspend fun getHousehold(householdId: String): Result<Household> =
        runCatching {
            if (householdId == SandboxConstants.SANDBOX_HOUSEHOLD_ID) {
                return@runCatching Household(
                    id = SandboxConstants.SANDBOX_HOUSEHOLD_ID,
                    name = "Demo Household",
                    memberUids = listOf(SandboxConstants.SANDBOX_USER_ID),
                    inviteCode = "DEMO00",
                    createdAt = System.currentTimeMillis()
                )
            }
            firestoreDataSource.getHousehold(householdId)
                ?: throw Exception("This household no longer exists.")
        }

    override suspend fun getHouseholdMembers(householdId: String): Result<List<User>> =
        runCatching {
            if (householdId == SandboxConstants.SANDBOX_HOUSEHOLD_ID) {
                return@runCatching listOf(
                    User(
                        uid = SandboxConstants.SANDBOX_USER_ID,
                        email = "demo@sandbox.local",
                        displayName = SandboxConstants.SANDBOX_DISPLAY_NAME,
                        householdIds = listOf(SandboxConstants.SANDBOX_HOUSEHOLD_ID),
                        activeHouseholdId = SandboxConstants.SANDBOX_HOUSEHOLD_ID
                    )
                )
            }
            val household = firestoreDataSource.getHousehold(householdId)
                ?: throw Exception("This household no longer exists.")
            household.memberUids.mapNotNull { uid ->
                firestoreDataSource.getUser(uid)
            }
        }

    override suspend fun getUserHouseholdId(userId: String): String? {
        if (userId == SandboxConstants.SANDBOX_USER_ID) return SandboxConstants.SANDBOX_HOUSEHOLD_ID
        return firestoreDataSource.getUserFromServer(userId)?.activeHouseholdId
            ?: firestoreDataSource.getUser(userId)?.activeHouseholdId
    }

    override suspend fun getUserHouseholds(userId: String): Result<List<Household>> =
        runCatching {
            val user = firestoreDataSource.getUserFromServer(userId)
                ?: throw Exception("Couldn't find your account. Sign out, then sign in again.")
            user.householdIds.mapNotNull { hId ->
                firestoreDataSource.getHousehold(hId)
            }
        }

    override suspend fun setActiveHousehold(userId: String, householdId: String): Result<Unit> =
        runCatching {
            val user = firestoreDataSource.getUserFromServer(userId)
                ?: throw Exception("Couldn't find your account. Sign out, then sign in again.")
            if (householdId !in user.householdIds) {
                throw Exception("You're not a member of this household.")
            }
            firestoreDataSource.setActiveHouseholdId(userId, householdId)
        }

    override suspend fun updateMemberRole(
        householdId: String,
        userId: String,
        role: String
    ): Result<Unit> = runCatching {
        firestoreDataSource.updateMemberRole(householdId, userId, role)
    }

    override suspend fun removeMember(householdId: String, userId: String): Result<Unit> =
        runCatching {
            firestoreDataSource.removeMemberFromHousehold(householdId, userId)
        }

    override suspend fun deleteHousehold(householdId: String, userId: String): Result<Unit> =
        try {
            android.util.Log.d("HouseholdRepo", "deleteHousehold: id=$householdId, userId=$userId")
            val household = firestoreDataSource.getHousehold(householdId)
                ?: throw Exception("This household no longer exists.")

            // 1. Delete every sub-collection, then the invite code, then the household — in
            // that order. The rules for sub-collections read the household document, so once
            // it is gone nothing under it can be deleted. Any failure stops here and leaves
            // the household in place, so the owner can retry rather than orphan data.
            android.util.Log.d("HouseholdRepo", "Deleting sub-collections...")
            firestoreDataSource.deleteAllExpenses(householdId)
            firestoreDataSource.deleteAllCategories(householdId)
            firestoreDataSource.deleteAllBudgets(householdId)
            firestoreDataSource.deleteAllSavingsGoals(householdId)
            firestoreDataSource.deleteAllRecurring(householdId)
            firestoreDataSource.deleteAllAssets(householdId)
            firestoreDataSource.deleteAllLiabilities(householdId)
            firestoreDataSource.deleteInviteCodeIfOwned(household.inviteCode, householdId)

            android.util.Log.d("HouseholdRepo", "Deleting household document...")
            firestoreDataSource.deleteHousehold(householdId)

            // 2. Remove the household from members' user docs. After the delete rather than
            // before, so a failed delete leaves it in the owner's list to retry. The rules
            // only let a user write their own user doc, so other members are expected to
            // fail here; their clients drop the missing household on next read.
            android.util.Log.d("HouseholdRepo", "Removing from ${household.memberUids.size} members...")
            for (memberUid in household.memberUids) {
                try {
                    firestoreDataSource.removeHouseholdFromUser(memberUid, householdId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("HouseholdRepo", "Failed to remove from user $memberUid: ${e.message}")
                }
            }

            // 3. Delete local Room data
            android.util.Log.d("HouseholdRepo", "Deleting local Room data...")
            expenseDao.deleteAllForHousehold(householdId)
            categoryDao.deleteAllForHousehold(householdId)
            assetDao.deleteAllForHousehold(householdId)
            liabilityDao.deleteAllForHousehold(householdId)
            android.util.Log.d("HouseholdRepo", "deleteHousehold complete")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("HouseholdRepo", "deleteHousehold failed", e)
            Result.failure(e)
        }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
