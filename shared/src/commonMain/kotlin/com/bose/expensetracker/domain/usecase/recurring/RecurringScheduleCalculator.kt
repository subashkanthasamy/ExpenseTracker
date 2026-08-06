package com.bose.expensetracker.domain.usecase.recurring

import com.bose.expensetracker.domain.model.RecurringExpense
import com.bose.expensetracker.domain.model.RecurringFrequency
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Works out which days a recurring rule should have produced an expense on.
 *
 * This lives in `shared` because both platforms now read the same rules from Firestore. If
 * Android checked only "is it due today" (its original behaviour) while iOS caught up on
 * missed days, the two would generate different expenses from the same rule and would fight
 * over `lastGeneratedDate`.
 *
 * Catch-up matters because neither platform can guarantee it runs on the due day: Android's
 * worker can be deferred by Doze, and iOS has no dependable background execution at all.
 * Walking from the last generated day means a missed day is picked up next launch instead of
 * being lost.
 */
object RecurringScheduleCalculator {

    /** Upper bound on how far back to walk, so an old start date can't flood the ledger. */
    const val MAX_CATCH_UP_DAYS = 400

    /**
     * Days (as start-of-day epoch millis) that [rule] is due on, from the day after
     * [lastGeneratedMillis] through [todayMillis] inclusive.
     */
    fun dueDays(
        rule: RecurringExpense,
        todayMillis: Long,
        lastGeneratedMillis: Long? = rule.lastGeneratedDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        maxCatchUpDays: Int = MAX_CATCH_UP_DAYS
    ): List<Long> {
        if (!rule.isActive) return emptyList()

        val today = dateOf(todayMillis, timeZone)
        val start = dateOf(rule.startDate, timeZone)
        if (today < start) return emptyList()

        rule.endDate?.let { end ->
            if (today > dateOf(end, timeZone)) return emptyList()
        }

        var cursor = lastGeneratedMillis
            ?.let { dateOf(it, timeZone).plus(DatePeriod(days = 1)) }
            ?.coerceAtLeast(start)
            ?: start

        val earliest = today.minusDays(maxCatchUpDays)
        if (cursor < earliest) cursor = earliest

        val due = mutableListOf<Long>()
        while (cursor <= today) {
            if (isDue(rule, cursor)) due.add(cursor.atStartOfDayIn(timeZone).toEpochMilliseconds())
            cursor = cursor.plus(DatePeriod(days = 1))
        }
        return due
    }

    /**
     * Convenience overload for Swift callers: Kotlin default arguments are not exported to
     * Objective-C, so without this every call site has to construct a kotlinx-datetime
     * TimeZone by hand.
     */
    fun dueDaysInDeviceZone(rule: RecurringExpense, todayMillis: Long): List<Long> =
        dueDays(rule = rule, todayMillis = todayMillis)

    /** Whether [rule] falls due on [date]. */
    fun isDue(rule: RecurringExpense, date: LocalDate): Boolean {
        val lastDayOfMonth = lastDayOfMonth(date.year, date.monthNumber)
        return when (rule.frequency) {
            RecurringFrequency.DAILY -> true
            RecurringFrequency.WEEKLY -> calendarDayOfWeek(date) == (rule.dayOfWeek ?: 2)
            RecurringFrequency.MONTHLY -> matchesDayOfMonth(date.dayOfMonth, rule.dayOfMonth ?: 1, lastDayOfMonth)
            RecurringFrequency.YEARLY ->
                matchesDayOfMonth(date.dayOfMonth, rule.dayOfMonth ?: 1, lastDayOfMonth) &&
                    date.monthNumber == (rule.monthOfYear ?: 1)
        }
    }

    /**
     * A rule set for a day the month doesn't have (e.g. the 31st in February) fires on the
     * last day instead, matching the original Android behaviour.
     */
    private fun matchesDayOfMonth(day: Int, target: Int, lastDayOfMonth: Int): Boolean =
        day == target || (target > lastDayOfMonth && day == lastDayOfMonth)

    /**
     * Both platforms persist `java.util.Calendar.DAY_OF_WEEK` values (Sunday = 1), whereas
     * kotlinx-datetime is ISO (Monday = 1).
     */
    private fun calendarDayOfWeek(date: LocalDate): Int = date.dayOfWeek.isoDayNumber % 7 + 1

    private fun dateOf(millis: Long, timeZone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date

    private fun LocalDate.minusDays(days: Int): LocalDate = plus(DatePeriod(days = -days))

    private fun lastDayOfMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        else -> 30
    }
}
