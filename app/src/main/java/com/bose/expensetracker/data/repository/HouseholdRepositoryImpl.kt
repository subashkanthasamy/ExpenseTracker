package com.bose.expensetracker.data.repository

import com.bose.expensetracker.data.local.dao.AssetDao
import com.bose.expensetracker.data.local.dao.CategoryDao
import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.local.dao.LiabilityDao
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
            firestoreDataSource.getHousehold(householdId)
                ?: throw Exception("Household not found")
        }

    override suspend fun getHouseholdMembers(householdId: String): Result<List<User>> =
        runCatching {
            val household = firestoreDataSource.getHousehold(householdId)
                ?: throw Exception("Household not found")
            household.memberUids.mapNotNull { uid ->
                firestoreDataSource.getUser(uid)
            }
        }

    override suspend fun getUserHouseholdId(userId: String): String? {
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
            // Get household to find all members
            val household = firestoreDataSource.getHousehold(householdId)
                ?: throw Exception("Household not found")

            // Delete all Firestore sub-collections
            firestoreDataSource.deleteAllExpenses(householdId)
            firestoreDataSource.deleteAllCategories(householdId)
            firestoreDataSource.deleteAllAssets(householdId)
            firestoreDataSource.deleteAllLiabilities(householdId)

            // Delete the household document
            firestoreDataSource.deleteHousehold(householdId)

            // Remove household from all members
            for (memberUid in household.memberUids) {
                try {
                    firestoreDataSource.removeHouseholdFromUser(memberUid, householdId)
                } catch (_: Exception) {
                    // Continue even if a member update fails
                }
            }

            // Delete local Room data
            expenseDao.deleteAllForHousehold(householdId)
            categoryDao.deleteAllForHousehold(householdId)
            assetDao.deleteAllForHousehold(householdId)
            liabilityDao.deleteAllForHousehold(householdId)
        }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
