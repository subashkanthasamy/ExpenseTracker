package com.bose.expensetracker.domain.usecase.smsimport

import com.bose.expensetracker.domain.model.PaymentMethod

data class ParsedTransaction(
    val amount: Double,
    val merchant: String?,
    val transactionType: TransactionType,
    val cardOrAccount: String?,
    /** Inferred instrument, [PaymentMethod.UNSPECIFIED] when the message does not say. */
    val paymentMethod: PaymentMethod,
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
            paymentMethod = inferPaymentMethod(body),
            rawMessage = body
        )
    }

    /**
     * Which instrument the message describes, or [PaymentMethod.UNSPECIFIED].
     *
     * Guessing wrong is worse than not guessing: a mislabelled row silently distorts the
     * spending breakdown, and the user has no reason to re-check a field the import filled in.
     * So this only fires on unambiguous wording.
     *
     * Two deliberate refusals:
     *  - A bare "card" is NOT credit. Indian bank SMS use the same phrasing for debit cards,
     *    which are an immediate bank debit and behave like UPI. Only an explicit credit signal
     *    counts.
     *  - ATM and cash-withdrawal messages are NOT [PaymentMethod.CASH]. A withdrawal is not an
     *    expense at all — it moves money between your own pockets — and importing it as one
     *    double-counts when that cash is later spent. Cash stays manual-entry only.
     */
    private fun inferPaymentMethod(body: String): PaymentMethod {
        val lower = body.lowercase()

        // UPI states itself: "via UPI", "UPI Ref no", or a VPA like name@okhdfcbank.
        val looksUpi = lower.contains("upi") ||
            lower.contains("vpa") ||
            Regex("""[\w.\-]+@[a-z]{2,}""").containsMatchIn(lower)
        if (looksUpi) return PaymentMethod.UPI

        val looksCredit = lower.contains("credit card") ||
            lower.contains("creditcard") ||
            Regex("""\bcc\b""").containsMatchIn(lower)
        if (looksCredit) return PaymentMethod.CREDIT_CARD

        return PaymentMethod.UNSPECIFIED
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
