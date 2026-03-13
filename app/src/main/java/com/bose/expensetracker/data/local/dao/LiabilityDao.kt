package com.bose.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bose.expensetracker.data.local.entity.LiabilityEntity
import com.bose.expensetracker.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface LiabilityDao {

    @Query("SELECT * FROM liabilities WHERE householdId = :householdId AND syncStatus != ${SyncStatus.PENDING_DELETE} ORDER BY date DESC")
    fun getAllLiabilities(householdId: String): Flow<List<LiabilityEntity>>

    @Query("SELECT * FROM liabilities WHERE id = :id")
    suspend fun getLiabilityById(id: String): LiabilityEntity?

    @Query("SELECT * FROM liabilities WHERE syncStatus != ${SyncStatus.SYNCED}")
    suspend fun getPendingSyncLiabilities(): List<LiabilityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(liability: LiabilityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(liabilities: List<LiabilityEntity>)

    @Update
    suspend fun update(liability: LiabilityEntity)

    @Query("UPDATE liabilities SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: Int)

    @Query("DELETE FROM liabilities WHERE id = :id")
    suspend fun deleteById(id: String)
}
