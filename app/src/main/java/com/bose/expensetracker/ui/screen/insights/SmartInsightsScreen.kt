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
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.Icons
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

            // This used to be presented as a "savings rate", which this app cannot compute: a
            // savings rate is (income - spending) / income, and Expense has no income field —
            // every row is an outflow. The old formula was really month-over-month spending
            // change, it was clamped to 0..100 so spending MORE showed as 0% "saved", and when
            // there was no previous month it fell back to a hardcoded 50%. So a single rent
            // payment could render as "50% SAVINGS RATE" over the amount just spent.
            //
            // Null means there is nothing to compare against yet; say so rather than invent it.
            val changePct: Float? = if (prevSpent > 0) {
                (((totalSpent - prevSpent) / prevSpent) * 100).toFloat()
            } else null
            val spentLess = (changePct ?: 0f) < 0f

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
                                "Insights",
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
                            // The arc shows magnitude only; the sign lives in the text, because
                            // a ring cannot express "more" versus "less".
                            CircularProgressRing(
                                percentage = changePct?.let { kotlin.math.abs(it).coerceIn(0f, 100f) } ?: 0f,
                                centerText = changePct?.let {
                                    "${if (it >= 0) "+" else "-"}${kotlin.math.abs(it).toInt()}%"
                                } ?: "—",
                                ringSize = 140.dp,
                                strokeWidth = 12.dp,
                                progressColor = if (changePct == null) AccentPurple
                                else if (spentLess) SavingsGreen else OverBudgetRed
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                when {
                                    changePct == null -> "NO PREVIOUS MONTH TO COMPARE"
                                    spentLess -> "LESS THAN LAST MONTH"
                                    else -> "MORE THAN LAST MONTH"
                                },
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
                                "spent this month" +
                                    if (prevSpent > 0) " • ${formatCurrency(prevSpent)} last month" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Insight cards
                if (uiState.insights.isNotEmpty()) {
                    items(uiState.insights) { insight ->
                        val (icon, badgeLabel, badgeColor) = when (insight.type) {
                            InsightType.TREND_UP -> Triple(
                                Icons.Filled.TrendingUp,
                                "SPENT MORE",
                                OverBudgetRed
                            )
                            InsightType.TREND_DOWN -> Triple(
                                Icons.Filled.Payments,
                                "SPENT LESS",
                                SavingsGreen
                            )
                            InsightType.ANOMALY -> Triple(
                                Icons.Filled.WarningAmber,
                                "SPIKE",
                                ExpenseRed
                            )
                            InsightType.SUGGESTION -> Triple(
                                Icons.Filled.Lightbulb,
                                "INSIGHT",
                                AccentPurple
                            )
                        }

                        SmartInsightCard(
                            icon = icon,
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
                                icon = Icons.Filled.ShoppingCart,
                                title = "${topCat.key} is your biggest\nspending category",
                                amount = formatCurrency(topCat.value),
                                badgeLabel = "TOP CATEGORY",
                                badgeColor = ExpenseRed
                            )
                        }
                    }

                    item {
                        val avgDaily = if (uiState.periodSummary.daysInPeriod > 0)
                            total / uiState.periodSummary.daysInPeriod
                        else 0.0
                        SmartInsightCard(
                            icon = Icons.Filled.CalendarMonth,
                            title = "Average daily spending\nthis month",
                            amount = formatCurrency(avgDaily),
                            badgeLabel = "PER DAY",
                            badgeColor = AccentPurple
                        )
                    }
                }

                // Month-end projection
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        // Not "PREDICTIVE AI": this is a run-rate extrapolation, and calling
                        // arithmetic AI is the second misrepresentation after the claim itself.
                        "PROJECTION",
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
                            // This card was previously a hardcoded sentence promising "surplus
                            // for investments" to every user regardless of their data — a
                            // financial claim attributed to AI that read no data at all. It now
                            // shows a run-rate projection, or says why it cannot.
                            val projected = uiState.periodSummary.projectedTotal
                            Text(
                                if (projected == null) "Too early to project"
                                else "Projected by the end of the month",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (projected != null) {
                                Text(
                                    formatCurrency(projected),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentPurple
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "At ${formatCurrency(uiState.periodSummary.averageDailySpend)} " +
                                        "per day over ${uiState.periodSummary.daysElapsed} days. " +
                                        "Shared spending only.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    "Check back after a few more days of spending for a " +
                                        "monthly estimate.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
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
                                    "Review budgets",
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
