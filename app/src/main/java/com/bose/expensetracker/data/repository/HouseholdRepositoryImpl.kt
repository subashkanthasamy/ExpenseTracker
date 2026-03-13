package com.bose.expensetracker.data.repository

import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.User
import com.bose.expensetracker.domain.repository.HouseholdRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseholdRepositoryImpl @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource
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
        return firestoreDataSource.getUser(userId)?.householdId
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
