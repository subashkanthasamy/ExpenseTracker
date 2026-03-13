package com.bose.expensetracker.ui.screen.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bose.expensetracker.domain.model.Asset
import com.bose.expensetracker.domain.model.Liability
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.bose.expensetracker.domain.repository.NetWorthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class NetWorthHistoryEntry(
    val label: String,
    val value: Double
)

data class NetWorthUiState(
    val totalAssets: Double = 0.0,
    val totalLiabilities: Double = 0.0,
    val netWorth: Double = 0.0,
    val assets: List<Asset> = emptyList(),
    val liabilities: List<Liability> = emptyList(),
    val history: List<NetWorthHistoryEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class NetWorthViewModel @Inject constructor(
    private val netWorthRepository: NetWorthRepository,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetWorthUiState())
    val uiState: StateFlow<NetWorthUiState> = _uiState.asStateFlow()

    private var householdId: String? = null

    init {
        loadNetWorth()
    }

    private fun loadNetWorth() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            householdId = householdRepository.getUserHouseholdId(userId)
            val hId = householdId ?: return@launch

            netWorthRepository.startRealtimeSync(hId)

            combine(
                netWorthRepository.getAssets(hId),
                netWorthRepository.getLiabilities(hId)
            ) { assets, liabilities ->
                val totalAssets = assets.sumOf { it.value }
                val totalLiabilities = liabilities.sumOf { it.amount }
                val netWorth = totalAssets - totalLiabilities

                // Build simple history from asset/liability dates
                val history = buildNetWorthHistory(assets, liabilities)

                NetWorthUiState(
                    totalAssets = totalAssets,
                    totalLiabilities = totalLiabilities,
                    netWorth = netWorth,
                    assets = assets,
                    liabilities = liabilities,
                    history = history,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun addAsset(name: String, value: Double, type: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            val hId = householdId ?: return@launch
            val asset = Asset(
                id = UUID.randomUUID().toString(),
                householdId = hId,
                name = name,
                value = value,
                type = type,
                date = System.currentTimeMillis(),
                addedBy = userId
            )
            netWorthRepository.addAsset(asset)
        }
    }

    fun addLiability(name: String, amount: Double, type: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            val hId = householdId ?: return@launch
            val liability = Liability(
                id = UUID.randomUUID().toString(),
                householdId = hId,
                name = name,
                amount = amount,
                type = type,
                date = System.currentTimeMillis(),
                addedBy = userId
            )
            netWorthRepository.addLiability(liability)
        }
    }

    fun deleteAsset(id: String) {
        viewModelScope.launch { netWorthRepository.deleteAsset(id) }
    }

    fun deleteLiability(id: String) {
        viewModelScope.launch { netWorthRepository.deleteLiability(id) }
    }

    private fun buildNetWorthHistory(
        assets: List<Asset>,
        liabilities: List<Liability>
    ): List<NetWorthHistoryEntry> {
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val entries = mutableListOf<NetWorthHistoryEntry>()

        // Show last 6 months as cumulative snapshots
        for (i in 5 downTo 0) {
            val monthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
            val monthEnd = monthCal.timeInMillis
            val label = monthFormat.format(monthCal.time)

            val assetsUpTo = assets.filter { it.date <= monthEnd }.sumOf { it.value }
            val liabilitiesUpTo = liabilities.filter { it.date <= monthEnd }.sumOf { it.amount }
            entries.add(NetWorthHistoryEntry(label, assetsUpTo - liabilitiesUpTo))
        }
        return entries
    }

    override fun onCleared() {
        super.onCleared()
        netWorthRepository.stopRealtimeSync()
    }
}
