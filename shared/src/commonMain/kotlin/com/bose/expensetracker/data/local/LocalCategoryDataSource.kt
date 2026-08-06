package com.bose.expensetracker.data.local

import com.bose.expensetracker.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface LocalCategoryDataSource {
    fun getCategories(householdId: String): Flow<List<Category>>
    suspend fun getCategoryById(id: String): Category?
    suspend fun insert(category: Category)
    suspend fun update(category: Category)
    suspend fun delete(id: String)
    suspend fun deleteByHousehold(householdId: String)
    suspend fun getPendingSync(): List<Category>
    suspend fun markSynced(id: String)
}
