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
        val category = criteria.categoryFilter?.let { resolveCategory(it, expenses) }

        return expenses.filter { expense ->
            matchesQuery(expense, query) &&
                (criteria.personFilter == null || expense.addedBy == criteria.personFilter) &&
                (category == null || categoryKey(expense) == category) &&
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
    /**
     * One option per category NAME, not per id.
     *
     * The same category can carry several ids in real data: the clients seeded categories
     * differently before [com.bose.expensetracker.domain.model.CategoryPresets] unified them,
     * so a household can hold two "Food" rows with different ids. Offering one option per id
     * listed "Food" twice, each showing only part of the food spending. Rows imported from a
     * CSV whose category did not match have no id at all and were never selectable.
     *
     * The option id stays a real category id where there is one — the smallest, so it is
     * stable across renders — which keeps saved filters and `?category=<id>` links working.
     * A name that only id-less rows carry gets a [NAME_KEY] id instead.
     */
    fun categoryOptions(expenses: List<Expense>): List<FilterOption> = expenses
        .groupBy { categoryKey(it) }
        .filterKeys { it != ID_KEY }
        .map { (key, rows) ->
            val id = rows.map { it.categoryId }.filter { it.isNotBlank() }.minOrNull()
                ?: (NAME_KEY + key)
            val label = rows.firstNotNullOfOrNull { it.categoryName.trim().ifBlank { null } } ?: id
            FilterOption(id = id, label = label)
        }
        .sortedBy { it.label.lowercase() }

    /** Distinct people who added something in [expenses], sorted by name. */
    /** Prefix for an option id that names a category with no id of its own. */
    private const val NAME_KEY = "name:"

    /** Key prefix for a row with no category name, grouped by its id instead. */
    private const val ID_KEY = "id:"

    /** What makes two rows "the same category": the name, ignoring case and spacing. */
    private fun categoryKey(expense: Expense): String {
        val name = expense.categoryName.trim().lowercase()
        return if (name.isNotEmpty()) name else ID_KEY + expense.categoryId
    }

    /**
     * The category key a filter value selects. A plain id resolves through any row carrying
     * it to that row's name, so every row with the same name matches — including rows filed
     * under a different id, or none.
     */
    private fun resolveCategory(filter: String, expenses: List<Expense>): String {
        if (filter.startsWith(NAME_KEY)) return filter.removePrefix(NAME_KEY)
        return expenses.firstOrNull { it.categoryId == filter }?.let { categoryKey(it) }
            ?: (ID_KEY + filter)
    }

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
