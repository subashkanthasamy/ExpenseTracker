package com.bose.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bose.expensetracker.data.local.entity.ProcessedSmsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessedSmsDao {

    @Query("SELECT EXISTS(SELECT 1 FROM processed_sms WHERE smsHash = :hash)")
    suspend fun exists(hash: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ProcessedSmsEntity)

    @Query("DELETE FROM processed_sms WHERE processedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("SELECT * FROM processed_sms ORDER BY processedAt DESC")
    fun getAll(): Flow<List<ProcessedSmsEntity>>

    @Query("SELECT COUNT(*) FROM processed_sms")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM processed_sms WHERE processedAt >= :since")
    suspend fun getCountSince(since: Long): Int
}
