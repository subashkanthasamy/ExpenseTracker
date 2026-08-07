package com.bose.expensetracker.domain.usecase.insights

import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.ui.state.PersonSpending

/**
 * Splits a period's spending across the household members who recorded it.
 *
 * Lives in `shared` so both platforms compute the same split — Android previously did this
 * inline as a comma-joined string built with `String.format`, which is unavailable in
 * Kotlin/Native and so could never have been reused on iOS.
 */
object SpendingSplitCalculator {

    /**
     * One entry per member with a recorded expense, largest share first.
     *
     * Returns empty for an empty or zero-total list — a split of nothing is not information,
     * and it keeps callers from dividing by zero to get the share.
     */
    fun split(expenses: List<Expense>): List<PersonSpending> {
        val total = expenses.sumOf { it.amount }
        if (total <= 0.0) return emptyList()

        return expenses
            .groupBy { it.addedBy }
            .map { (userId, rows) ->
                val amount = rows.sumOf { it.amount }
                PersonSpending(
                    userId = userId,
                    // Older rows can carry a blank name; prefer any populated one for the member.
                    name = rows.firstOrNull { it.addedByName.isNotBlank() }?.addedByName
                        ?: "Unknown",
                    amount = amount,
                    share = amount / total
                )
            }
            .sortedByDescending { it.amount }
    }
}
