package com.bose.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bose.expensetracker.data.local.entity.PendingSmsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSmsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingSmsEntity)

    @Query("SELECT * FROM pending_sms WHERE id = :id")
    suspend fun getById(id: String): PendingSmsEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM pending_sms WHERE smsHash = :hash)")
    suspend fun exists(hash: String): Boolean

    @Query("UPDATE pending_sms SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT * FROM pending_sms WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun getPending(): Flow<List<PendingSmsEntity>>

    @Query("SELECT COUNT(*) FROM pending_sms WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("DELETE FROM pending_sms WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pending_sms WHERE createdAt < :olderThan AND status != 'PENDING'")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("UPDATE pending_sms SET categoryId = :categoryId, categoryName = :categoryName WHERE id = :id")
    suspend fun updateCategory(id: String, categoryId: String, categoryName: String)
}
