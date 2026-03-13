package com.bose.expensetracker.domain.usecase.export

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.ExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.io.FileWriter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportExpensesUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    @ApplicationContext private val context: Context
) {

    suspend fun exportCsv(
        householdId: String,
        startDate: Long? = null,
        endDate: Long? = null,
        userId: String? = null
    ): File {
        val expenses = getFilteredExpenses(householdId, startDate, endDate, userId)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val file = File(context.cacheDir, "expenses_${System.currentTimeMillis()}.csv")

        FileWriter(file).use { writer ->
            writer.write("Date,Amount,Category,Notes,Added By\n")
            expenses.forEach { expense ->
                val date = dateFormat.format(Date(expense.date))
                val notes = expense.notes.replace("\"", "\"\"")
                writer.write("$date,${expense.amount},\"${expense.categoryName}\",\"$notes\",\"${expense.addedByName}\"\n")
            }
        }
        return file
    }

    suspend fun exportPdf(
        householdId: String,
        startDate: Long? = null,
        endDate: Long? = null,
        userId: String? = null
    ): File {
        val expenses = getFilteredExpenses(householdId, startDate, endDate, userId)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

        val document = PdfDocument()
        val pageWidth = 595 // A4
        val pageHeight = 842

        var pageNumber = 1
        var yPosition = 80f

        fun newPage(): PdfDocument.Page {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
            return document.startPage(pageInfo)
        }

        var page = newPage()
        var canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val headerPaint = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 10f }

        // Title
        canvas.drawText("Expense Report", 40f, yPosition, titlePaint)
        yPosition += 30f

        // Summary
        val total = expenses.sumOf { it.amount }
        canvas.drawText("Total: ${currencyFormat.format(total)}  |  ${expenses.size} expenses", 40f, yPosition, bodyPaint)
        yPosition += 30f

        // Header
        canvas.drawText("Date", 40f, yPosition, headerPaint)
        canvas.drawText("Category", 150f, yPosition, headerPaint)
        canvas.drawText("Amount", 300f, yPosition, headerPaint)
        canvas.drawText("Added By", 400f, yPosition, headerPaint)
        yPosition += 20f

        expenses.forEach { expense ->
            if (yPosition > pageHeight - 60) {
                document.finishPage(page)
                page = newPage()
                canvas = page.canvas
                yPosition = 60f
            }

            canvas.drawText(dateFormat.format(Date(expense.date)), 40f, yPosition, bodyPaint)
            canvas.drawText(expense.categoryName, 150f, yPosition, bodyPaint)
            canvas.drawText(currencyFormat.format(expense.amount), 300f, yPosition, bodyPaint)
            canvas.drawText(expense.addedByName, 400f, yPosition, bodyPaint)
            yPosition += 18f
        }

        document.finishPage(page)

        val file = File(context.cacheDir, "expenses_${System.currentTimeMillis()}.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()

        return file
    }

    private suspend fun getFilteredExpenses(
        householdId: String,
        startDate: Long?,
        endDate: Long?,
        userId: String?
    ): List<Expense> {
        val flow = if (startDate != null && endDate != null) {
            expenseRepository.getExpensesByDateRange(householdId, startDate, endDate)
        } else if (userId != null) {
            expenseRepository.getExpensesByUser(householdId, userId)
        } else {
            expenseRepository.getExpenses(householdId)
        }

        return flow.firstOrNull() ?: emptyList()
    }
}
