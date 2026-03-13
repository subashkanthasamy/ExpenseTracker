package com.bose.expensetracker.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showExportSheet by remember { mutableStateOf(false) }
    val exportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.signOutEvent.collect {
            onSignOut()
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
                uiState.household?.let { household ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Household", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Name: ${household.name}")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Invite Code: ${household.inviteCode}", fontWeight = FontWeight.Bold)
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(household.inviteCode))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Members:", style = MaterialTheme.typography.labelMedium)
                            uiState.members.forEach { member ->
                                Text("  ${member.displayName} (${member.email})")
                            }
                        }
                    }
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

                // Export
                ListItem(
                    headlineContent = { Text("Export Data") },
                    supportingContent = { Text("Export expenses as CSV or PDF") },
                    leadingContent = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    trailingContent = {
                        Button(onClick = { showExportSheet = true }) { Text("Export") }
                    }
                )

                // Seed Categories
                ListItem(
                    headlineContent = { Text("Seed Categories") },
                    supportingContent = { Text("Push preset categories to Firebase") },
                    trailingContent = {
                        Button(onClick = { viewModel.seedCategories() }) { Text("Seed") }
                    }
                )

                // Populate Dummy Data
                ListItem(
                    headlineContent = { Text("Populate Dummy Data") },
                    supportingContent = { Text("Insert sample expenses for testing") },
                    trailingContent = {
                        Button(onClick = { viewModel.populateDummyData() }) { Text("Populate") }
                    }
                )

                if (uiState.dummyDataMessage != null) {
                    Text(
                        uiState.dummyDataMessage!!,
                        color = if (uiState.dummyDataMessage!!.startsWith("Error"))
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

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
}
