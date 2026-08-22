package com.bose.expensetracker.domain.model

data class Expense(
    val id: String,
    val householdId: String,
    val amount: Double,
    val categoryId: String,
    val categoryName: String,
    val date: Long,
    val notes: String,
    val addedBy: String,
    val addedByName: String,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * How it was paid. Defaulted so every existing construction site keeps compiling, and so
     * rows written before this field existed read back as
     * [PaymentMethod.UNSPECIFIED] rather than being guessed at.
     */
    val paymentMethod: PaymentMethod = PaymentMethod.UNSPECIFIED,
    /**
     * Visibility. Defaults to [ExpenseScope.SHARED] so existing construction sites compile and
     * so a row can never become invisible by omission.
     */
    val scope: ExpenseScope = ExpenseScope.SHARED,
    val isSynced: Boolean = false
)
