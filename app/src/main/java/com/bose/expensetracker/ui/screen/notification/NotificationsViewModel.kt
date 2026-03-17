package com.bose.expensetracker.ui.screen.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.data.local.dao.PendingSmsDao
import com.bose.expensetracker.data.local.dao.ProcessedSmsDao
import com.bose.expensetracker.data.local.entity.PendingSmsEntity
import com.bose.expensetracker.data.local.entity.ProcessedSmsEntity
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.bose.expensetracker.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class SmsStats(
    val totalImported: Int = 0,
    val thisMonth: Int = 0
)

data class NotificationsUiState(
    val pendingSms: List<PendingSmsEntity> = emptyList(),
    val smsHistory: List<SmsHistoryItem> = emptyList(),
    val smsStats: SmsStats = SmsStats(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

data class SmsHistoryItem(
    val expenseId: String,
    val amount: Double,
    val categoryName: String,
    val notes: String,
    val date: Long,
    val processedAt: Long
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val processedSmsDao: ProcessedSmsDao,
    private val pendingSmsDao: PendingSmsDao,
    private val expenseRepository: ExpenseRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val categoryRepository: CategoryRepository,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            val householdId = householdRepository.getUserHouseholdId(uid) ?: return@launch

            // Load pending SMS
            launch {
                pendingSmsDao.getPending().collect { pending ->
                    _uiState.update { it.copy(pendingSms = pending) }
                }
            }

            // Load categories
            launch {
                categoryRepository.getCategories(householdId).collect { categories ->
                    _uiState.update { it.copy(categories = categories) }
                }
            }

            // Load SMS stats
            launch {
                val total = processedSmsDao.getCount()
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val thisMonth = processedSmsDao.getCountSince(cal.timeInMillis)
                _uiState.update { it.copy(smsStats = SmsStats(total, thisMonth)) }
            }

            // Load SMS history with expense details
            launch {
                processedSmsDao.getAll().collect { smsList ->
                    val historyItems = smsList.mapNotNull { sms ->
                        val expense = expenseRepository.getExpenseById(sms.expenseId)
                        if (expense != null) {
                            SmsHistoryItem(
                                expenseId = sms.expenseId,
                                amount = expense.amount,
                                categoryName = expense.categoryName,
                                notes = expense.notes,
                                date = expense.date,
                                processedAt = sms.processedAt
                            )
                        } else null
                    }
                    _uiState.update { it.copy(smsHistory = historyItems, isLoading = false) }
                }
            }
        }
    }

    fun confirmPendingSms(pending: PendingSmsEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val expense = Expense(
                id = UUID.randomUUID().toString(),
                householdId = pending.householdId,
                amount = pending.amount,
                categoryId = pending.categoryId,
                categoryName = pending.categoryName,
                date = pending.receivedTimestamp,
                notes = buildNotes(pending),
                addedBy = pending.userId,
                addedByName = pending.userName,
                createdAt = now,
                updatedAt = now
            )
            val result = expenseRepository.addExpense(expense)
            if (result.isSuccess) {
                processedSmsDao.insert(
                    ProcessedSmsEntity(pending.smsHash, expense.id, now)
                )
                pendingSmsDao.updateStatus(pending.id, PendingSmsEntity.STATUS_CONFIRMED)
                notificationHelper.cancelNotification(pending.notificationId)
            }
        }
    }

    fun updatePendingSmsCategory(pendingId: String, category: Category) {
        viewModelScope.launch {
            pendingSmsDao.updateCategory(pendingId, category.id, category.name)
        }
    }

    fun dismissPendingSms(pending: PendingSmsEntity) {
        viewModelScope.launch {
            pendingSmsDao.updateStatus(pending.id, PendingSmsEntity.STATUS_DISMISSED)
            notificationHelper.cancelNotification(pending.notificationId)
        }
    }

    private fun buildNotes(pending: PendingSmsEntity): String {
        return buildString {
            append("SMS Import")
            pending.merchant?.let { append(": $it") }
            pending.cardOrAccount?.let { append(" (XX$it)") }
            append(" [${pending.sender}]")
        }
    }
}
