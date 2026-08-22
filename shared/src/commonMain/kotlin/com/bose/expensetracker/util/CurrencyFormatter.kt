package com.bose.expensetracker.util

import kotlin.math.abs
import kotlin.math.roundToLong

fun formatCurrency(amount: Double): String {
    val absAmount = abs(amount)
    val prefix = if (amount < 0) "-" else ""

    // Rounded to paise FIRST, then split. Taking the whole part and rounding the leftover
    // fraction separately lets that fraction reach 100, so 1.999 rendered as "1.100".
    val totalPaise = (absAmount * 100).roundToLong()

    // Indian number format: 1,23,456.00
    val wholeStr = formatIndianNumber(totalPaise / 100)
    return "$prefix₹$wholeStr.${(totalPaise % 100).toString().padStart(2, '0')}"
}

fun formatAmount(amount: Double): String {
    val absAmount = abs(amount)
    return when {
        absAmount >= 10_000_000 -> "₹${formatOneDecimal(absAmount / 10_000_000)}Cr"
        absAmount >= 100_000 -> "₹${formatOneDecimal(absAmount / 100_000)}L"
        absAmount >= 1_000 -> "₹${formatOneDecimal(absAmount / 1_000)}K"
        else -> formatCurrency(amount)
    }
}

private fun formatOneDecimal(value: Double): String {
    // The same rounding trap as formatCurrency: rounding the fraction on its own let it reach
    // 10, so 9.99 lakh rendered as "9.10L" instead of "10L".
    val tenths = (value * 10).roundToLong()
    val whole = tenths / 10
    val tenth = tenths % 10
    return if (tenth == 0L) "$whole" else "$whole.$tenth"
}

/**
 * Groups [number] the Indian way: the last three digits, then pairs — 1,23,45,678.
 *
 * Every group is zero-padded to two digits **except the most significant one**, and the only
 * way to know a group is the most significant is that nothing remains after it. Deciding that
 * from the size of the accumulated list got it exactly backwards — it padded the leading group
 * and skipped the interior one, so one lakh rendered as "01,0,000".
 */
private fun formatIndianNumber(number: Long): String {
    if (number < 1000) return number.toString()
    val parts = mutableListOf((number % 1000).toString().padStart(3, '0'))
    var remaining = number / 1000
    while (remaining > 0) {
        val group = remaining % 100
        remaining /= 100
        parts.add(0, if (remaining > 0) group.toString().padStart(2, '0') else group.toString())
    }
    return parts.joinToString(",")
}

fun getCategoryEmoji(categoryName: String): String = when (categoryName.lowercase()) {
    "food" -> "🍔"
    "groceries" -> "🛒"
    "transport" -> "🚗"
    "entertainment" -> "🎬"
    "shopping" -> "🛍️"
    "bills" -> "📱"
    "health" -> "🏥"
    "education" -> "📚"
    "rent" -> "🏠"
    "salary", "income" -> "💰"
    "investment" -> "📈"
    "travel" -> "✈️"
    "insurance" -> "🛡️"
    "gifts" -> "🎁"
    "fitness" -> "💪"
    else -> "💳"
}
