package com.bose.expensetracker.ui.screen.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HouseholdSetupScreen(
    viewModel: AuthViewModel,
    onNavigateToDashboard: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateForm by remember { mutableStateOf(false) }
    var showJoinForm by remember { mutableStateOf(false) }
    var householdName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.NavigateToDashboard -> onNavigateToDashboard()
                else -> {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Set Up Your Household",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Create a new household or join an existing one",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!showCreateForm && !showJoinForm) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                onClick = { showCreateForm = true }
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Create New Household", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Start fresh and invite your partner",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                onClick = { showJoinForm = true }
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Join Existing Household", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Enter the invite code from your partner",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (showCreateForm) {
            OutlinedTextField(
                value = householdName,
                onValueChange = { householdName = it },
                label = { Text("Household Name") },
                placeholder = { Text("e.g., The Bose Family") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        showCreateForm = false
                        householdName = ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { viewModel.createHousehold(householdName) },
                    enabled = !uiState.isLoading && householdName.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    } else {
                        Text("Create")
                    }
                }
            }

            uiState.household?.let { household ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Invite Code:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            household.inviteCode,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Share this code with your partner",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (showJoinForm) {
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it.uppercase().take(6) },
                label = { Text("Invite Code") },
                placeholder = { Text("Enter 6-character code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        showJoinForm = false
                        inviteCode = ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { viewModel.joinHousehold(inviteCode) },
                    enabled = !uiState.isLoading && inviteCode.length == 6,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    } else {
                        Text("Join")
                    }
                }
            }
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Snackbar(
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}
