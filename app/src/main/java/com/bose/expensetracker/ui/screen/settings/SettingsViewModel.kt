package com.bose.expensetracker.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.data.preferences.BiometricPreferences
import com.bose.expensetracker.data.preferences.SmsImportPreferences
import com.bose.expensetracker.data.preferences.ThemePreferences
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.User
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.bose.expensetracker.domain.usecase.export.ExportExpensesUseCase
import com.bose.expensetracker.domain.usecase.importdata.ImportExpensesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    val user: User? = null,
    val household: Household? = null,
    val households: List<Household> = emptyList(),
    val members: List<User> = emptyList(),
    val biometricEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val dummyDataMessage: String? = null,
    val isResetting: Boolean = false,
    val resetMessage: String? = null,
    val smsImportEnabled: Boolean = false,
    val themeMode: Int = ThemePreferences.THEME_SYSTEM,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val exportExpensesUseCase: ExportExpensesUseCase,
    private val importExpensesUseCase: ImportExpensesUseCase,
    private val biometricPreferences: BiometricPreferences,
    private val smsImportPreferences: SmsImportPreferences,
    private val themePreferences: ThemePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _signOutEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signOutEvent: SharedFlow<Unit> = _signOutEvent.asSharedFlow()

    private val _householdSwitchedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val householdSwitchedEvent: SharedFlow<Unit> = _householdSwitchedEvent.asSharedFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@collect
                }
                _uiState.update { it.copy(user = user) }
                try {
                    loadHouseholdData(user.uid)
                } catch (e: Exception) {
                    android.util.Log.e("SettingsVM", "loadHouseholdData failed: ${e.message}", e)
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load household data") }
                }
            }
        }
        // Load biometric preference in a separate coroutine to avoid blocking the outer collect
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            biometricPreferences.isBiometricEnabled(uid).collect { enabled ->
                _uiState.update { it.copy(biometricEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            smsImportPreferences.isSmsImportEnabled(uid).collect { enabled ->
                _uiState.update { it.copy(smsImportEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            themePreferences.getThemeMode().collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    private suspend fun loadHouseholdData(uid: String) {
        val hId = householdRepository.getUserHouseholdId(uid)
        android.util.Log.d("SettingsVM", "loadHouseholdData: uid=$uid, activeHouseholdId=$hId")
        if (hId == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        // Load all households for switching
        householdRepository.getUserHouseholds(uid).onSuccess { allHouseholds ->
            android.util.Log.d("SettingsVM", "loadHouseholdData: ${allHouseholds.size} households loaded")
            _uiState.update { it.copy(households = allHouseholds) }
        }.onFailure { error ->
            android.util.Log.e("SettingsVM", "loadHouseholdData: getUserHouseholds failed: ${error.message}")
        }

        householdRepository.getHousehold(hId).onSuccess { household ->
            android.util.Log.d("SettingsVM", "loadHouseholdData: active household=${household.name}")
            _uiState.update { it.copy(household = household) }
        }.onFailure { error ->
            android.util.Log.e("SettingsVM", "loadHouseholdData: getHousehold failed: ${error.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
        householdRepository.getHouseholdMembers(hId).onSuccess { members ->
            _uiState.update { it.copy(members = members, isLoading = false) }
        }.onFailure {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _signOutEvent.emit(Unit)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            biometricPreferences.setBiometricEnabled(uid, enabled)
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun setSmsImportEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            smsImportPreferences.setSmsImportEnabled(uid, enabled)
        }
    }

    fun switchHousehold(householdId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val uid = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(errorMessage = "Not signed in") }
                return@launch
            }
            android.util.Log.d("SettingsVM", "switchHousehold: uid=$uid, target=$householdId")
            householdRepository.setActiveHousehold(uid, householdId).onSuccess {
                android.util.Log.d("SettingsVM", "switchHousehold: success, navigating")
                _householdSwitchedEvent.emit(Unit)
            }.onFailure { error ->
                android.util.Log.e("SettingsVM", "switchHousehold failed: ${error.message}", error)
                _uiState.update { it.copy(errorMessage = "Switch failed: ${error.message}") }
            }
        }
    }

    fun createNewHousehold(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val uid = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(errorMessage = "Not signed in") }
                return@launch
            }
            android.util.Log.d("SettingsVM", "createNewHousehold: uid=$uid, name=$name")
            householdRepository.createHousehold(name, uid).onSuccess { household ->
                android.util.Log.d("SettingsVM", "createNewHousehold: success, id=${household.id}")
                categoryRepository.seedPresetCategories(household.id)
                _householdSwitchedEvent.emit(Unit)
            }.onFailure { error ->
                android.util.Log.e("SettingsVM", "createNewHousehold failed: ${error.message}", error)
                _uiState.update { it.copy(errorMessage = "Create failed: ${error.message}") }
            }
        }
    }

    fun joinNewHousehold(inviteCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val uid = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(errorMessage = "Not signed in") }
                return@launch
            }
            android.util.Log.d("SettingsVM", "joinNewHousehold: uid=$uid, code=$inviteCode")
            householdRepository.joinHousehold(inviteCode, uid).onSuccess {
                android.util.Log.d("SettingsVM", "joinNewHousehold: success")
                _householdSwitchedEvent.emit(Unit)
            }.onFailure { error ->
                android.util.Log.e("SettingsVM", "joinNewHousehold failed: ${error.message}", error)
                _uiState.update { it.copy(errorMessage = "Join failed: ${error.message}") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private val _exportedFile = MutableSharedFlow<File>()
    val exportedFile: SharedFlow<File> = _exportedFile.asSharedFlow()

    fun exportExpenses(format: ExportFormat, startDate: Long?, endDate: Long?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val uid = authRepository.getCurrentUserId() ?: return@launch
                val hId = householdRepository.getUserHouseholdId(uid) ?: return@launch
                val file = when (format) {
                    ExportFormat.CSV -> exportExpensesUseCase.exportCsv(hId, startDate, endDate)
                    ExportFormat.PDF -> exportExpensesUseCase.exportPdf(hId, startDate, endDate)
                }
                _exportedFile.emit(file)
            } catch (e: Exception) {
                _uiState.update { it.copy(dummyDataMessage = "Export failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun importExpenses(inputStream: InputStream) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importMessage = null) }
            try {
                val uid = authRepository.getCurrentUserId() ?: return@launch
                val hId = householdRepository.getUserHouseholdId(uid) ?: return@launch
                val userName = authRepository.getCurrentUserDisplayName() ?: "User"
                val result = importExpensesUseCase.importCsv(inputStream, hId, uid, userName)
                val message = if (result.errors.isNotEmpty() && result.importedCount == 0) {
                    "Import failed: ${result.errors.first()}"
                } else {
                    buildString {
                        append("Imported ${result.importedCount} of ${result.totalRows} expenses.")
                        if (result.skippedCount > 0) append("\n${result.skippedCount} rows skipped.")
                    }
                }
                _uiState.update { it.copy(importMessage = message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(importMessage = "Import failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    fun clearImportMessage() {
        _uiState.update { it.copy(importMessage = null) }
    }

    fun resetAllExpenses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isResetting = true) }
            try {
                val uid = authRepository.getCurrentUserId()
                if (uid == null) {
                    _uiState.update { it.copy(resetMessage = "Not signed in") }
                    return@launch
                }
                val hId = householdRepository.getUserHouseholdId(uid)
                if (hId == null) {
                    _uiState.update { it.copy(resetMessage = "No active household") }
                    return@launch
                }
                android.util.Log.d("SettingsVM", "resetAllExpenses: uid=$uid, hId=$hId")
                expenseRepository.deleteAllExpenses(hId).getOrThrow()
                _uiState.update { it.copy(resetMessage = "All expenses have been deleted.") }
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "resetAllExpenses failed", e)
                _uiState.update { it.copy(resetMessage = "Failed to reset expenses: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isResetting = false) }
            }
        }
    }

    fun clearResetMessage() {
        _uiState.update { it.copy(resetMessage = null) }
    }

    fun populateDummyData() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            val hId = householdRepository.getUserHouseholdId(uid) ?: return@launch
            val userName = authRepository.getCurrentUserDisplayName() ?: "User"

            // Ensure categories exist
            val categories = categoryRepository.getCategories(hId).firstOrNull() ?: emptyList()
            if (categories.isEmpty()) {
                categoryRepository.seedPresetCategories(hId)
            }
            val cats = categoryRepository.getCategories(hId).firstOrNull() ?: emptyList()
            val catMap = cats.associateBy { it.name }

            // Helper to get a date for this month
            fun dateOfMonth(day: Int): Long {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, day)
                cal.set(Calendar.HOUR_OF_DAY, 10)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                return cal.timeInMillis
            }

            // Helper to get a date for last month
            fun dateLastMonth(day: Int): Long {
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, day)
                cal.set(Calendar.HOUR_OF_DAY, 10)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                return cal.timeInMillis
            }

            data class DummyExpense(
                val amount: Double,
                val categoryName: String,
                val description: String,
                val day: Int,
                val isLastMonth: Boolean = false
            )

            val dummyExpenses = listOf(
                // This month expenses
                DummyExpense(250.0, "Food", "Lunch at restaurant", 1),
                DummyExpense(180.0, "Food", "Dinner with family", 3),
                DummyExpense(120.0, "Food", "Breakfast café", 7),
                DummyExpense(350.0, "Food", "Weekend dinner", 10),
                DummyExpense(1500.0, "Groceries", "Weekly groceries", 2),
                DummyExpense(800.0, "Groceries", "Vegetables & fruits", 5),
                DummyExpense(2200.0, "Groceries", "Monthly provisions", 8),
                DummyExpense(500.0, "Transport", "Petrol", 1),
                DummyExpense(1200.0, "Transport", "Petrol refill", 6),
                DummyExpense(150.0, "Transport", "Auto rickshaw", 9),
                DummyExpense(15000.0, "Rent/Home Loan", "Monthly EMI", 5),
                DummyExpense(2500.0, "Bills", "Electricity bill", 3),
                DummyExpense(999.0, "Bills", "Internet bill", 4),
                DummyExpense(499.0, "Bills", "Mobile recharge", 1),
                DummyExpense(3000.0, "Family", "Kids school supplies", 2),
                DummyExpense(1500.0, "Family", "Family outing", 8),
                DummyExpense(500.0, "Entertainment", "Movie tickets", 6),
                DummyExpense(299.0, "Entertainment", "Netflix subscription", 1),
                DummyExpense(750.0, "Entertainment", "Concert tickets", 9),
                DummyExpense(200.0, "Misc", "Stationery", 4),
                DummyExpense(350.0, "Misc", "Gift for friend", 7),
                // Last month expenses
                DummyExpense(300.0, "Food", "Restaurant", 5, true),
                DummyExpense(200.0, "Food", "Street food", 12, true),
                DummyExpense(1800.0, "Groceries", "Monthly groceries", 3, true),
                DummyExpense(900.0, "Groceries", "Fruits & veggies", 10, true),
                DummyExpense(1500.0, "Transport", "Petrol", 1, true),
                DummyExpense(15000.0, "Rent/Home Loan", "Monthly EMI", 5, true),
                DummyExpense(2200.0, "Bills", "Electricity bill", 4, true),
                DummyExpense(999.0, "Bills", "Internet bill", 4, true),
                DummyExpense(2000.0, "Family", "Weekend trip", 15, true),
                DummyExpense(400.0, "Entertainment", "Movie + popcorn", 8, true),
                DummyExpense(500.0, "Misc", "Household items", 6, true),
            )

            val now = System.currentTimeMillis()
            var inserted = 0

            dummyExpenses.forEach { dummy ->
                val cat = catMap[dummy.categoryName]
                val date = if (dummy.isLastMonth) dateLastMonth(dummy.day) else dateOfMonth(dummy.day)
                val expense = Expense(
                    id = UUID.randomUUID().toString(),
                    householdId = hId,
                    amount = dummy.amount,
                    categoryId = cat?.id ?: "",
                    categoryName = dummy.categoryName,
                    date = date,
                    notes = dummy.description,
                    addedBy = uid,
                    addedByName = userName,
                    createdAt = now,
                    updatedAt = now
                )
                expenseRepository.addExpense(expense)
                inserted++
            }

            _uiState.update { it.copy(dummyDataMessage = "$inserted dummy expenses added!") }
        }
    }

    fun seedCategories() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId()
            android.util.Log.d("SettingsVM", "seedCategories: uid=$uid")
            if (uid == null) {
                _uiState.update { it.copy(dummyDataMessage = "Error: Not signed in") }
                return@launch
            }

            var hId: String? = null
            for (attempt in 1..3) {
                hId = householdRepository.getUserHouseholdId(uid)
                android.util.Log.d("SettingsVM", "seedCategories: attempt=$attempt, householdId=$hId")
                if (hId != null) break
                if (attempt < 3) kotlinx.coroutines.delay(1000L)
            }

            if (hId == null) {
                _uiState.update { it.copy(dummyDataMessage = "Error: No household found. Create or join a household first.") }
                return@launch
            }

            // Force seed categories (both Room + Firestore)
            categoryRepository.seedPresetCategories(hId)

            val cats = categoryRepository.getCategories(hId).firstOrNull() ?: emptyList()
            android.util.Log.d("SettingsVM", "seedCategories: ${cats.size} categories after seeding: ${cats.map { it.name }}")
            _uiState.update { it.copy(dummyDataMessage = "${cats.size} categories seeded to Firebase!") }
        }
    }

    fun clearDummyDataMessage() {
        _uiState.update { it.copy(dummyDataMessage = null) }
    }
}
