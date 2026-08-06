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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bose.expensetracker.domain.model.InsightType
import com.bose.expensetracker.ui.components.CircularProgressRing
import com.bose.expensetracker.ui.components.SmartInsightCard
import com.bose.expensetracker.ui.components.formatCurrency
import com.bose.expensetracker.ui.theme.AccentPurple
import com.bose.expensetracker.ui.theme.ExpenseRed
import com.bose.expensetracker.ui.theme.OverBudgetRed
import com.bose.expensetracker.ui.theme.SavingsGreen

@Composable
fun SmartInsightsScreen(
    viewModel: InsightsViewModel,
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToBudget: () -> Unit = {}
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
            val totalSpent = uiState.periodSummary.totalSpent
            val prevSpent = uiState.periodSummary.previousPeriodSpent
            val savingsRate = if (prevSpent > 0) {
                ((1 - (totalSpent / prevSpent)) * 100).toFloat().coerceIn(0f, 100f)
            } else if (totalSpent > 0) 50f else 0f

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                "ANALYSIS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "\uD83D\uDCA1 Smart Insights",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        androidx.compose.material3.TextButton(onClick = onNavigateToAnalytics) {
                            Text(
                                "Analytics \u2192",
                                color = AccentPurple,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Circular progress ring + savings rate
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressRing(
                                percentage = savingsRate,
                                centerText = "${savingsRate.toInt()}%",
                                ringSize = 140.dp,
                                strokeWidth = 12.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "SAVINGS RATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                formatCurrency(totalSpent),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "of total expenses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Insight cards
                if (uiState.insights.isNotEmpty()) {
                    items(uiState.insights) { insight ->
                        val (emoji, badgeLabel, badgeColor) = when (insight.type) {
                            InsightType.TREND_UP -> Triple(
                                "\uD83D\uDCC8",
                                "OVER BUDGET",
                                OverBudgetRed
                            )
                            InsightType.TREND_DOWN -> Triple(
                                "\uD83D\uDCB0",
                                "SAVINGS",
                                SavingsGreen
                            )
                            InsightType.ANOMALY -> Triple(
                                "\u26A0\uFE0F",
                                "SPIKE",
                                ExpenseRed
                            )
                            InsightType.SUGGESTION -> Triple(
                                "\uD83D\uDCA1",
                                "INSIGHT",
                                AccentPurple
                            )
                        }

                        SmartInsightCard(
                            emoji = emoji,
                            title = insight.title + (insight.description.let { "\n$it" }),
                            amount = insight.percentageChange?.let {
                                "${if (it >= 0) "+" else ""}${"%.0f".format(it)}%"
                            } ?: "",
                            badgeLabel = badgeLabel,
                            badgeColor = badgeColor
                        )
                    }
                }

                // Category quick stats from breakdown
                if (uiState.categoryBreakdown.isNotEmpty()) {
                    val total = uiState.categoryBreakdown.values.sum()
                    val topCat = uiState.categoryBreakdown.entries.maxByOrNull { it.value }
                    if (topCat != null) {
                        val pct = if (total > 0) (topCat.value / total * 100).toInt() else 0
                        item {
                            SmartInsightCard(
                                emoji = "\uD83D\uDED2",
                                title = "${topCat.key} is your biggest\nspend category",
                                amount = formatCurrency(topCat.value),
                                badgeLabel = "HIGHEST SPEND",
                                badgeColor = ExpenseRed
                            )
                        }
                    }

                    item {
                        val avgDaily = if (uiState.periodSummary.daysInPeriod > 0)
                            total / uiState.periodSummary.daysInPeriod
                        else 0.0
                        SmartInsightCard(
                            emoji = "\uD83D\uDCC5",
                            title = "Daily average spend this\nmonth",
                            amount = formatCurrency(avgDaily),
                            badgeLabel = "PER DAY",
                            badgeColor = AccentPurple
                        )
                    }
                }

                // Predictive AI section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "PREDICTIVE AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Next month projection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Based on your current trajectory, we estimate you will have surplus for investments if your spending returns to normal levels.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToBudget,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentPurple
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Review Budget Plan",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
