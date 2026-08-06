package com.bose.expensetracker.util

data class ParsedExpense(
    val amount: Double?,
    val categoryHint: String?,
    val rawText: String
)

object VoiceExpenseParser {

    private val categoryKeywords = mapOf(
        "Food" to listOf("food", "lunch", "dinner", "breakfast", "eat", "restaurant", "meal", "snack", "cafe"),
        "Groceries" to listOf("groceries", "grocery", "vegetables", "fruits", "provisions", "supermarket"),
        "Transport" to listOf("uber", "lyft", "gas", "fuel", "bus", "taxi", "transport", "cab", "ride", "petrol", "auto"),
        "Rent/Home Loan" to listOf("rent", "emi", "home loan", "mortgage", "housing"),
        "Bills" to listOf("bill", "electricity", "water", "internet", "phone", "utility", "recharge", "mobile"),
        "Family" to listOf("family", "kids", "children", "school", "outing", "trip"),
        "Entertainment" to listOf("movie", "netflix", "game", "concert", "entertainment", "fun", "party", "tickets"),
        "Misc" to listOf("other", "misc", "miscellaneous", "shopping", "gift", "stationery")
    )

    fun parse(text: String): ParsedExpense {
        val amount = extractAmount(text)
        val categoryHint = extractCategory(text)
        return ParsedExpense(amount = amount, categoryHint = categoryHint, rawText = text)
    }

    private fun extractAmount(text: String): Double? {
        val patterns = listOf(
            Regex("(\\d+\\.?\\d*)\\s*(dollars?|bucks?|rupees?|rs\\.?)", RegexOption.IGNORE_CASE),
            Regex("\\$\\s*(\\d+\\.?\\d*)"),
            Regex("spent\\s+(\\d+\\.?\\d*)", RegexOption.IGNORE_CASE),
            Regex("(\\d+\\.?\\d*)\\s*(?:on|for)", RegexOption.IGNORE_CASE),
            Regex("(\\d+\\.?\\d*)")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val group = if (match.groupValues.size > 1) match.groupValues[1] else match.groupValues[0]
                return group.toDoubleOrNull()
            }
        }
        return null
    }

    private fun extractCategory(text: String): String? {
        val lowerText = text.lowercase()
        return categoryKeywords.entries.find { (_, keywords) ->
            keywords.any { lowerText.contains(it) }
        }?.key
    }
}
