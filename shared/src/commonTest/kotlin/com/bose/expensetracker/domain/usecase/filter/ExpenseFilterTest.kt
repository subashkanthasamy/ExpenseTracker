package com.bose.expensetracker.domain.usecase.filter

import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.ui.state.DateRangeFilter
import com.bose.expensetracker.ui.state.ExpenseFilterCriteria
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseFilterTest {

    private val utc = TimeZone.UTC

    private fun millis(date: String): Long =
        LocalDate.parse(date).atStartOfDayIn(utc).toEpochMilliseconds()

    private fun expense(
        id: String,
        date: String,
        amount: Double = 100.0,
        categoryId: String = "c1",
        categoryName: String = "Food",
        notes: String = "",
        addedBy: String = "u1",
        addedByName: String = "Asha"
    ) = Expense(
        id = id,
        householdId = "h1",
        amount = amount,
        categoryId = categoryId,
        categoryName = categoryName,
        date = millis(date),
        notes = notes,
        addedBy = addedBy,
        addedByName = addedByName,
        createdAt = millis(date),
        updatedAt = millis(date)
    )

    /** 2026-03-15. Every date assertion below is relative to this. */
    private val now = millis("2026-03-15")

    private val thisMonth = expense("e1", "2026-03-15", categoryId = "c_food", categoryName = "Food")
    private val monthStart = expense("e2", "2026-03-01", categoryId = "c_rent", categoryName = "Rent")
    private val lastMonth = expense("e3", "2026-02-20", categoryId = "c_travel", categoryName = "Travel")
    private val nextMonth = expense("e4", "2026-04-01", categoryId = "c_food", categoryName = "Food")
    private val lastYear = expense("e5", "2025-12-31", categoryId = "c_gifts", categoryName = "Gifts")

    private val all = listOf(thisMonth, monthStart, lastMonth, nextMonth, lastYear)

    private fun ids(expenses: List<Expense>) = expenses.map { it.id }

    private fun filter(criteria: ExpenseFilterCriteria, expenses: List<Expense> = all) =
        ExpenseFilter.apply(expenses, criteria, now, utc)

    // --- identity ---

    @Test
    fun `empty criteria returns everything unchanged`() {
        val result = filter(ExpenseFilterCriteria())
        assertEquals(ids(all), ids(result))
    }

    @Test
    fun `empty criteria is not active`() {
        assertFalse(ExpenseFilterCriteria().isActive)
    }

    @Test
    fun `any single choice makes the criteria active`() {
        assertTrue(ExpenseFilterCriteria(searchQuery = "food").isActive)
        assertTrue(ExpenseFilterCriteria(categoryFilter = "c1").isActive)
        assertTrue(ExpenseFilterCriteria(personFilter = "u1").isActive)
        assertTrue(ExpenseFilterCriteria(dateRange = DateRangeFilter.THIS_MONTH).isActive)
    }

    @Test
    fun `a blank search query does not count as active`() {
        assertFalse(ExpenseFilterCriteria(searchQuery = "   ").isActive)
    }

    // --- search ---

    @Test
    fun `search matches category name case insensitively`() {
        val result = filter(ExpenseFilterCriteria(searchQuery = "fOoD"))
        assertEquals(listOf("e1", "e4"), ids(result))
    }

    @Test
    fun `search matches notes`() {
        val expenses = listOf(
            expense("a", "2026-03-10", notes = "Dinner with Ravi"),
            expense("b", "2026-03-11", notes = "Petrol")
        )
        val result = filter(ExpenseFilterCriteria(searchQuery = "ravi"), expenses)
        assertEquals(listOf("a"), ids(result))
    }

    @Test
    fun `search matches the member name shown on the row`() {
        val expenses = listOf(
            expense("a", "2026-03-10", addedBy = "u1", addedByName = "Asha"),
            expense("b", "2026-03-11", addedBy = "u2", addedByName = "Bala")
        )
        val result = filter(ExpenseFilterCriteria(searchQuery = "bal"), expenses)
        assertEquals(listOf("b"), ids(result))
    }

    @Test
    fun `search matches the amount`() {
        val expenses = listOf(
            expense("a", "2026-03-10", amount = 450.0),
            expense("b", "2026-03-11", amount = 90.0)
        )
        val result = filter(ExpenseFilterCriteria(searchQuery = "450"), expenses)
        assertEquals(listOf("a"), ids(result))
    }

    @Test
    fun `search is trimmed so a stray space still matches`() {
        val result = filter(ExpenseFilterCriteria(searchQuery = "  Travel  "))
        assertEquals(listOf("e3"), ids(result))
    }

    @Test
    fun `search with no match returns empty`() {
        assertTrue(filter(ExpenseFilterCriteria(searchQuery = "zzzz")).isEmpty())
    }

    // --- category and person ---

    @Test
    fun `category filter matches every row with that category name whatever its id`() {
        val expenses = listOf(
            expense("a", "2026-03-10", categoryId = "c1", categoryName = "Food"),
            expense("b", "2026-03-11", categoryId = "c2", categoryName = "food "),
            expense("c", "2026-03-12", categoryId = "", categoryName = "Food"),
            expense("d", "2026-03-13", categoryId = "c3", categoryName = "Rent")
        )
        val result = filter(ExpenseFilterCriteria(categoryFilter = "c2"), expenses)
        assertEquals(listOf("a", "b", "c"), ids(result))
    }

    @Test
    fun `person filter matches on uid`() {
        val expenses = listOf(
            expense("a", "2026-03-10", addedBy = "u1"),
            expense("b", "2026-03-11", addedBy = "u2")
        )
        val result = filter(ExpenseFilterCriteria(personFilter = "u2"), expenses)
        assertEquals(listOf("b"), ids(result))
    }

    // --- date ranges ---

    @Test
    fun `this month includes the first of the month and excludes the next month`() {
        val result = filter(ExpenseFilterCriteria(dateRange = DateRangeFilter.THIS_MONTH))
        assertEquals(listOf("e1", "e2"), ids(result))
    }

    @Test
    fun `last month covers only the previous calendar month`() {
        val result = filter(ExpenseFilterCriteria(dateRange = DateRangeFilter.LAST_MONTH))
        assertEquals(listOf("e3"), ids(result))
    }

    @Test
    fun `this year excludes December of the year before`() {
        val result = filter(ExpenseFilterCriteria(dateRange = DateRangeFilter.THIS_YEAR))
        assertEquals(listOf("e1", "e2", "e3", "e4"), ids(result))
    }

    @Test
    fun `all has no date bounds`() {
        assertNull(ExpenseFilter.dateBounds(DateRangeFilter.ALL, now, utc))
    }

    @Test
    fun `last month rolls back across a year boundary`() {
        val january = millis("2026-01-10")
        val bounds = ExpenseFilter.dateBounds(DateRangeFilter.LAST_MONTH, january, utc)!!
        assertEquals(millis("2025-12-01"), bounds.first)
        assertEquals(millis("2026-01-01") - 1, bounds.last)
    }

    @Test
    fun `this month bounds are half open`() {
        val bounds = ExpenseFilter.dateBounds(DateRangeFilter.THIS_MONTH, now, utc)!!
        assertEquals(millis("2026-03-01"), bounds.first)
        assertEquals(millis("2026-04-01") - 1, bounds.last)
    }

    // --- combinations ---

    @Test
    fun `category and date range both apply`() {
        val result = filter(
            ExpenseFilterCriteria(
                categoryFilter = "c_food",
                dateRange = DateRangeFilter.THIS_MONTH
            )
        )
        // e4 is Food but next month; e2 is this month but Rent — both excluded.
        assertEquals(listOf("e1"), ids(result))
    }

    @Test
    fun `search and person both apply`() {
        val expenses = listOf(
            expense("a", "2026-03-10", categoryName = "Food", addedBy = "u1"),
            expense("b", "2026-03-11", categoryName = "Food", addedBy = "u2"),
            expense("c", "2026-03-12", categoryName = "Rent", addedBy = "u1")
        )
        val result = filter(
            ExpenseFilterCriteria(searchQuery = "food", personFilter = "u1"),
            expenses
        )
        assertEquals(listOf("a"), ids(result))
    }

    @Test
    fun `filtering an empty list yields empty rather than crashing`() {
        assertTrue(filter(ExpenseFilterCriteria(searchQuery = "food"), emptyList()).isEmpty())
    }

    // --- option lists ---

    @Test
    fun `category options are distinct and sorted by label`() {
        val result = ExpenseFilter.categoryOptions(all)
        assertEquals(listOf("Food", "Gifts", "Rent", "Travel"), result.map { it.label })
        assertEquals("c_food", result.first { it.label == "Food" }.id)
    }

    @Test
    fun `category options dedupe by id when the same category repeats`() {
        val expenses = listOf(
            expense("a", "2026-03-10", categoryId = "c1", categoryName = "Food"),
            expense("b", "2026-03-11", categoryId = "c1", categoryName = "Food")
        )
        assertEquals(1, ExpenseFilter.categoryOptions(expenses).size)
    }

    @Test
    fun `person options are distinct and sorted by label`() {
        val expenses = listOf(
            expense("a", "2026-03-10", addedBy = "u2", addedByName = "Bala"),
            expense("b", "2026-03-11", addedBy = "u1", addedByName = "Asha"),
            expense("c", "2026-03-12", addedBy = "u1", addedByName = "Asha")
        )
        val result = ExpenseFilter.personOptions(expenses)
        assertEquals(listOf("Asha", "Bala"), result.map { it.label })
        assertEquals(listOf("u1", "u2"), result.map { it.id })
    }

    @Test
    fun `options skip entries with a blank id`() {
        val expenses = listOf(
            expense("a", "2026-03-10", addedBy = "", addedByName = ""),
            expense("b", "2026-03-11", addedBy = "u1", addedByName = "Asha")
        )
        assertEquals(listOf("u1"), ExpenseFilter.personOptions(expenses).map { it.id })
    }

    @Test
    fun `options of an empty list are empty`() {
        assertTrue(ExpenseFilter.categoryOptions(emptyList()).isEmpty())
        assertTrue(ExpenseFilter.personOptions(emptyList()).isEmpty())
    }
}
