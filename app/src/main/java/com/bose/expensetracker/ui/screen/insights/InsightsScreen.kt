package com.bose.expensetracker.ui.screen.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bose.expensetracker.domain.model.InsightType
import com.bose.expensetracker.domain.model.SpendingInsight
import com.bose.expensetracker.ui.components.BarChart
import com.bose.expensetracker.ui.components.BarChartEntry
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: InsightsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN"))

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Insights") })
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Smart Insights
                if (uiState.insights.isNotEmpty()) {
                    item {
                        Text(
                            "Smart Insights",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(uiState.insights) { insight ->
                        InsightCard(insight)
                    }
                }

                // Daily Spending Bar Chart
                if (uiState.dailySpending.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Daily Spending Trend",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BarChart(
                                data = uiState.dailySpending.entries
                                    .sortedBy { it.key }
                                    .takeLast(14)
                                    .map { (day, amount) ->
                                        BarChartEntry(label = day, value = amount.toFloat())
                                    },
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // Category breakdown
                if (uiState.categoryBreakdown.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Category Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val total = uiState.categoryBreakdown.values.sum()
                    items(uiState.categoryBreakdown.entries.sortedByDescending { it.value }.toList()) { (name, amount) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        currencyFormat.format(amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { if (total > 0) (amount / total).toFloat() else 0f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun InsightCard(insight: SpendingInsight) {
    val (containerColor, icon) = when (insight.type) {
        InsightType.TREND_UP -> MaterialTheme.colorScheme.errorContainer to Icons.Default.TrendingUp
        InsightType.TREND_DOWN -> MaterialTheme.colorScheme.primaryContainer to Icons.Default.TrendingDown
        InsightType.ANOMALY -> MaterialTheme.colorScheme.tertiaryContainer to Icons.Default.Warning
        InsightType.SUGGESTION -> MaterialTheme.colorScheme.secondaryContainer to Icons.Default.Lightbulb
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(insight.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(insight.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
