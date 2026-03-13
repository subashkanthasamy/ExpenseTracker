package com.bose.expensetracker.domain.repository

import com.bose.expensetracker.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getCategories(householdId: String): Flow<List<Category>>
    suspend fun getCategoryById(id: String): Category?
    suspend fun addCategory(category: Category): Result<Unit>
    suspend fun updateCategory(category: Category): Result<Unit>
    suspend fun deleteCategory(id: String): Result<Unit>
    suspend fun seedPresetCategories(householdId: String)
    suspend fun syncPendingCategories()
    fun startRealtimeSync(householdId: String)
    fun stopRealtimeSync()
}
