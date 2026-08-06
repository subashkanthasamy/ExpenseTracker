package com.bose.expensetracker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bose.expensetracker.ui.theme.AccentPurple
import com.bose.expensetracker.ui.theme.NavInactive

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val route: Any
)

val bottomNavItems = listOf(
    BottomNavItem("Home", Icons.Outlined.Home, Icons.Filled.Home, DashboardRoute),
    BottomNavItem("Timeline", Icons.Outlined.ViewTimeline, Icons.Filled.ViewTimeline, ExpenseListRoute),
    BottomNavItem("Insights", Icons.Outlined.Insights, Icons.Filled.Insights, SmartInsightsRoute),
    BottomNavItem("Wealth", Icons.Outlined.AccountBalance, Icons.Filled.AccountBalance, NetWorthRoute)
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onItemClick: (Any) -> Unit,
    onFabClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Bottom bar row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left 2 items
            bottomNavItems.take(2).forEach { item ->
                NavBarItem(
                    item = item,
                    isSelected = isRouteSelected(currentRoute, item.route),
                    onClick = { onItemClick(item.route) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Center spacer for FAB
            Box(modifier = Modifier.weight(1f))

            // Right 2 items
            bottomNavItems.drop(2).forEach { item ->
                NavBarItem(
                    item = item,
                    isSelected = isRouteSelected(currentRoute, item.route),
                    onClick = { onItemClick(item.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Center FAB (elevated above bar)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-16).dp)
                .shadow(8.dp, CircleShape)
                .size(56.dp)
                .clip(CircleShape)
                .background(AccentPurple)
                .clickable { onFabClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add Transaction",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun NavBarItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(22.dp),
            tint = if (isSelected) AccentPurple else NavInactive
        )
        Text(
            text = item.label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) AccentPurple else NavInactive,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            softWrap = false
        )
    }
}

private fun isRouteSelected(currentRoute: String?, itemRoute: Any): Boolean {
    val routeName = itemRoute::class.qualifiedName ?: ""
    val shortName = routeName.substringAfterLast(".")
    return currentRoute?.endsWith(shortName) == true
}
