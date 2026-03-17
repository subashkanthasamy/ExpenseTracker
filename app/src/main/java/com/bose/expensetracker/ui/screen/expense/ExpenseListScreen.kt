package com.bose.expensetracker.ui.screen.expense

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.ui.components.SectionHeader
import com.bose.expensetracker.ui.components.TransactionItem
import com.bose.expensetracker.ui.theme.AccentPurple
import com.bose.expensetracker.ui.theme.ExpenseRed
import com.bose.expensetracker.ui.components.formatCurrency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpenseListScreen(
    viewModel: ExpenseListViewModel,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExpense,
                containerColor = AccentPurple,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Custom top row with centered title
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Expenses",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Rounded search bar
            TextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = {
                    Text(
                        "Search expenses...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentPurple)
                    }
                }

                uiState.expenses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No expenses yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tap + to add your first expense",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val sorted = uiState.expenses.sortedByDescending { it.date }
                    val grouped = sorted.groupBy { expense ->
                        dateFormat.format(Date(expense.date))
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        grouped.forEach { (date, expenses) ->
                            item(key = "header_$date") {
                                SectionHeader(
                                    title = date,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            items(expenses, key = { it.id }) { expense ->
                                ExpenseTransactionRow(
                                    expense = expense,
                                    dateFormat = dateFormat,
                                    onEdit = { onEditExpense(expense.id) },
                                    onDeleteRequest = { expenseToDelete = expense }
                                )
                            }
                        }
                        // Bottom spacing so FAB doesn't overlap last item
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    expenseToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Delete Expense") },
            text = {
                Text(
                    "Are you sure you want to delete this ${expense.categoryName} expense of ${
                        formatCurrency(expense.amount)
                    }?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense(expense.id)
                    expenseToDelete = null
                }) {
                    Text("Delete", color = ExpenseRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ExpenseTransactionRow(
    expense: Expense,
    dateFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val (icon, iconColor) = getCategoryVisuals(expense.categoryName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransactionItem(
            icon = icon,
            iconBackground = iconColor,
            title = expense.categoryName,
            subtitle = dateFormat.format(Date(expense.date)),
            amount = -expense.amount,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDeleteRequest) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = ExpenseRed,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun getCategoryVisuals(categoryName: String): Pair<ImageVector, Color> {
    val name = categoryName.lowercase()
    return when {
        name.contains("food") || name.contains("dining") || name.contains("restaurant") ->
            Icons.Outlined.Restaurant to Color(0xFFFF7043)
        name.contains("grocery") || name.contains("shopping") || name.contains("shop") ->
            Icons.Outlined.ShoppingCart to Color(0xFF7B61FF)
        name.contains("transport") || name.contains("fuel") || name.contains("car") || name.contains("travel") ->
            Icons.Outlined.DirectionsCar to Color(0xFF42A5F5)
        name.contains("rent") || name.contains("home") || name.contains("house") ->
            Icons.Outlined.Home to Color(0xFF26A69A)
        name.contains("health") || name.contains("medical") || name.contains("doctor") ->
            Icons.Outlined.LocalHospital to Color(0xFFEF5350)
        name.contains("education") || name.contains("school") || name.contains("book") ->
            Icons.Outlined.School to Color(0xFF5C6BC0)
        name.contains("entertainment") || name.contains("game") || name.contains("movie") ->
            Icons.Outlined.SportsEsports to Color(0xFFAB47BC)
        name.contains("flight") || name.contains("vacation") ->
            Icons.Outlined.Flight to Color(0xFF29B6F6)
        name.contains("cloth") || name.contains("fashion") || name.contains("apparel") ->
            Icons.Outlined.Checkroom to Color(0xFFEC407A)
        name.contains("bill") || name.contains("electric") || name.contains("utility") || name.contains("recharge") ->
            Icons.Outlined.Bolt to Color(0xFFFFA726)
        else ->
            Icons.Outlined.MoreHoriz to Color(0xFF78909C)
    }
}
