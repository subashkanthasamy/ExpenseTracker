package com.bose.expensetracker.ui.screen.reminder

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.bose.expensetracker.data.local.entity.ReminderEntity
import com.bose.expensetracker.ui.components.SectionHeader
import com.bose.expensetracker.ui.theme.AccentPurple
import com.bose.expensetracker.ui.theme.ExpenseRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(
    viewModel: ReminderViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddBillDialog by remember { mutableStateOf(false) }
    var showDailyTimeDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "Reminders",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentPurple)
            }
        } else {
            ReminderContent(
                reminders = uiState.reminders,
                onToggle = { viewModel.toggleReminder(it) },
                onDelete = { viewModel.deleteReminder(it) },
                onAddDaily = { showDailyTimeDialog = true },
                onAddBudget = { showBudgetDialog = true },
                onAddBill = { showAddBillDialog = true }
            )
        }
    }

    // Add Bill Dialog
    if (showAddBillDialog) {
        var title by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var dueDay by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddBillDialog = false },
            title = { Text("Add Bill Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("Bill Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it },
                        label = { Text("Amount (\u20B9)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dueDay, onValueChange = { dueDay = it },
                        label = { Text("Due Day (1-31)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        val day = dueDay.toIntOrNull() ?: 1
                        if (title.isNotBlank() && amt > 0) {
                            viewModel.addBillReminder(title.trim(), amt, day, ReminderEntity.REPEAT_MONTHLY)
                            showAddBillDialog = false
                        }
                    },
                    enabled = title.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddBillDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Daily Reminder Time Dialog
    if (showDailyTimeDialog) {
        var hour by remember { mutableStateOf("20") }
        var minute by remember { mutableStateOf("00") }

        AlertDialog(
            onDismissRequest = { showDailyTimeDialog = false },
            title = { Text("Set Reminder Time") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hour, onValueChange = { hour = it },
                        label = { Text("Hour") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = minute, onValueChange = { minute = it },
                        label = { Text("Min") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 20
                    val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    viewModel.addDailyReminder(h, m)
                    showDailyTimeDialog = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showDailyTimeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Budget Limit Dialog
    if (showBudgetDialog) {
        var limit by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Set Monthly Budget") },
            text = {
                OutlinedTextField(
                    value = limit, onValueChange = { limit = it },
                    label = { Text("Budget Limit (\u20B9)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amt = limit.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            viewModel.addBudgetReminder(amt)
                            showBudgetDialog = false
                        }
                    },
                    enabled = (limit.toDoubleOrNull() ?: 0.0) > 0
                ) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ReminderContent(
    reminders: List<ReminderEntity>,
    onToggle: (ReminderEntity) -> Unit,
    onDelete: (String) -> Unit,
    onAddDaily: () -> Unit,
    onAddBudget: () -> Unit,
    onAddBill: () -> Unit
) {
    val dailyReminder = reminders.find { it.type == ReminderEntity.TYPE_DAILY }
    val budgetReminder = reminders.find { it.type == ReminderEntity.TYPE_BUDGET }
    val billReminders = reminders.filter { it.type == ReminderEntity.TYPE_BILL }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Daily Reminder
        item {
            SectionHeader("Daily Reminder")
            ReminderCard(
                icon = Icons.Default.AccessTime,
                iconColor = AccentPurple,
                title = "Log Expenses Daily",
                subtitle = if (dailyReminder != null)
                    "Reminder at ${"%02d:%02d".format(dailyReminder.hour, dailyReminder.minute)}"
                else "Tap to set reminder time",
                isEnabled = dailyReminder?.isEnabled ?: false,
                onToggle = {
                    if (dailyReminder != null) onToggle(dailyReminder)
                    else onAddDaily()
                },
                onClick = onAddDaily
            )
        }

        // Budget Alert
        item {
            SectionHeader("Budget Alert")
            ReminderCard(
                icon = Icons.Default.AccountBalanceWallet,
                iconColor = ExpenseRed,
                title = "Monthly Budget Limit",
                subtitle = if (budgetReminder != null)
                    "Alert when spending exceeds \u20B9${"%.0f".format(budgetReminder.amount)}"
                else "Tap to set budget limit",
                isEnabled = budgetReminder?.isEnabled ?: false,
                onToggle = {
                    if (budgetReminder != null) onToggle(budgetReminder)
                    else onAddBudget()
                },
                onClick = onAddBudget
            )
        }

        // Bill Reminders
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Bill Reminders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAddBill) {
                    Icon(Icons.Default.Add, contentDescription = "Add Bill", tint = AccentPurple)
                }
            }
        }

        if (billReminders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No bill reminders yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        items(billReminders) { bill ->
            BillReminderCard(
                bill = bill,
                onToggle = { onToggle(bill) },
                onDelete = { onDelete(bill.id) }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentPurple)
            )
        }
    }
}

@Composable
private fun BillReminderCard(
    bill: ReminderEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(bill.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "\u20B9${"%.0f".format(bill.amount)} \u2022 Due day ${bill.dueDay}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = bill.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentPurple)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExpenseRed, modifier = Modifier.size(20.dp))
            }
        }
    }
}
