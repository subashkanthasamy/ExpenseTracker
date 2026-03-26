package com.bose.expensetracker.data.repository

import com.bose.expensetracker.data.local.dao.AssetDao
import com.bose.expensetracker.data.local.dao.LiabilityDao
import com.bose.expensetracker.data.local.entity.SyncStatus
import com.bose.expensetracker.data.mapper.toDomain
import com.bose.expensetracker.data.mapper.toEntity
import com.bose.expensetracker.data.preferences.SandboxPreferences
import com.bose.expensetracker.data.remote.FirestoreDataSource
import com.bose.expensetracker.domain.model.Asset
import com.bose.expensetracker.domain.model.Liability
import com.bose.expensetracker.domain.repository.NetWorthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetWorthRepositoryImpl @Inject constructor(
    private val assetDao: AssetDao,
    private val liabilityDao: LiabilityDao,
    private val firestoreDataSource: FirestoreDataSource,
    private val sandboxPreferences: SandboxPreferences
) : NetWorthRepository {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var assetSyncJob: Job? = null
    private var liabilitySyncJob: Job? = null

    override fun getAssets(householdId: String): Flow<List<Asset>> =
        assetDao.getAllAssets(householdId).map { entities -> entities.map { it.toDomain() } }

    override fun getLiabilities(householdId: String): Flow<List<Liability>> =
        liabilityDao.getAllLiabilities(householdId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAssetById(id: String): Asset? = assetDao.getAssetById(id)?.toDomain()

    override suspend fun getLiabilityById(id: String): Liability? = liabilityDao.getLiabilityById(id)?.toDomain()

    override suspend fun addAsset(asset: Asset): Result<Unit> = runCatching {
        if (sandboxPreferences.isSandboxCached) {
            assetDao.insert(asset.toEntity(SyncStatus.SYNCED))
            return@runCatching
        }
        assetDao.insert(asset.toEntity(SyncStatus.PENDING_CREATE))
        try {
            firestoreDataSource.addAsset(asset.householdId, asset)
            assetDao.updateSyncStatus(asset.id, SyncStatus.SYNCED)
        } catch (_: Exception) { }
    }

    override suspend fun updateAsset(asset: Asset): Result<Unit> = runCatching {
        if (sandboxPreferences.isSandboxCached) {
            assetDao.update(asset.toEntity(SyncStatus.SYNCED))
            return@runCatching
        }
        assetDao.update(asset.toEntity(SyncStatus.PENDING_UPDATE))
        try {
            firestoreDataSource.updateAsset(asset.householdId, asset)
            assetDao.updateSyncStatus(asset.id, SyncStatus.SYNCED)
        } catch (_: Exception) { }
    }

    override suspend fun deleteAsset(id: String): Result<Unit> = runCatching {
        val asset = assetDao.getAssetById(id) ?: return@runCatching
        if (sandboxPreferences.isSandboxCached) {
            assetDao.deleteById(id)
            return@runCatching
        }
        assetDao.update(asset.copy(syncStatus = SyncStatus.PENDING_DELETE))
        try {
            firestoreDataSource.deleteAsset(asset.householdId, id)
            assetDao.deleteById(id)
        } catch (_: Exception) { }
    }

    override suspend fun addLiability(liability: Liability): Result<Unit> = runCatching {
        if (sandboxPreferences.isSandboxCached) {
            liabilityDao.insert(liability.toEntity(SyncStatus.SYNCED))
            return@runCatching
        }
        liabilityDao.insert(liability.toEntity(SyncStatus.PENDING_CREATE))
        try {
            firestoreDataSource.addLiability(liability.householdId, liability)
            liabilityDao.updateSyncStatus(liability.id, SyncStatus.SYNCED)
        } catch (_: Exception) { }
    }

    override suspend fun updateLiability(liability: Liability): Result<Unit> = runCatching {
        if (sandboxPreferences.isSandboxCached) {
            liabilityDao.update(liability.toEntity(SyncStatus.SYNCED))
            return@runCatching
        }
        liabilityDao.update(liability.toEntity(SyncStatus.PENDING_UPDATE))
        try {
            firestoreDataSource.updateLiability(liability.householdId, liability)
            liabilityDao.updateSyncStatus(liability.id, SyncStatus.SYNCED)
        } catch (_: Exception) { }
    }

    override suspend fun deleteLiability(id: String): Result<Unit> = runCatching {
        val liability = liabilityDao.getLiabilityById(id) ?: return@runCatching
        if (sandboxPreferences.isSandboxCached) {
            liabilityDao.deleteById(id)
            return@runCatching
        }
        liabilityDao.update(liability.copy(syncStatus = SyncStatus.PENDING_DELETE))
        try {
            firestoreDataSource.deleteLiability(liability.householdId, id)
            liabilityDao.deleteById(id)
        } catch (_: Exception) { }
    }

    override suspend fun syncPending() {
        val pendingAssets = assetDao.getPendingSyncAssets()
        for (entity in pendingAssets) {
            try {
                when (entity.syncStatus) {
                    SyncStatus.PENDING_CREATE -> {
                        firestoreDataSource.addAsset(entity.householdId, entity.toDomain())
                        assetDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
                    }
                    SyncStatus.PENDING_UPDATE -> {
                        firestoreDataSource.updateAsset(entity.householdId, entity.toDomain())
                        assetDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
                    }
                    SyncStatus.PENDING_DELETE -> {
                        firestoreDataSource.deleteAsset(entity.householdId, entity.id)
                        assetDao.deleteById(entity.id)
                    }
                }
            } catch (_: Exception) { }
        }

        val pendingLiabilities = liabilityDao.getPendingSyncLiabilities()
        for (entity in pendingLiabilities) {
            try {
                when (entity.syncStatus) {
                    SyncStatus.PENDING_CREATE -> {
                        firestoreDataSource.addLiability(entity.householdId, entity.toDomain())
                        liabilityDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
                    }
                    SyncStatus.PENDING_UPDATE -> {
                        firestoreDataSource.updateLiability(entity.householdId, entity.toDomain())
                        liabilityDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
                    }
                    SyncStatus.PENDING_DELETE -> {
                        firestoreDataSource.deleteLiability(entity.householdId, entity.id)
                        liabilityDao.deleteById(entity.id)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override fun startRealtimeSync(householdId: String) {
        if (sandboxPreferences.isSandboxCached) return
        assetSyncJob?.cancel()
        assetSyncJob = scope.launch {
            firestoreDataSource.observeAssets(householdId).collect { assets ->
                val pendingIds = assetDao.getPendingSyncAssets().map { it.id }.toSet()
                val safeToInsert = assets
                    .filter { it.id !in pendingIds }
                    .map { it.toEntity(SyncStatus.SYNCED) }
                if (safeToInsert.isNotEmpty()) {
                    assetDao.insertAll(safeToInsert)
                }
            }
        }
        liabilitySyncJob?.cancel()
        liabilitySyncJob = scope.launch {
            firestoreDataSource.observeLiabilities(householdId).collect { liabilities ->
                val pendingIds = liabilityDao.getPendingSyncLiabilities().map { it.id }.toSet()
                val safeToInsert = liabilities
                    .filter { it.id !in pendingIds }
                    .map { it.toEntity(SyncStatus.SYNCED) }
                if (safeToInsert.isNotEmpty()) {
                    liabilityDao.insertAll(safeToInsert)
                }
            }
        }
    }

    override fun stopRealtimeSync() {
        assetSyncJob?.cancel()
        liabilitySyncJob?.cancel()
        assetSyncJob = null
        liabilitySyncJob = null
    }
}
