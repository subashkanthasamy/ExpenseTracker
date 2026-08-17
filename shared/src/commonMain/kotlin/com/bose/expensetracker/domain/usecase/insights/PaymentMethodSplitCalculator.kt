package com.bose.expensetracker.domain.usecase.insights

import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.model.PaymentMethod

/**
 * One slice of a spending breakdown.
 *
 * Deliberately not [com.bose.expensetracker.ui.state.PersonSpending], whose `userId`/`name`
 * fields mean the wrong thing here. That type could fold into this one later; it is shipped
 * and working, so it is left alone.
 */
data class SpendingSlice(
    val id: String,
    val label: String,
    val amount: Double,
    /** Fraction of the period's total, 0.0..1.0. */
    val share: Double
)

/**
 * Splits a period's spending across the instruments it was paid with.
 *
 * Mirrors [SpendingSplitCalculator] — same grouping, same share-of-total shape, same
 * zero-total guard — and lives in `shared` for the same reason: two implementations of the
 * same breakdown would drift between the platforms.
 */
object PaymentMethodSplitCalculator {

    /**
     * One entry per method actually used, largest share first.
     *
     * Expenses recorded before payment methods existed group under
     * [PaymentMethod.UNSPECIFIED] and are shown as such rather than being folded into cash —
     * the split has to be honest about what it does not know.
     */
    fun split(expenses: List<Expense>): List<SpendingSlice> {
        val total = expenses.sumOf { it.amount }
        if (total <= 0.0) return emptyList()

        return expenses
            .groupBy { it.paymentMethod }
            .map { (method, rows) ->
                val amount = rows.sumOf { it.amount }
                SpendingSlice(
                    id = method.name,
                    label = method.label,
                    amount = amount,
                    share = amount / total
                )
            }
            .sortedByDescending { it.amount }
    }
}
