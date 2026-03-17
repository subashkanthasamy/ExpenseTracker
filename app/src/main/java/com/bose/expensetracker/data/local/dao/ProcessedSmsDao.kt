package com.bose.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bose.expensetracker.data.local.entity.ProcessedSmsEntity

@Dao
interface ProcessedSmsDao {

    @Query("SELECT EXISTS(SELECT 1 FROM processed_sms WHERE smsHash = :hash)")
    suspend fun exists(hash: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ProcessedSmsEntity)

    @Query("DELETE FROM processed_sms WHERE processedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
