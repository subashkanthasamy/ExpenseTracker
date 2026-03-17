package com.bose.expensetracker.ui.screen.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onSignOut: () -> Unit,
    onHouseholdSwitched: () -> Unit = {},
    onNavigateToHousehold: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showExportSheet by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.RECEIVE_SMS] == true
        if (smsGranted) {
            viewModel.setSmsImportEnabled(true)
        } else {
            viewModel.setSmsImportEnabled(false)
        }
    }
    val exportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            if (inputStream != null) {
                viewModel.importExpenses(inputStream)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.signOutEvent.collect {
            onSignOut()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.householdSwitchedEvent.collect {
            onHouseholdSwitched()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportedFile.collect { file ->
            shareExportedFile(context, file)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Name: ${uiState.user?.displayName ?: "-"}")
                        Text("Email: ${uiState.user?.email ?: "-"}")
                    }
                }

                // Household
                Card(
                    onClick = onNavigateToHousehold,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("Household") },
                        supportingContent = { Text(uiState.household?.name ?: "Manage households") },
                        leadingContent = { Icon(Icons.Default.Home, contentDescription = null) },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    )
                }

                // Biometric
                ListItem(
                    headlineContent = { Text("Biometric Lock") },
                    supportingContent = { Text("Require fingerprint/face to open app") },
                    leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = uiState.biometricEnabled,
                            onCheckedChange = { viewModel.setBiometricEnabled(it) }
                        )
                    }
                )

                // SMS Auto-Import
                ListItem(
                    headlineContent = { Text("SMS Auto-Import") },
                    supportingContent = { Text("Automatically create expenses from bank SMS") },
                    leadingContent = { Icon(Icons.Default.Sms, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = uiState.smsImportEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val permissions = buildList {
                                        add(Manifest.permission.RECEIVE_SMS)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }.toTypedArray()
                                    smsPermissionLauncher.launch(permissions)
                                } else {
                                    viewModel.setSmsImportEnabled(false)
                                }
                            }
                        )
                    }
                )

                // Export
                ListItem(
                    headlineContent = { Text("Export Data") },
                    supportingContent = { Text("Export expenses as CSV or PDF") },
                    leadingContent = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    trailingContent = {
                        Button(onClick = { showExportSheet = true }) { Text("Export") }
                    }
                )

                // Import
                ListItem(
                    headlineContent = { Text("Import Data") },
                    supportingContent = { Text("Import expenses from a CSV file") },
                    leadingContent = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                    trailingContent = {
                        if (uiState.isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Button(onClick = { importFilePicker.launch("text/*") }) {
                                Text("Import")
                            }
                        }
                    }
                )

                // Reset All Expenses
                ListItem(
                    headlineContent = { Text("Reset All Expenses") },
                    supportingContent = { Text("Delete all expenses from this household") },
                    leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                    trailingContent = {
                        if (uiState.isResetting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Button(
                                onClick = { showResetConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("Reset") }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sign Out
                Button(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Sign Out")
                }

                // App version
                Text(
                    "Expense Tracker v1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    if (showExportSheet) {
        ExportBottomSheet(
            sheetState = exportSheetState,
            isExporting = uiState.isExporting,
            onDismiss = { showExportSheet = false },
            onExport = { format, startDate, endDate ->
                viewModel.exportExpenses(format, startDate, endDate)
                showExportSheet = false
            }
        )
    }

    uiState.importMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearImportMessage() },
            title = { Text("Import Result") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearImportMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrorMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset All Expenses") },
            text = { Text("Are you sure you want to delete all expenses? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllExpenses()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    uiState.resetMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearResetMessage() },
            title = { Text("Reset Expenses") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearResetMessage() }) {
                    Text("OK")
                }
            }
        )
    }
}
