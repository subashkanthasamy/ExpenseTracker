package com.bose.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bose.expensetracker.data.local.entity.CategoryEntity
import com.bose.expensetracker.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE householdId = :householdId AND syncStatus != ${SyncStatus.PENDING_DELETE} ORDER BY isPreset DESC, name ASC")
    fun getAllCategories(householdId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE householdId = :householdId AND syncStatus != ${SyncStatus.PENDING_DELETE}")
    suspend fun getAllCategoriesOnce(householdId: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE syncStatus != ${SyncStatus.SYNCED}")
    suspend fun getPendingSyncCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: Int)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM categories WHERE householdId = :householdId")
    suspend fun deleteAllForHousehold(householdId: String)
}
