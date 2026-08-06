package com.bose.expensetracker.ui.screen.receipt

import android.content.Context
import android.net.Uri
import com.bose.expensetracker.domain.usecase.receipt.ReceiptTextParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// ReceiptResult and the amount/date/merchant heuristics now live in the shared module
// (domain/usecase/receipt/ReceiptTextParser) so iOS runs exactly the same logic. Only the
// OCR step is platform specific — ML Kit here, Vision on iOS.
typealias ReceiptResult = com.bose.expensetracker.domain.usecase.receipt.ReceiptResult

@Singleton
class ReceiptParser @Inject constructor() {

    suspend fun parseReceipt(context: Context, imageUri: Uri): ReceiptResult {
        val image = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()
        return ReceiptTextParser.parse(result.text)
    }
}
