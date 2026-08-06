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
    suspend fun deleteHousehold(householdId: String, userId: String): Result<Unit>
}
