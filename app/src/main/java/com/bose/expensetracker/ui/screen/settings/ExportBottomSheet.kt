package com.bose.expensetracker.ui.screen.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat { CSV, PDF }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    sheetState: SheetState,
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onExport: (format: ExportFormat, startDate: Long?, endDate: Long?) -> Unit
) {
    var selectedFormat by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Export Expenses",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Format selector
            Text("Format", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedFormat == 0,
                    onClick = { selectedFormat = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("CSV") }
                SegmentedButton(
                    selected = selectedFormat == 1,
                    onClick = { selectedFormat = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("PDF") }
            }

            // Date range
            Text("Date Range (Optional)", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (startDate != null && endDate != null) {
                            "${dateFormat.format(Date(startDate!!))} - ${dateFormat.format(Date(endDate!!))}"
                        } else {
                            "All Time"
                        }
                    )
                }
                if (startDate != null) {
                    TextButton(onClick = { startDate = null; endDate = null }) {
                        Text("Clear")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Export button
            Button(
                onClick = {
                    val format = if (selectedFormat == 0) ExportFormat.CSV else ExportFormat.PDF
                    onExport(format, startDate, endDate)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.height(20.dp)
                    )
                } else {
                    Text("Export & Share")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = dateRangePickerState.selectedStartDateMillis
                    endDate = dateRangePickerState.selectedEndDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.height(400.dp)
            )
        }
    }
}

fun shareExportedFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val mimeType = if (file.extension == "pdf") "application/pdf" else "text/csv"
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Expense Report"))
}
