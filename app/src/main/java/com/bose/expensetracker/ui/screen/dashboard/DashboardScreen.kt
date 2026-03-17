package com.bose.expensetracker.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bose.expensetracker.ui.components.GradientCard
import com.bose.expensetracker.ui.components.SectionHeader
import com.bose.expensetracker.ui.components.TransactionItem
import com.bose.expensetracker.ui.components.formatCurrency
import com.bose.expensetracker.ui.theme.AccentPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    onViewAllExpenses: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentPurple)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top bar
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { }) {
                            Icon(
                                Icons.Default.Widgets,
                                contentDescription = "Dashboard",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Text(
                            "Home",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row {
                            IconButton(onClick = onNavigateToReminders) {
                                Icon(
                                    Icons.Default.Alarm,
                                    contentDescription = "Reminders",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            IconButton(onClick = onNavigateToNotifications) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = "SMS Report",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }

                // Person filter chips
                if (uiState.members.size > 1) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = uiState.personFilter == null,
                                    onClick = { viewModel.setPersonFilter(null) },
                                    label = { Text("All") }
                                )
                            }
                            items(uiState.members) { member ->
                                FilterChip(
                                    selected = uiState.personFilter == member.uid,
                                    onClick = { viewModel.setPersonFilter(member.uid) },
                                    label = { Text(member.displayName) }
                                )
                            }
                        }
                    }
                }

                // Gradient balance card
                item {
                    GradientCard {
                        Column {
                            Text(
                                "Total Balance",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                formatCurrency(uiState.monthTotal),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 34.sp
                                ),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Income
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                Color.White.copy(alpha = 0.2f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Income",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "Income",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            formatCurrency(0.0),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                                // Expenses
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                Color.White.copy(alpha = 0.2f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Expenses",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "Expenses",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            formatCurrency(uiState.monthTotal),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Empty state
                if (uiState.recentExpenses.isEmpty() && uiState.categoryBreakdown.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No expenses yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap + to add your first expense",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Transactions section
                if (uiState.recentExpenses.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Transactions",
                            actionText = "See All",
                            onAction = onViewAllExpenses,
                            modifier = Modifier.clickable { onViewAllExpenses() }
                                .padding(0.dp) // reset; the clickable is only on "See All" text area
                        )
                    }

                    items(uiState.recentExpenses.take(5)) { expense ->
                        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                        val timeString = timeFormat.format(Date(expense.date))
                        val dateString = dateFormat.format(Date(expense.date))
                        val subtitle = "$dateString, $timeString"

                        TransactionItem(
                            icon = getCategoryIcon(expense.categoryName),
                            iconBackground = getCategoryColor(expense.categoryName),
                            title = expense.categoryName,
                            subtitle = subtitle,
                            amount = -expense.amount, // negative for expenses
                            modifier = Modifier.clickable { onEditExpense(expense.id) }
                        )
                    }
                }

                // Bottom spacer for FAB
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        // Purple FAB
        FloatingActionButton(
            onClick = onAddExpense,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = AccentPurple,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Expense")
        }
    }
}

/**
 * Maps a category name to an appropriate Material icon.
 */
private fun getCategoryIcon(categoryName: String): ImageVector {
    val name = categoryName.lowercase()
    return when {
        name.contains("grocery") || name.contains("groceries") -> Icons.Default.LocalGroceryStore
        name.contains("food") || name.contains("restaurant") || name.contains("dining") -> Icons.Default.Fastfood
        name.contains("shopping") || name.contains("shop") -> Icons.Default.ShoppingCart
        name.contains("transport") || name.contains("fuel") || name.contains("car") || name.contains("travel") -> Icons.Default.DirectionsCar
        name.contains("flight") || name.contains("trip") -> Icons.Default.FlightTakeoff
        name.contains("rent") || name.contains("home") || name.contains("house") -> Icons.Default.Home
        name.contains("health") || name.contains("medical") || name.contains("medicine") -> Icons.Default.HealthAndSafety
        name.contains("education") || name.contains("school") || name.contains("course") -> Icons.Default.School
        name.contains("entertainment") || name.contains("movie") -> Icons.Default.Movie
        name.contains("phone") || name.contains("mobile") || name.contains("recharge") -> Icons.Default.PhoneAndroid
        name.contains("bill") || name.contains("utility") || name.contains("electricity") -> Icons.Default.Payments
        else -> Icons.Default.Receipt
    }
}

/**
 * Maps a category name to a color for the icon background.
 */
private fun getCategoryColor(categoryName: String): Color {
    val name = categoryName.lowercase()
    return when {
        name.contains("grocery") || name.contains("groceries") -> Color(0xFF4CAF50)
        name.contains("food") || name.contains("restaurant") || name.contains("dining") -> Color(0xFFFF9800)
        name.contains("shopping") || name.contains("shop") -> Color(0xFFE91E63)
        name.contains("transport") || name.contains("fuel") || name.contains("car") -> Color(0xFF2196F3)
        name.contains("flight") || name.contains("trip") || name.contains("travel") -> Color(0xFF00BCD4)
        name.contains("rent") || name.contains("home") || name.contains("house") -> Color(0xFF795548)
        name.contains("health") || name.contains("medical") -> Color(0xFFF44336)
        name.contains("education") || name.contains("school") -> Color(0xFF3F51B5)
        name.contains("entertainment") || name.contains("movie") -> Color(0xFF9C27B0)
        name.contains("phone") || name.contains("mobile") -> Color(0xFF607D8B)
        name.contains("bill") || name.contains("utility") -> Color(0xFFFF5722)
        else -> Color(0xFF7B61FF)
    }
}
