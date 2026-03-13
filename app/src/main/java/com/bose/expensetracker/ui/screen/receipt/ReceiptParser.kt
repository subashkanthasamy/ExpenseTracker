package com.bose.expensetracker.ui.screen.receipt

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class ReceiptResult(
    val rawText: String,
    val amount: Double?,
    val date: Long?,
    val merchant: String?
)

@Singleton
class ReceiptParser @Inject constructor() {

    suspend fun parseReceipt(context: Context, imageUri: Uri): ReceiptResult {
        val image = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()

        return ReceiptResult(
            rawText = result.text,
            amount = extractAmount(result.text),
            date = extractDate(result.text),
            merchant = extractMerchant(result.text)
        )
    }

    private fun extractAmount(text: String): Double? {
        // Prioritize lines containing "total"
        val lines = text.lines()
        for (line in lines) {
            val lowerLine = line.lowercase()
            if (lowerLine.contains("total") || lowerLine.contains("amount") || lowerLine.contains("grand total") || lowerLine.contains("balance due")) {
                val amountRegex = Regex("\\$?([\\d,]+\\.\\d{2})")
                val match = amountRegex.find(line)
                if (match != null) {
                    return match.groupValues[1].replace(",", "").toDoubleOrNull()
                }
            }
        }

        // Fallback: largest dollar amount
        val amountRegex = Regex("\\$?([\\d,]+\\.\\d{2})")
        return amountRegex.findAll(text)
            .mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }
            .maxOrNull()
    }

    private fun extractDate(text: String): Long? {
        val dateRegex = Regex("(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})")
        val match = dateRegex.find(text) ?: return null
        return try {
            val dateStr = match.groupValues[1]
            val formats = listOf(
                java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US),
                java.text.SimpleDateFormat("MM/dd/yy", java.util.Locale.US),
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US),
                java.text.SimpleDateFormat("MM-dd-yyyy", java.util.Locale.US)
            )
            formats.firstNotNullOfOrNull { format ->
                try {
                    format.isLenient = false
                    format.parse(dateStr)?.time
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractMerchant(text: String): String? {
        val lines = text.lines().filter { it.isNotBlank() }
        return lines.firstOrNull()?.trim()
    }
}
