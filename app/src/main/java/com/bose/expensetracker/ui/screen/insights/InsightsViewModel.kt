package com.bose.expensetracker.ui.screen.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.model.InsightType
import com.bose.expensetracker.domain.model.SpendingInsight
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class SummaryPeriod(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

data class PeriodSummary(
    val totalSpent: Double = 0.0,
    val previousPeriodSpent: Double = 0.0,
    val percentChange: Double = 0.0,
    val topCategory: String = "",
    val topCategoryAmount: Double = 0.0,
    val averageDailySpend: Double = 0.0,
    val daysInPeriod: Int = 1
)

data class InsightsUiState(
    val insights: List<SpendingInsight> = emptyList(),
    val dailySpending: Map<String, Double> = emptyMap(),
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val selectedPeriod: SummaryPeriod = SummaryPeriod.MONTH,
    val periodSummary: PeriodSummary = PeriodSummary(),
    val isLoading: Boolean = true
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private var allExpensesCache: List<Expense> = emptyList()

    init {
        loadInsights()
    }

    fun selectPeriod(period: SummaryPeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        recomputePeriod(allExpensesCache, period)
    }

    private fun recomputePeriod(allExpenses: List<Expense>, period: SummaryPeriod) {
        val now = Calendar.getInstance()
        val (currentStart, currentEnd) = periodRange(now, period)
        val prevCal = Calendar.getInstance().apply { timeInMillis = currentStart }
        when (period) {
            SummaryPeriod.WEEK -> prevCal.add(Calendar.WEEK_OF_YEAR, -1)
            SummaryPeriod.MONTH -> prevCal.add(Calendar.MONTH, -1)
            SummaryPeriod.YEAR -> prevCal.add(Calendar.YEAR, -1)
        }
        val (prevStart, prevEnd) = periodRange(prevCal, period)

        val currentExpenses = allExpenses.filter { it.date in currentStart until currentEnd }
        val prevExpenses = allExpenses.filter { it.date in prevStart until prevEnd }

        val totalSpent = currentExpenses.sumOf { it.amount }
        val prevTotal = prevExpenses.sumOf { it.amount }
        val pctChange = if (prevTotal > 0) ((totalSpent - prevTotal) / prevTotal * 100) else 0.0
        val days = ((currentEnd - currentStart) / 86400000L).coerceAtLeast(1).toInt()

        val topCat = currentExpenses.groupBy { it.categoryName }
            .maxByOrNull { (_, e) -> e.sumOf { it.amount } }

        val categoryBreakdown = currentExpenses
            .groupBy { it.categoryName }
            .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

        val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
        val dailySpending = currentExpenses
            .groupBy { dayFormat.format(Date(it.date)) }
            .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

        _uiState.update {
            it.copy(
                periodSummary = PeriodSummary(
                    totalSpent = totalSpent,
                    previousPeriodSpent = prevTotal,
                    percentChange = pctChange,
                    topCategory = topCat?.key ?: "",
                    topCategoryAmount = topCat?.value?.sumOf { e -> e.amount } ?: 0.0,
                    averageDailySpend = totalSpent / days,
                    daysInPeriod = days
                ),
                categoryBreakdown = categoryBreakdown,
                dailySpending = dailySpending
            )
        }
    }

    private fun periodRange(cal: Calendar, period: SummaryPeriod): Pair<Long, Long> {
        val start = cal.clone() as Calendar
        val end = cal.clone() as Calendar
        start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0)
        when (period) {
            SummaryPeriod.WEEK -> {
                start.set(Calendar.DAY_OF_WEEK, start.firstDayOfWeek)
                end.timeInMillis = start.timeInMillis; end.add(Calendar.WEEK_OF_YEAR, 1)
            }
            SummaryPeriod.MONTH -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
                end.timeInMillis = start.timeInMillis; end.add(Calendar.MONTH, 1)
            }
            SummaryPeriod.YEAR -> {
                start.set(Calendar.DAY_OF_YEAR, 1)
                end.timeInMillis = start.timeInMillis; end.add(Calendar.YEAR, 1)
            }
        }
        return start.timeInMillis to end.timeInMillis
    }

    private fun loadInsights() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val householdId = householdRepository.getUserHouseholdId(userId) ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            expenseRepository.startRealtimeSync(householdId)

            expenseRepository.getExpenses(householdId).collect { allExpenses ->
                allExpensesCache = allExpenses
                val now = Calendar.getInstance()
                val currentMonth = now.get(Calendar.MONTH)
                val currentYear = now.get(Calendar.YEAR)

                val lastMonthCal = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1)
                }
                val lastMonth = lastMonthCal.get(Calendar.MONTH)
                val lastMonthYear = lastMonthCal.get(Calendar.YEAR)

                val expenseCal = Calendar.getInstance()

                val thisMonthExpenses = allExpenses.filter { expense ->
                    expenseCal.timeInMillis = expense.date
                    expenseCal.get(Calendar.MONTH) == currentMonth && expenseCal.get(Calendar.YEAR) == currentYear
                }

                val lastMonthExpenses = allExpenses.filter { expense ->
                    expenseCal.timeInMillis = expense.date
                    expenseCal.get(Calendar.MONTH) == lastMonth && expenseCal.get(Calendar.YEAR) == lastMonthYear
                }

                val insights = generateInsights(thisMonthExpenses, lastMonthExpenses)

                _uiState.update {
                    it.copy(
                        insights = insights,
                        isLoading = false
                    )
                }

                recomputePeriod(allExpenses, _uiState.value.selectedPeriod)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        expenseRepository.stopRealtimeSync()
    }

    private fun generateInsights(
        thisMonth: List<Expense>,
        lastMonth: List<Expense>
    ): List<SpendingInsight> {
        val insights = mutableListOf<SpendingInsight>()
        val thisTotal = thisMonth.sumOf { it.amount }
        val lastTotal = lastMonth.sumOf { it.amount }

        // Monthly trend
        if (lastTotal > 0) {
            val change = ((thisTotal - lastTotal) / lastTotal * 100)
            insights.add(
                SpendingInsight(
                    title = if (change >= 0) "Spending Up" else "Spending Down",
                    description = "Your spending is ${if (change >= 0) "up" else "down"} ${String.format("%.0f", kotlin.math.abs(change))}% compared to last month",
                    type = if (change >= 0) InsightType.TREND_UP else InsightType.TREND_DOWN,
                    relatedCategory = null,
                    percentageChange = change
                )
            )
        }

        // Top category
        val topCategory = thisMonth
            .groupBy { it.categoryName }
            .maxByOrNull { (_, expenses) -> expenses.sumOf { it.amount } }

        if (topCategory != null) {
            val categoryTotal = topCategory.value.sumOf { it.amount }
            val pct = if (thisTotal > 0) (categoryTotal / thisTotal * 100).toInt() else 0
            insights.add(
                SpendingInsight(
                    title = "Top Category: ${topCategory.key}",
                    description = "${topCategory.key} accounts for $pct% of your spending this month",
                    type = InsightType.SUGGESTION,
                    relatedCategory = topCategory.key,
                    percentageChange = pct.toDouble()
                )
            )
        }

        // Category anomalies
        val lastMonthByCategory = lastMonth.groupBy { it.categoryName }
            .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
        thisMonth.groupBy { it.categoryName }.forEach { (name, expenses) ->
            val thisAmount = expenses.sumOf { it.amount }
            val lastAmount = lastMonthByCategory[name] ?: 0.0
            if (lastAmount > 0 && thisAmount > lastAmount * 1.5) {
                val increase = ((thisAmount - lastAmount) / lastAmount * 100).toInt()
                insights.add(
                    SpendingInsight(
                        title = "Spike in $name",
                        description = "$name spending is up $increase% vs last month",
                        type = InsightType.ANOMALY,
                        relatedCategory = name,
                        percentageChange = increase.toDouble()
                    )
                )
            }
        }

        // Person split
        val byPerson = thisMonth.groupBy { it.addedByName.ifBlank { "Unknown" } }
        if (byPerson.size > 1) {
            val personSummary = byPerson.entries.joinToString(", ") { (name, expenses) ->
                "$name: ${String.format("%.2f", expenses.sumOf { it.amount })}"
            }
            insights.add(
                SpendingInsight(
                    title = "Spending Split",
                    description = personSummary,
                    type = InsightType.SUGGESTION,
                    relatedCategory = null,
                    percentageChange = null
                )
            )
        }

        return insights
    }
}
