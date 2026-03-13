package com.bose.expensetracker.data.mapper

import com.bose.expensetracker.data.local.entity.AssetEntity
import com.bose.expensetracker.data.local.entity.LiabilityEntity
import com.bose.expensetracker.data.local.entity.SyncStatus
import com.bose.expensetracker.domain.model.Asset
import com.bose.expensetracker.domain.model.Liability

fun AssetEntity.toDomain() = Asset(
    id = id,
    householdId = householdId,
    name = name,
    value = value,
    type = type,
    date = date,
    addedBy = addedBy
)

fun Asset.toEntity(syncStatus: Int = SyncStatus.SYNCED) = AssetEntity(
    id = id,
    householdId = householdId,
    name = name,
    value = value,
    type = type,
    date = date,
    addedBy = addedBy,
    syncStatus = syncStatus
)

fun LiabilityEntity.toDomain() = Liability(
    id = id,
    householdId = householdId,
    name = name,
    amount = amount,
    type = type,
    date = date,
    addedBy = addedBy
)

fun Liability.toEntity(syncStatus: Int = SyncStatus.SYNCED) = LiabilityEntity(
    id = id,
    householdId = householdId,
    name = name,
    amount = amount,
    type = type,
    date = date,
    addedBy = addedBy,
    syncStatus = syncStatus
)
