package com.bose.expensetracker.ui.screen.insights

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bose.expensetracker.ui.components.CategoryProgressBar
import com.bose.expensetracker.ui.components.SectionHeader
import com.bose.expensetracker.ui.components.WeeklyBarChart
import com.bose.expensetracker.ui.components.formatCurrency
import com.bose.expensetracker.ui.components.getCategoryEmoji
import com.bose.expensetracker.ui.theme.AccentPurple
import com.bose.expensetracker.ui.theme.ExpenseRed
import com.bose.expensetracker.ui.theme.IncomeGreen

@Composable
fun InsightsScreen(viewModel: InsightsViewModel) {
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
        } else if (uiState.insights.isEmpty() && uiState.dailySpending.isEmpty() && uiState.categoryBreakdown.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                AnalyticsHeader()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = AccentPurple.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No Insights Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Start adding expenses to see spending trends and analytics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            val totalExpenses = uiState.categoryBreakdown.values.sum()
            val summary = uiState.periodSummary

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item { AnalyticsHeader() }

                // Period selector
                item {
                    com.bose.expensetracker.ui.components.FilterTabRow(
                        tabs = SummaryPeriod.entries.map { it.label },
                        selectedIndex = SummaryPeriod.entries.indexOf(uiState.selectedPeriod),
                        onTabSelected = { index ->
                            viewModel.selectPeriod(SummaryPeriod.entries[index])
                        }
                    )
                }

                // Savings rate card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "SAVINGS RATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatCurrency(summary.totalSpent),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "75%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentPurple
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val pctChange = summary.percentChange
                            val changeColor = if (pctChange <= 0) IncomeGreen else ExpenseRed
                            val arrow = if (pctChange <= 0) "↓" else "↑"
                            Text(
                                "$arrow ${"%.1f".format(kotlin.math.abs(pctChange))}% vs last month",
                                style = MaterialTheme.typography.bodySmall,
                                color = changeColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Income / Spent cards side by side
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "INCOME",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    formatCurrency(0.0),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = IncomeGreen
                                )
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "SPENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    formatCurrency(totalExpenses),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = ExpenseRed
                                )
                            }
                        }
                    }
                }

                // Weekly Spend bar chart
                if (uiState.dailySpending.isNotEmpty()) {
                    item {
                        SectionHeader(title = "WEEKLY SPEND")
                    }
                    item {
                        // Map daily spending entries to chart data with day-of-month labels
                        val sortedEntries = uiState.dailySpending.entries
                            .sortedBy { it.key }
                            .takeLast(7)
                        val chartData = sortedEntries.map { entry ->
                            entry.key to entry.value.toFloat()
                        }
                        if (chartData.isNotEmpty()) {
                            WeeklyBarChart(data = chartData)
                        }
                    }
                }

                // By Category
                if (uiState.categoryBreakdown.isNotEmpty()) {
                    item {
                        SectionHeader(title = "BY CATEGORY")
                    }

                    val total = uiState.categoryBreakdown.values.sum()
                    val sortedCategories = uiState.categoryBreakdown.entries
                        .sortedByDescending { it.value }

                    items(sortedCategories.toList()) { (name, amount) ->
                        val pct = if (total > 0) (amount / total * 100).toFloat() else 0f
                        CategoryProgressBar(
                            emoji = getCategoryEmoji(name),
                            name = name,
                            percentage = pct,
                            color = AccentPurple
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AnalyticsHeader() {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "PERFORMANCE OVERVIEW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Analytics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = { }) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
