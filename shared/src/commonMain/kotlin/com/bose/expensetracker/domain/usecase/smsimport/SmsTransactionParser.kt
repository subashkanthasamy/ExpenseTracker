package com.bose.expensetracker.domain.usecase.smsimport

data class ParsedTransaction(
    val amount: Double,
    val merchant: String?,
    val transactionType: TransactionType,
    val cardOrAccount: String?,
    val rawMessage: String
)

enum class TransactionType { DEBIT, CREDIT }

class SmsTransactionParser {

    private val amountPattern = Regex(
        """(?:Rs\.?|INR|₹)\s*(\d[\d,]*\.?\d*)""",
        RegexOption.IGNORE_CASE
    )

    private val creditKeywords = listOf(
        "credited", "received", "refund", "cashback", "reversed"
    )

    private val debitKeywords = listOf(
        "debited", "spent", "paid", "charged", "withdrawn", "purchase", "txn", "transaction", "sent", "transferred"
    )

    private val merchantPatterns = listOf(
        Regex("""(?:at|towards)\s+(.+?)(?:\s+on|\s+via|\s+ref|\s+UPI|\.\s|$)""", RegexOption.IGNORE_CASE),
        Regex("""^To\s+(.+?)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
        Regex("""(?:at|to)\s+(.+?)(?:\s+on|\s+\d|$)""", RegexOption.IGNORE_CASE)
    )

    private val accountPattern = Regex(
        """(?:a/c|ac|acct|card|account)\s*(?:no\.?\s*)?(?:xx|XX|x|X|\*+)(\d{4,})""",
        RegexOption.IGNORE_CASE
    )

    fun parse(sender: String, body: String, receivedTimestamp: Long): ParsedTransaction? {
        if (!isTransactionalSms(sender, body)) return null

        val amount = extractAmount(body) ?: return null
        val type = determineTransactionType(body)
        if (type == TransactionType.CREDIT) return null

        val merchant = extractMerchant(body)
        val account = accountPattern.find(body)?.groupValues?.get(1)

        return ParsedTransaction(
            amount = amount,
            merchant = merchant?.trim()?.take(50),
            transactionType = type,
            cardOrAccount = account,
            rawMessage = body
        )
    }

    private fun isTransactionalSms(sender: String, body: String): Boolean {
        val lowerBody = body.lowercase()
        val hasAmount = amountPattern.containsMatchIn(body)
        val hasKeyword = debitKeywords.any { lowerBody.contains(it) } ||
                creditKeywords.any { lowerBody.contains(it) }
        return hasAmount && hasKeyword
    }

    private fun extractAmount(body: String): Double? {
        val match = amountPattern.find(body) ?: return null
        val amountStr = match.groupValues[1].replace(",", "")
        return amountStr.toDoubleOrNull()?.takeIf { it > 0 }
    }

    private fun determineTransactionType(body: String): TransactionType {
        val lowerBody = body.lowercase()
        val creditScore = creditKeywords.count { lowerBody.contains(it) }
        val debitScore = debitKeywords.count { lowerBody.contains(it) }
        return if (creditScore > debitScore) TransactionType.CREDIT else TransactionType.DEBIT
    }

    private fun extractMerchant(body: String): String? {
        for (pattern in merchantPatterns) {
            val match = pattern.find(body)
            if (match != null) {
                val merchant = match.groupValues[1].trim()
                if (merchant.length in 2..50) return merchant
            }
        }
        return null
    }
}
