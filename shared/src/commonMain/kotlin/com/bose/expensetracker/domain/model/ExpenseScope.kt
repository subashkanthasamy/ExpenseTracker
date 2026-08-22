package com.bose.expensetracker.domain.model

/**
 * Who an expense is visible to, and whether it counts toward the household's shared totals.
 *
 * [wire] is what goes to Firestore, and it is deliberately not `name`: renaming a constant must
 * not orphan stored rows. It is also a **required** field on the wire — the security rule tests
 * `resource.data.scope` literally, because Firestore's query prover is conservative about a
 * `.get()` with a default, and if it cannot prove a member's query is safe the whole expense
 * listener is rejected rather than filtered.
 */
enum class ExpenseScope(val label: String, val wire: String) {
    /** Everyone in the household sees it; counts toward shared totals. */
    SHARED("Shared", "shared"),

    /**
     * Visible to the author, and to the owner and admins. Excluded from shared totals.
     *
     * The point is the surprise gift: in a family the person you are hiding a purchase from is
     * in the same household, so "excluded from totals but still listed" would not do.
     */
    PERSONAL("Personal", "personal");

    companion object {
        val selectable: List<ExpenseScope> = listOf(SHARED, PERSONAL)

        /**
         * Decodes a stored value.
         *
         * Anything unrecognised — blank, missing, or written by a newer build — becomes
         * [SHARED]. Failing open matters here: defaulting to [PERSONAL] would hide a row from
         * the household because of a typo or a version skew.
         */
        fun fromWire(value: String?): ExpenseScope =
            entries.firstOrNull { it.wire == value } ?: SHARED
    }
}
