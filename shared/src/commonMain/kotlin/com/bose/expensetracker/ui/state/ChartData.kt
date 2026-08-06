package com.bose.expensetracker.ui.state

data class PieChartEntry(
    val label: String,
    val value: Float,
    val color: Long
)

data class BarChartEntry(
    val label: String,
    val value: Float
)

data class LineChartEntry(
    val label: String,
    val value: Float
)
