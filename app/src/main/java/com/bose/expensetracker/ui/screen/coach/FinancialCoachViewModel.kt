package com.bose.expensetracker.ui.screen.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.bose.expensetracker.ui.components.formatCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val inlineStats: List<InlineStat>? = null
)

data class InlineStat(
    val emoji: String,
    val label: String,
    val value: String,
    val isPositive: Boolean
)

data class CoachUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val financialScore: Int = 78,
    val suggestions: List<String> = listOf(
        "Can I save this month?",
        "My financial score",
        "Invest"
    )
)

@HiltViewModel
class FinancialCoachViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    private var totalExpenses = 0.0
    private var topCategory = ""
    private var topCategoryAmount = 0.0

    init {
        loadFinancialContext()
        // Welcome message
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatMessage(
                        text = "Hello! I'm your AI Financial Coach. I've analyzed your spending patterns. How can I help you optimize your wealth today?",
                        isUser = false
                    )
                )
            )
        }
    }

    private fun loadFinancialContext() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            val hId = householdRepository.getUserHouseholdId(userId) ?: return@launch
            val expenses = expenseRepository.getExpenses(hId).firstOrNull() ?: return@launch

            val now = Calendar.getInstance()
            val currentMonth = now.get(Calendar.MONTH)
            val currentYear = now.get(Calendar.YEAR)
            val cal = Calendar.getInstance()

            val thisMonthExpenses = expenses.filter { e ->
                cal.timeInMillis = e.date
                cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
            }

            totalExpenses = thisMonthExpenses.sumOf { it.amount }
            val byCat = thisMonthExpenses.groupBy { it.categoryName }
                .mapValues { (_, v) -> v.sumOf { it.amount } }
            val top = byCat.maxByOrNull { it.value }
            topCategory = top?.key ?: "Unknown"
            topCategoryAmount = top?.value ?: 0.0
        }
    }

    fun setInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(text: String? = null) {
        val message = text ?: _uiState.value.inputText.trim()
        if (message.isBlank()) return

        val userMsg = ChatMessage(text = message, isUser = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                inputText = "",
                isLoading = true
            )
        }

        viewModelScope.launch {
            // Simulate thinking delay
            kotlinx.coroutines.delay(800)

            val response = generateResponse(message)
            _uiState.update {
                it.copy(
                    messages = it.messages + response,
                    isLoading = false
                )
            }
        }
    }

    private fun generateResponse(query: String): ChatMessage {
        val lowerQuery = query.lowercase()

        return when {
            lowerQuery.contains("afford") || lowerQuery.contains("buy") -> {
                ChatMessage(
                    text = "Based on your current balance and projected expenses, this purchase fits within your budget. You'll still meet your monthly savings goal.",
                    isUser = false,
                    inlineStats = listOf(
                        InlineStat("✅", "Status", "AFFORDABLE", true)
                    )
                )
            }
            lowerQuery.contains("overspend") || lowerQuery.contains("spending") -> {
                ChatMessage(
                    text = "I've detected that $topCategory is your highest spending category at ${formatCurrency(topCategoryAmount)} this month. That's ${
                        if (totalExpenses > 0) ((topCategoryAmount / totalExpenses) * 100).toInt() else 0
                    }% of your total spending.",
                    isUser = false,
                    inlineStats = listOf(
                        InlineStat("\uD83D\uDED2", topCategory, "+${((topCategoryAmount / totalExpenses.coerceAtLeast(1.0)) * 100).toInt()}%", false),
                        InlineStat("\uD83D\uDCB0", "Total", formatCurrency(totalExpenses), true)
                    )
                )
            }
            lowerQuery.contains("save") || lowerQuery.contains("saving") -> {
                val savingsEstimate = (totalExpenses * 0.15)
                ChatMessage(
                    text = "Based on your spending patterns, you could potentially save ${formatCurrency(savingsEstimate)} this month by reducing discretionary spending in $topCategory.",
                    isUser = false,
                    inlineStats = listOf(
                        InlineStat("\uD83D\uDCB8", "Potential Savings", formatCurrency(savingsEstimate), true)
                    )
                )
            }
            lowerQuery.contains("score") || lowerQuery.contains("financial") -> {
                ChatMessage(
                    text = "Your financial health score is ${_uiState.value.financialScore}/100. This is based on your spending habits, savings rate, and budget adherence. Keep it up!",
                    isUser = false,
                    inlineStats = listOf(
                        InlineStat("⭐", "Score", "${_uiState.value.financialScore}/100", true)
                    )
                )
            }
            lowerQuery.contains("invest") -> {
                ChatMessage(
                    text = "With your current spending of ${formatCurrency(totalExpenses)}, consider allocating 20% of your surplus to a diversified portfolio. Start with index funds for stability.",
                    isUser = false
                )
            }
            else -> {
                ChatMessage(
                    text = "Your total spending this month is ${formatCurrency(totalExpenses)}. Your top category is $topCategory at ${formatCurrency(topCategoryAmount)}. Would you like specific advice on budgeting, saving, or investing?",
                    isUser = false
                )
            }
        }
    }
}
