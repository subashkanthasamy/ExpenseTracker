package com.bose.expensetracker.ui.screen.receipt

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScannerUiState(
    val capturedImageUri: Uri? = null,
    val receiptResult: ReceiptResult? = null,
    val isProcessing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReceiptScannerViewModel @Inject constructor(
    private val receiptParser: ReceiptParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onImageCaptured(uri: Uri, context: android.content.Context) {
        _uiState.update { it.copy(capturedImageUri = uri, isProcessing = true) }
        viewModelScope.launch {
            try {
                val result = receiptParser.parseReceipt(context, uri)
                _uiState.update { it.copy(receiptResult = result, isProcessing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isProcessing = false) }
            }
        }
    }

    fun reset() {
        _uiState.value = ScannerUiState()
    }
}
