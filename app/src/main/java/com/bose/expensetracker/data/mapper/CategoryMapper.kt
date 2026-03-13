package com.bose.expensetracker.data.mapper

import com.bose.expensetracker.data.local.entity.CategoryEntity
import com.bose.expensetracker.data.local.entity.SyncStatus
import com.bose.expensetracker.domain.model.Category

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    icon = icon,
    color = color,
    isPreset = isPreset,
    householdId = householdId
)

fun Category.toEntity(syncStatus: Int = SyncStatus.SYNCED) = CategoryEntity(
    id = id,
    name = name,
    icon = icon,
    color = color,
    isPreset = isPreset,
    householdId = householdId,
    syncStatus = syncStatus
)
