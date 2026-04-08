package com.bose.expensetracker.domain.repository

import com.bose.expensetracker.domain.model.Asset
import com.bose.expensetracker.domain.model.Liability
import kotlinx.coroutines.flow.Flow

interface NetWorthRepository {
    fun getAssets(householdId: String): Flow<List<Asset>>
    fun getLiabilities(householdId: String): Flow<List<Liability>>
    suspend fun getAssetById(id: String): Asset?
    suspend fun getLiabilityById(id: String): Liability?
    suspend fun addAsset(asset: Asset): Result<Unit>
    suspend fun updateAsset(asset: Asset): Result<Unit>
    suspend fun deleteAsset(id: String): Result<Unit>
    suspend fun addLiability(liability: Liability): Result<Unit>
    suspend fun updateLiability(liability: Liability): Result<Unit>
    suspend fun deleteLiability(id: String): Result<Unit>
    suspend fun syncPending()
    fun startRealtimeSync(householdId: String)
    fun stopRealtimeSync()
}
