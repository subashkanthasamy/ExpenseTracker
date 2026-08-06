package com.bose.expensetracker.data.remote

import com.bose.expensetracker.domain.model.Asset
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.Liability
import com.bose.expensetracker.domain.model.User
import kotlinx.coroutines.flow.Flow

interface RemoteDataSource {
    // User
    suspend fun getUser(uid: String): User?
    suspend fun saveUser(user: User)

    // Household
    suspend fun createHousehold(household: Household)
    suspend fun getHousehold(householdId: String): Household?
    suspend fun getHouseholdByInviteCode(inviteCode: String): Household?
    suspend fun updateHouseholdMembers(householdId: String, memberUids: List<String>)
    suspend fun deleteHousehold(householdId: String)
    suspend fun getUserHouseholds(userId: String): List<Household>

    // Expenses
    suspend fun getExpenses(householdId: String): List<Expense>
    suspend fun addExpense(householdId: String, expense: Expense)
    suspend fun updateExpense(householdId: String, expense: Expense)
    suspend fun deleteExpense(householdId: String, expenseId: String)
    suspend fun deleteAllExpenses(householdId: String)
    fun observeExpenses(householdId: String): Flow<List<Expense>>

    // Categories
    suspend fun getCategories(householdId: String): List<Category>
    suspend fun addCategory(householdId: String, category: Category)
    suspend fun updateCategory(householdId: String, category: Category)
    suspend fun deleteCategory(householdId: String, categoryId: String)
    fun observeCategories(householdId: String): Flow<List<Category>>

    // Assets
    suspend fun getAssets(householdId: String): List<Asset>
    suspend fun addAsset(householdId: String, asset: Asset)
    suspend fun updateAsset(householdId: String, asset: Asset)
    suspend fun deleteAsset(householdId: String, assetId: String)
    fun observeAssets(householdId: String): Flow<List<Asset>>

    // Liabilities
    suspend fun getLiabilities(householdId: String): List<Liability>
    suspend fun addLiability(householdId: String, liability: Liability)
    suspend fun updateLiability(householdId: String, liability: Liability)
    suspend fun deleteLiability(householdId: String, liabilityId: String)
    fun observeLiabilities(householdId: String): Flow<List<Liability>>
}
