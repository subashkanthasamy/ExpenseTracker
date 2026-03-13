package com.bose.expensetracker.data.mapper

import com.bose.expensetracker.data.local.entity.ExpenseEntity
import com.bose.expensetracker.data.local.entity.SyncStatus
import com.bose.expensetracker.domain.model.Expense

fun ExpenseEntity.toDomain() = Expense(
    id = id,
    householdId = householdId,
    amount = amount,
    categoryId = categoryId,
    categoryName = categoryName,
    date = date,
    notes = notes,
    addedBy = addedBy,
    addedByName = addedByName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isSynced = syncStatus == SyncStatus.SYNCED
)

fun Expense.toEntity(syncStatus: Int = SyncStatus.SYNCED) = ExpenseEntity(
    id = id,
    householdId = householdId,
    amount = amount,
    categoryId = categoryId,
    categoryName = categoryName,
    date = date,
    notes = notes,
    addedBy = addedBy,
    addedByName = addedByName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus
)
