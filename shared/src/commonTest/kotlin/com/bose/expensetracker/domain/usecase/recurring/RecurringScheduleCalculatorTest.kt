package com.bose.expensetracker.domain.usecase.recurring

import com.bose.expensetracker.domain.model.RecurringExpense
import com.bose.expensetracker.domain.model.RecurringFrequency
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurringScheduleCalculatorTest {

    private val utc = TimeZone.UTC

    private fun millis(date: String): Long =
        LocalDate.parse(date).atStartOfDayIn(utc).toEpochMilliseconds()

    private fun rule(
        frequency: RecurringFrequency,
        startDate: String,
        dayOfMonth: Int? = null,
        dayOfWeek: Int? = null,
        monthOfYear: Int? = null,
        endDate: String? = null,
        lastGenerated: String? = null,
        isActive: Boolean = true
    ) = RecurringExpense(
        id = "r1",
        householdId = "h1",
        amount = 100.0,
        categoryId = "c1",
        categoryName = "Rent",
        notes = "",
        addedBy = "u1",
        addedByName = "Me",
        frequency = frequency,
        dayOfWeek = dayOfWeek,
        dayOfMonth = dayOfMonth,
        monthOfYear = monthOfYear,
        startDate = millis(startDate),
        endDate = endDate?.let { millis(it) },
        lastGeneratedDate = lastGenerated?.let { millis(it) },
        isActive = isActive,
        createdAt = millis(startDate)
    )

    // --- catch-up ------------------------------------------------------------

    @Test
    fun dailyRuleCatchesUpEveryMissedDay() {
        val due = RecurringScheduleCalculator.dueDays(
            rule(RecurringFrequency.DAILY, startDate = "2026-08-01", lastGenerated = "2026-08-01"),
            todayMillis = millis("2026-08-05"),
            timeZone = utc
        )
        // 2nd, 3rd, 4th, 5th — the missed days are recovered rather than lost.
        assertEquals(listOf(millis("2026-08-02"), millis("2026-08-03"), millis("2026-08-04"), millis("2026-08-05")), due)
    }

    @Test
    fun nothingDueWhenAlreadyGeneratedToday() {
        val due = RecurringScheduleCalculator.dueDays(
            rule(RecurringFrequency.DAILY, startDate = "2026-08-01", lastGenerated = "2026-08-05"),
            todayMillis = millis("2026-08-05"),
            timeZone = utc
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun monthlyRuleProducesOneDayPerMonthWhileCatchingUp() {
        val due = RecurringScheduleCalculator.dueDays(
            rule(RecurringFrequency.MONTHLY, startDate = "2026-01-10", dayOfMonth = 10, lastGenerated = "2026-01-10"),
            todayMillis = millis("2026-04-15"),
            timeZone = utc
        )
        assertEquals(listOf(millis("2026-02-10"), millis("2026-03-10"), millis("2026-04-10")), due)
    }

    @Test
    fun monthlyOn31stFallsBackToLastDayOfShortMonths() {
        val due = RecurringScheduleCalculator.dueDays(
            rule(RecurringFrequency.MONTHLY, startDate = "2026-01-31", dayOfMonth = 31, lastGenerated = "2026-01-31"),
            todayMillis = millis("2026-04-30"),
            timeZone = utc
        )
        // February clamps to the 28th in 2026, April to the 30th.
        assertEquals(listOf(millis("2026-02-28"), millis("2026-03-31"), millis("2026-04-30")), due)
    }

    @Test
    fun weeklyRuleFiresOnTheConfiguredCalendarWeekday() {
        // 2026-08-05 is a Wednesday; Calendar.WEDNESDAY == 4.
        val due = RecurringScheduleCalculator.dueDays(
            rule(RecurringFrequency.WEEKLY, startDate = "2026-07-27", dayOfWeek = 4, lastGenerated = "2026-07-27"),
            todayMillis = millis("2026-08-05"),
            timeZone = utc
        )
        assertEquals(listOf(millis("2026-07-29"), millis("2026-08-05")), due)
    }

    @Test
    fun yearlyRuleFiresOnMonthAndDay() {
        val due = RecurringScheduleCalculator.dueDays(
            rule(RecurringFrequency.YEARLY, startDate = "2025-04-01", dayOfMonth = 1, monthOfYear = 4, lastGenerated = "2025-04-01"),
            todayMillis = millis("2026-04-02"),
            timeZone = utc,
            maxCatchUpDays = 800
        )
        assertEquals(listOf(millis("2026-04-01")), due)
    }

    // --- boundaries ----------------------------------------------------------

    @Test
    fun inactiveRuleProducesNothing() {
        val due = RecurringScheduleCalculator.dueDays(
            rule(RecurringFrequency.DAILY, startDate = "2026-08-01", lastGenerated = "2026-08-01", isActive = false),
            todayMillis = millis("2026-08-05"),
            timeZone = utc
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun expiredRuleProducesNothing() {
        val due = RecurringScheduleCalculator.dueDays(
            rule(RecurringFrequency.DAILY, startDate = "2026-08-01", endDate = "2026-08-03", lastGenerated = "2026-08-01"),
            todayMillis = millis("2026-08-05"),
            timeZone = utc
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun ruleStartingInTheFutureProducesNothing() {
        val due = RecurringScheduleCalculator.dueDays(
            rule(RecurringFrequency.DAILY, startDate = "2026-09-01"),
            todayMillis = millis("2026-08-05"),
            timeZone = utc
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun neverGeneratedRuleStartsFromItsStartDate() {
        val due = RecurringScheduleCalculator.dueDays(
            rule(RecurringFrequency.DAILY, startDate = "2026-08-03"),
            todayMillis = millis("2026-08-05"),
            timeZone = utc
        )
        assertEquals(listOf(millis("2026-08-03"), millis("2026-08-04"), millis("2026-08-05")), due)
    }

    @Test
    fun catchUpIsCappedSoAnOldRuleCannotFloodTheLedger() {
        val due = RecurringScheduleCalculator.dueDays(
            rule(RecurringFrequency.DAILY, startDate = "2020-01-01", lastGenerated = "2020-01-01"),
            todayMillis = millis("2026-08-05"),
            timeZone = utc,
            maxCatchUpDays = 10
        )
        assertEquals(11, due.size)  // the cap plus today itself
    }
}
