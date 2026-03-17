package com.bose.expensetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bose.expensetracker.data.local.dao.AssetDao
import com.bose.expensetracker.data.local.dao.CategoryDao
import com.bose.expensetracker.data.local.dao.ExpenseDao
import com.bose.expensetracker.data.local.dao.LiabilityDao
import com.bose.expensetracker.data.local.dao.ProcessedSmsDao
import com.bose.expensetracker.data.local.entity.AssetEntity
import com.bose.expensetracker.data.local.entity.CategoryEntity
import com.bose.expensetracker.data.local.entity.ExpenseEntity
import com.bose.expensetracker.data.local.entity.LiabilityEntity
import com.bose.expensetracker.data.local.entity.ProcessedSmsEntity

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        AssetEntity::class,
        LiabilityEntity::class,
        ProcessedSmsEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class ExpenseTrackerDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun assetDao(): AssetDao
    abstract fun liabilityDao(): LiabilityDao
    abstract fun processedSmsDao(): ProcessedSmsDao
}
