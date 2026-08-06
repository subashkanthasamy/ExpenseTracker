package com.bose.expensetracker.domain.usecase.receipt

/**
 * Result of interpreting OCR text from a receipt.
 *
 * [date] is epoch millis, matching the rest of the domain, or null when no date was found.
 */
data class ReceiptResult(
    val rawText: String,
    val amount: Double? = null,
    val date: Long? = null,
    val merchant: String? = null
)

/**
 * Platform-agnostic receipt interpretation.
 *
 * Only the *text extraction* is platform specific — ML Kit on Android, Vision on iOS — so
 * the regex/heuristic half lives here and is shared. Behaviour is kept identical to the
 * original Android `ReceiptParser` so moving it changes nothing for existing users.
 */
object ReceiptTextParser {

    private val amountRegex = Regex("""\$?([\d,]+\.\d{2})""")
    private val dateRegex = Regex("""(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})""")

    private val totalKeywords = listOf("grand total", "balance due", "total", "amount")

    fun parse(text: String): ReceiptResult = ReceiptResult(
        rawText = text,
        amount = extractAmount(text),
        date = extractDate(text),
        merchant = extractMerchant(text)
    )

    /**
     * Prefers an amount on a line that looks like a total; otherwise the largest amount.
     *
     * Keywords are tried in priority order rather than taking whichever matching line comes
     * first, and "subtotal" is explicitly excluded — it contains "total" as a substring, so
     * a naive check picks the subtotal over the real total (the original Android
     * implementation had exactly that bug).
     */
    fun extractAmount(text: String): Double? {
        val lines = text.lines()
        for (keyword in totalKeywords) {
            for (line in lines) {
                val lower = line.lowercase()
                if (!lower.contains(keyword)) continue
                if (keyword == "total" && lower.contains("subtotal") && !lower.contains("grand total")) continue
                amountRegex.find(line)?.let { match ->
                    match.groupValues[1].replace(",", "").toDoubleOrNull()?.let { return it }
                }
            }
        }
        return amountRegex.findAll(text)
            .mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }
            .maxOrNull()
    }

    /**
     * Parses the first d/m/y-looking token to epoch millis (UTC midnight).
     *
     * Ambiguous values are read as **month/day** first to match the Android original, then
     * as day/month when the first component cannot be a month.
     */
    fun extractDate(text: String): Long? {
        val match = dateRegex.find(text) ?: return null
        val a = match.groupValues[1].toIntOrNull() ?: return null
        val b = match.groupValues[2].toIntOrNull() ?: return null
        val rawYear = match.groupValues[3].toIntOrNull() ?: return null

        val year = when {
            rawYear >= 1000 -> rawYear
            rawYear >= 70 -> 1900 + rawYear
            else -> 2000 + rawYear
        }

        val (month, day) = when {
            a in 1..12 && b in 1..31 -> a to b
            b in 1..12 && a in 1..31 -> b to a
            else -> return null
        }
        if (day > daysInMonth(month, year)) return null
        return utcMillis(year, month, day)
    }

    /** First non-blank line, which is where a merchant name usually sits. */
    fun extractMerchant(text: String): String? =
        text.lines().firstOrNull { it.isNotBlank() }?.trim()

    private fun daysInMonth(month: Int, year: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 0
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    /** Days-from-epoch arithmetic, since kotlinx-datetime isn't needed for this. */
    private fun utcMillis(year: Int, month: Int, day: Int): Long {
        var days = 0L
        if (year >= 1970) {
            for (y in 1970 until year) days += if (isLeapYear(y)) 366 else 365
        } else {
            for (y in year until 1970) days -= if (isLeapYear(y)) 366 else 365
        }
        for (m in 1 until month) days += daysInMonth(m, year)
        days += (day - 1)
        return days * 86_400_000L
    }
}
