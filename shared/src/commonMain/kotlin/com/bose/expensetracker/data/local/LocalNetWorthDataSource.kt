package com.bose.expensetracker.data.local

import com.bose.expensetracker.domain.model.Asset
import com.bose.expensetracker.domain.model.Liability
import kotlinx.coroutines.flow.Flow

interface LocalNetWorthDataSource {
    fun getAssets(householdId: String): Flow<List<Asset>>
    fun getLiabilities(householdId: String): Flow<List<Liability>>
    suspend fun getAssetById(id: String): Asset?
    suspend fun getLiabilityById(id: String): Liability?
    suspend fun insertAsset(asset: Asset)
    suspend fun updateAsset(asset: Asset)
    suspend fun deleteAsset(id: String)
    suspend fun insertLiability(liability: Liability)
    suspend fun updateLiability(liability: Liability)
    suspend fun deleteLiability(id: String)
    suspend fun getPendingAssets(): List<Asset>
    suspend fun getPendingLiabilities(): List<Liability>
    suspend fun markAssetSynced(id: String)
    suspend fun markLiabilitySynced(id: String)
}
