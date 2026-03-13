package com.bose.expensetracker.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object LoginRoute
@Serializable object SignUpRoute
@Serializable object PhoneAuthRoute
@Serializable object HouseholdSetupRoute
@Serializable object DashboardRoute
@Serializable object ExpenseListRoute
@Serializable data class AddEditExpenseRoute(val expenseId: String? = null)
@Serializable object CategoryRoute
@Serializable object InsightsRoute
@Serializable object ReceiptScannerRoute
@Serializable object NetWorthRoute
@Serializable data class AddEditAssetRoute(val assetId: String? = null, val isAsset: Boolean = true)
@Serializable object SettingsRoute
