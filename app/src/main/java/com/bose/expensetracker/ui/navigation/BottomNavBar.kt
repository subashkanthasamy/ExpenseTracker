package com.bose.expensetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: Any
)

val bottomNavItems = listOf(
    BottomNavItem("Dashboard", Icons.Default.Home, DashboardRoute),
    BottomNavItem("Expenses", Icons.Default.Receipt, ExpenseListRoute),
    BottomNavItem("Insights", Icons.Default.Insights, InsightsRoute),
    BottomNavItem("Net Worth", Icons.Default.AccountBalance, NetWorthRoute),
    BottomNavItem("Settings", Icons.Default.Settings, SettingsRoute)
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onItemClick: (Any) -> Unit
) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            val routeName = item.route::class.qualifiedName ?: ""
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute?.contains(routeName.substringAfterLast(".")) == true,
                onClick = { onItemClick(item.route) }
            )
        }
    }
}
