package com.bose.expensetracker.domain.usecase.importdata

import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
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

        // Validate header
        val header = reader.readLine()?.trim()
        if (header == null || header.lowercase() != "date,amount,category,notes,added by") {
            return ImportResult(0, 0, 0, listOf("Invalid CSV format. Expected header: Date,Amount,Category,Notes,Added By"))
        }

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
                    if (fields.size < 5) {
                        skippedCount++
                        errors.add("Row $totalRows: expected 5 fields, got ${fields.size}")
                        continue
                    }

                    val dateStr = fields[0].trim()
                    val amountStr = fields[1].trim()
                    val categoryName = fields[2].trim()
                    val notes = fields[3].trim()

                    val date = dateFormat.parse(dateStr)?.time
                    if (date == null) {
                        skippedCount++
                        errors.add("Row $totalRows: invalid date '$dateStr'")
                        continue
                    }

                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null) {
                        skippedCount++
                        errors.add("Row $totalRows: invalid amount '$amountStr'")
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
                        updatedAt = now
                    )

                    val result = expenseRepository.addExpense(expense)
                    if (result.isSuccess) {
                        importedCount++
                    } else {
                        skippedCount++
                        errors.add("Row $totalRows: save failed")
                    }
                } catch (e: Exception) {
                    skippedCount++
                    errors.add("Row $totalRows: ${e.message}")
                }
            }
        }

        return ImportResult(totalRows, importedCount, skippedCount, errors)
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
