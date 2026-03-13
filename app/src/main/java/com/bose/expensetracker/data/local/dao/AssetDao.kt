package com.bose.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bose.expensetracker.data.local.entity.AssetEntity
import com.bose.expensetracker.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    @Query("SELECT * FROM assets WHERE householdId = :householdId AND syncStatus != ${SyncStatus.PENDING_DELETE} ORDER BY date DESC")
    fun getAllAssets(householdId: String): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getAssetById(id: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE syncStatus != ${SyncStatus.SYNCED}")
    suspend fun getPendingSyncAssets(): List<AssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: AssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assets: List<AssetEntity>)

    @Update
    suspend fun update(asset: AssetEntity)

    @Query("UPDATE assets SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: Int)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteById(id: String)
}
