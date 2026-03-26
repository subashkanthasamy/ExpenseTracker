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
                inviteCode = generateInviteCode(),
                createdAt = System.currentTimeMillis()
            )
            firestoreDataSource.createHousehold(household)
            firestoreDataSource.updateUserHouseholdId(userId, household.id)
            household
        }

    override suspend fun joinHousehold(inviteCode: String, userId: String): Result<Household> =
        runCatching {
            val household = firestoreDataSource.getHouseholdByInviteCode(inviteCode)
                ?: throw Exception("No household found with this invite code")
            firestoreDataSource.addMemberToHousehold(household.id, userId)
            firestoreDataSource.updateUserHouseholdId(userId, household.id)
            household.copy(memberUids = household.memberUids + userId)
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
                ?: throw Exception("Household not found")
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
                ?: throw Exception("Household not found")
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
                ?: throw Exception("User not found")
            user.householdIds.mapNotNull { hId ->
                firestoreDataSource.getHousehold(hId)
            }
        }

    override suspend fun setActiveHousehold(userId: String, householdId: String): Result<Unit> =
        runCatching {
            val user = firestoreDataSource.getUserFromServer(userId)
                ?: throw Exception("User not found")
            if (householdId !in user.householdIds) {
                throw Exception("You are not a member of this household")
            }
            firestoreDataSource.setActiveHouseholdId(userId, householdId)
        }

    override suspend fun deleteHousehold(householdId: String, userId: String): Result<Unit> =
        runCatching {
            android.util.Log.d("HouseholdRepo", "deleteHousehold: id=$householdId, userId=$userId")
            val household = firestoreDataSource.getHousehold(householdId)
                ?: throw Exception("Household not found")

            // 1. Remove household from all members FIRST (this updates user docs, which we have permission for)
            android.util.Log.d("HouseholdRepo", "Removing from ${household.memberUids.size} members...")
            for (memberUid in household.memberUids) {
                try {
                    firestoreDataSource.removeHouseholdFromUser(memberUid, householdId)
                } catch (e: Exception) {
                    android.util.Log.w("HouseholdRepo", "Failed to remove from user $memberUid: ${e.message}")
                }
            }

            // 2. Delete all Firestore sub-collections
            android.util.Log.d("HouseholdRepo", "Deleting sub-collections...")
            try {
                firestoreDataSource.deleteAllExpenses(householdId)
                firestoreDataSource.deleteAllCategories(householdId)
                firestoreDataSource.deleteAllAssets(householdId)
                firestoreDataSource.deleteAllLiabilities(householdId)
            } catch (e: Exception) {
                android.util.Log.w("HouseholdRepo", "Sub-collection delete error (continuing): ${e.message}")
            }

            // 3. Delete the household document (may fail due to Firestore rules)
            android.util.Log.d("HouseholdRepo", "Deleting household document...")
            try {
                firestoreDataSource.deleteHousehold(householdId)
            } catch (e: Exception) {
                android.util.Log.w("HouseholdRepo", "Household doc delete failed (continuing): ${e.message}")
                // Continue anyway — the user is already removed from this household
            }

            // 4. Delete local Room data
            android.util.Log.d("HouseholdRepo", "Deleting local Room data...")
            expenseDao.deleteAllForHousehold(householdId)
            categoryDao.deleteAllForHousehold(householdId)
            assetDao.deleteAllForHousehold(householdId)
            liabilityDao.deleteAllForHousehold(householdId)
            android.util.Log.d("HouseholdRepo", "deleteHousehold complete")
        }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
