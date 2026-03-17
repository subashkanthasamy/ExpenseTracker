package com.bose.expensetracker.ui.screen.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.data.local.dao.ReminderDao
import com.bose.expensetracker.data.local.entity.ReminderEntity
import com.bose.expensetracker.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ReminderUiState(
    val reminders: List<ReminderEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val reminderDao: ReminderDao,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderUiState())
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    init {
        loadReminders()
    }

    private fun loadReminders() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            reminderDao.getReminders(uid).collect { reminders ->
                _uiState.update { it.copy(reminders = reminders, isLoading = false) }
            }
        }
    }

    fun addDailyReminder(hour: Int, minute: Int) {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            val existing = reminderDao.getReminderByType(uid, ReminderEntity.TYPE_DAILY)
            val entity = ReminderEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                userId = uid,
                type = ReminderEntity.TYPE_DAILY,
                title = "Daily Expense Reminder",
                hour = hour,
                minute = minute,
                isEnabled = true
            )
            reminderDao.insert(entity)
        }
    }

    fun addBudgetReminder(limit: Double) {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            val existing = reminderDao.getReminderByType(uid, ReminderEntity.TYPE_BUDGET)
            val entity = ReminderEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                userId = uid,
                type = ReminderEntity.TYPE_BUDGET,
                title = "Budget Alert",
                amount = limit,
                isEnabled = true
            )
            reminderDao.insert(entity)
        }
    }

    fun addBillReminder(title: String, amount: Double, dueDay: Int, repeat: Int) {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            val entity = ReminderEntity(
                id = UUID.randomUUID().toString(),
                userId = uid,
                type = ReminderEntity.TYPE_BILL,
                title = title,
                amount = amount,
                dueDay = dueDay,
                repeatInterval = repeat,
                isEnabled = true
            )
            reminderDao.insert(entity)
        }
    }

    fun toggleReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderDao.update(reminder.copy(isEnabled = !reminder.isEnabled))
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            reminderDao.deleteById(id)
        }
    }
}
