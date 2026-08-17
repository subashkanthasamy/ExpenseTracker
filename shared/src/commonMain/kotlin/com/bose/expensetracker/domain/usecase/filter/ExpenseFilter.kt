package com.bose.expensetracker.domain.usecase.filter

import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.ui.state.DateRangeFilter
import com.bose.expensetracker.ui.state.ExpenseFilterCriteria
import com.bose.expensetracker.ui.state.FilterOption
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Narrows an expense list by search text, category, person and date window.
 *
 * This lives in `shared` for the same reason [com.bose.expensetracker.domain.usecase.recurring.RecurringScheduleCalculator]
 * does: both platforms show the same list from the same Firestore data, so two
 * implementations of "which rows match" would drift and the same filter would give different
 * answers on Android and iOS. Android used to own the only copy of this predicate and iOS a
 * narrower one that searched fewer fields.
 *
 * [nowMillis] is a parameter rather than a `Clock` read so date windows are testable and so a
 * single list render can't straddle midnight.
 */
object ExpenseFilter {

    /**
     * [expenses] narrowed to those matching [criteria], using the device's time zone.
     *
     * The zone-taking overload below exists for tests. These are separate arities rather than
     * one function with a default because Kotlin default arguments are not exported to Swift,
     * and neither the iOS app nor the Android app module can name a `kotlinx.datetime.TimeZone`.
     */
    fun apply(
        expenses: List<Expense>,
        criteria: ExpenseFilterCriteria,
        nowMillis: Long
    ): List<Expense> = apply(expenses, criteria, nowMillis, TimeZone.currentSystemDefault())

    /** [expenses] narrowed to those matching [criteria]. Input order is preserved. */
    fun apply(
        expenses: List<Expense>,
        criteria: ExpenseFilterCriteria,
        nowMillis: Long,
        timeZone: TimeZone
    ): List<Expense> {
        if (!criteria.isActive) return expenses

        val query = criteria.searchQuery.trim()
        val bounds = dateBounds(criteria.dateRange, nowMillis, timeZone)

        return expenses.filter { expense ->
            matchesQuery(expense, query) &&
                (criteria.personFilter == null || expense.addedBy == criteria.personFilter) &&
                (criteria.categoryFilter == null || expense.categoryId == criteria.categoryFilter) &&
                (criteria.paymentMethodFilter == null ||
                    expense.paymentMethod == criteria.paymentMethodFilter) &&
                (bounds == null || expense.date in bounds)
        }
    }

    /**
     * Half-open window for [range] as an inclusive [LongRange] of epoch millis, or `null` for
     * [DateRangeFilter.ALL] (no constraint). Month arithmetic goes through [LocalDate] so
     * rolling back from January lands in the previous December.
     */
    fun dateBounds(
        range: DateRangeFilter,
        nowMillis: Long,
        timeZone: TimeZone
    ): LongRange? {
        if (range == DateRangeFilter.ALL) return null

        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
        val firstOfThisMonth = LocalDate(today.year, today.monthNumber, 1)

        val (startDate, endDate) = when (range) {
            DateRangeFilter.THIS_MONTH ->
                firstOfThisMonth to firstOfThisMonth.plus(DatePeriod(months = 1))

            DateRangeFilter.LAST_MONTH ->
                firstOfThisMonth.plus(DatePeriod(months = -1)) to firstOfThisMonth

            DateRangeFilter.THIS_YEAR ->
                LocalDate(today.year, 1, 1) to LocalDate(today.year + 1, 1, 1)

            DateRangeFilter.ALL -> return null
        }

        val start = startDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val end = endDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
        return start until end
    }

    /** Distinct categories present in [expenses], sorted by name. */
    fun categoryOptions(expenses: List<Expense>): List<FilterOption> =
        options(expenses) { it.categoryId to it.categoryName }

    /** Distinct people who added something in [expenses], sorted by name. */
    fun personOptions(expenses: List<Expense>): List<FilterOption> =
        options(expenses) { it.addedBy to it.addedByName }

    /**
     * Chip options are derived from the loaded rows rather than fetched, which means no extra
     * query on either platform and no chip that would select nothing.
     */
    private fun options(
        expenses: List<Expense>,
        pick: (Expense) -> Pair<String, String>
    ): List<FilterOption> = expenses
        .asSequence()
        .map(pick)
        .filter { (id, _) -> id.isNotBlank() }
        .distinctBy { (id, _) -> id }
        .map { (id, name) -> FilterOption(id = id, label = name.ifBlank { id }) }
        .sortedBy { it.label.lowercase() }
        .toList()

    private fun matchesQuery(expense: Expense, query: String): Boolean {
        if (query.isEmpty()) return true
        return expense.notes.contains(query, ignoreCase = true) ||
            expense.categoryName.contains(query, ignoreCase = true) ||
            expense.addedByName.contains(query, ignoreCase = true) ||
            // No String.format in Kotlin/Native, so match the plain toString ("450.0").
            expense.amount.toString().contains(query)
    }
}
