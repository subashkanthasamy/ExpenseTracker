package com.bose.expensetracker.domain.model

/**
 * How an expense was paid.
 *
 * A record of the instrument, not a payment integration — this app has no commerce. Every
 * method below is treated as an immediate outflow on the transaction date, including
 * [CREDIT_CARD]; see the note there.
 *
 * [wire] is what goes to Firestore. It is deliberately not `name`, so renaming an enum
 * constant cannot silently orphan every stored row.
 */
enum class PaymentMethod(val label: String, val wire: String, val emoji: String) {
    /**
     * No method recorded.
     *
     * Every expense written before this feature existed decodes to this. It is never
     * something the user picks — defaulting those rows to [CASH] would invent data and make
     * the spending breakdown lie about money that may well have moved by card.
     */
    UNSPECIFIED("Not recorded", "", "•"),
    CASH("Cash", "cash", "💵"),
    UPI("UPI", "upi", "📱"),
    CREDIT_CARD("Credit card", "credit_card", "💳");

    companion object {
        /** The methods a user may choose, in picker order. [UNSPECIFIED] is not among them. */
        val selectable: List<PaymentMethod> = listOf(UPI, CASH, CREDIT_CARD)

        /**
         * Decodes a stored value.
         *
         * Anything unrecognised — a blank, a missing field, or a method added by a newer
         * build — becomes [UNSPECIFIED] rather than throwing, so an older client can still
         * read a household that a newer one has written to.
         */
        fun fromWire(value: String?): PaymentMethod =
            entries.firstOrNull { it.wire.isNotEmpty() && it.wire == value } ?: UNSPECIFIED
    }
}
