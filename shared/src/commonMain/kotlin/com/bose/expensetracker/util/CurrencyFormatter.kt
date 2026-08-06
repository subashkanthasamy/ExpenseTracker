package com.bose.expensetracker.util

import kotlin.math.abs
import kotlin.math.roundToInt

fun formatCurrency(amount: Double): String {
    val absAmount = abs(amount)
    val prefix = if (amount < 0) "-" else ""
    val wholePart = absAmount.toLong()
    val decimalPart = ((absAmount - wholePart) * 100).roundToInt()

    // Indian number format: 1,23,456.00
    val wholeStr = formatIndianNumber(wholePart)
    return "$prefix₹$wholeStr.${decimalPart.toString().padStart(2, '0')}"
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
    val intPart = value.toLong()
    val decPart = ((value - intPart) * 10).roundToInt()
    return if (decPart == 0) "$intPart" else "$intPart.$decPart"
}

private fun formatIndianNumber(number: Long): String {
    if (number < 1000) return number.toString()
    val last3 = (number % 1000).toString().padStart(3, '0')
    var remaining = number / 1000
    val parts = mutableListOf(last3)
    while (remaining > 0) {
        parts.add(0, (remaining % 100).toString().let { if (parts.size > 1) it.padStart(2, '0') else it })
        remaining /= 100
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
