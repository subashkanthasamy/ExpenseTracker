package com.bose.expensetracker.domain.usecase.importdata

import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.model.ExpenseScope
import com.bose.expensetracker.domain.model.PaymentMethod
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val totalRows: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val errors: List<String> = emptyList()
)

@Singleton
class ImportExpensesUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) {

    suspend fun importCsv(
        inputStream: InputStream,
        householdId: String,
        userId: String,
        userName: String
    ): ImportResult {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val errors = mutableListOf<String>()

        // Columns are found by header name, not position: the Android, iOS and web exports
        // each order them differently, and older Android files have no payment method.
        val header = reader.readLine()?.removePrefix("\uFEFF")
            ?.let { parseCsvLine(it) }
            ?.map { it.trim().lowercase() }
            ?: emptyList()
        fun column(name: String) = header.indexOf(name).takeIf { it >= 0 }
        val dateCol = column("date")
        val amountCol = column("amount")
        val categoryCol = column("category")
        if (dateCol == null || amountCol == null || categoryCol == null) {
            return ImportResult(0, 0, 0, listOf("This file isn't in the expected format. The first row needs Date, Amount and Category columns."))
        }
        val notesCol = column("notes")
        val methodCol = column("payment method")
        val visibilityCol = column("visibility")
        val requiredColumns = maxOf(dateCol, amountCol, categoryCol) + 1

        // Load categories for name resolution
        val categories = categoryRepository.getCategories(householdId).firstOrNull() ?: emptyList()
        val categoryMap = categories.associateBy { it.name.lowercase() }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = System.currentTimeMillis()
        var totalRows = 0
        var importedCount = 0
        var skippedCount = 0

        reader.useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                totalRows++

                try {
                    val fields = parseCsvLine(line)
                    if (fields.size < requiredColumns) {
                        skippedCount++
                        errors.add("Row $totalRows: expected at least $requiredColumns columns, found ${fields.size}")
                        continue
                    }
                    fun field(col: Int?) = col?.let { fields.getOrNull(it) }?.trim().orEmpty()

                    val dateStr = field(dateCol)
                    val amountStr = field(amountCol)
                    val categoryName = field(categoryCol)
                    val notes = field(notesCol)

                    val date = dateFormat.parse(dateStr)?.time
                    if (date == null) {
                        skippedCount++
                        errors.add("Row $totalRows: couldn't read the date '$dateStr'")
                        continue
                    }

                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null) {
                        skippedCount++
                        errors.add("Row $totalRows: couldn't read the amount '$amountStr'")
                        continue
                    }

                    val category = categoryMap[categoryName.lowercase()]

                    val expense = Expense(
                        id = UUID.randomUUID().toString(),
                        householdId = householdId,
                        amount = amount,
                        categoryId = category?.id ?: "",
                        categoryName = categoryName,
                        date = date,
                        notes = notes,
                        addedBy = userId,
                        addedByName = userName,
                        createdAt = now,
                        updatedAt = now,
                        paymentMethod = parsePaymentMethod(field(methodCol)),
                        scope = if (field(visibilityCol).equals("personal", ignoreCase = true)) {
                            ExpenseScope.PERSONAL
                        } else {
                            ExpenseScope.SHARED
                        }
                    )

                    val result = expenseRepository.addExpense(expense)
                    if (result.isSuccess) {
                        importedCount++
                    } else {
                        skippedCount++
                        errors.add("Row $totalRows: couldn't save this expense")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    skippedCount++
                    errors.add("Row $totalRows: ${e.message}")
                }
            }
        }

        return ImportResult(totalRows, importedCount, skippedCount, errors)
    }

    /** Accepts the label ("Credit card") or the wire value ("credit_card"), any case. */
    private fun parsePaymentMethod(value: String): PaymentMethod {
        if (value.isEmpty()) return PaymentMethod.UNSPECIFIED
        return PaymentMethod.entries.firstOrNull {
            it.wire.isNotEmpty() &&
                (it.wire.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true))
        } ?: PaymentMethod.UNSPECIFIED
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var i = 0
        while (i < line.length) {
            if (line[i] == '"') {
                val sb = StringBuilder()
                i++ // skip opening quote
                while (i < line.length) {
                    if (line[i] == '"' && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i += 2
                    } else if (line[i] == '"') {
                        i++ // skip closing quote
                        break
                    } else {
                        sb.append(line[i])
                        i++
                    }
                }
                fields.add(sb.toString())
                if (i < line.length && line[i] == ',') i++
            } else {
                val next = line.indexOf(',', i)
                if (next == -1) {
                    fields.add(line.substring(i))
                    break
                } else {
                    fields.add(line.substring(i, next))
                    i = next + 1
                }
            }
        }
        return fields
    }
}
