package com.bose.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PieChart(
    data: List<PieChartEntry>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val total = data.sumOf { it.value.toDouble() }.toFloat()
    if (total <= 0f) return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pie
        Canvas(
            modifier = Modifier.size(140.dp)
        ) {
            var startAngle = -90f
            data.forEach { entry ->
                val sweep = (entry.value / total) * 360f
                drawArc(
                    color = entry.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    size = Size(size.width, size.height)
                )
                startAngle += sweep
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Legend
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            data.take(6).forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = entry.color)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${entry.label} (${((entry.value / total) * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (data.size > 6) {
                Text(
                    "+${data.size - 6} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BarChart(
    data: List<BarChartEntry>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val maxValue = data.maxOf { it.value }.toFloat()
    if (maxValue <= 0f) return

    val barColor = com.bose.expensetracker.ui.theme.AccentPurple
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val labelStep = when {
        data.size > 10 -> 3
        data.size > 6 -> 2
        else -> 1
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val barCount = data.size
        val spacing = 4.dp.toPx()
        val labelAreaHeight = 24.dp.toPx()
        val chartHeight = size.height - labelAreaHeight
        val barWidth = (size.width - (spacing * (barCount + 1))) / barCount

        val textPaint = android.graphics.Paint().apply {
            color = labelColor.hashCode()
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        data.forEachIndexed { index, entry ->
            val barHeight = (entry.value / maxValue) * chartHeight
            val x = spacing + index * (barWidth + spacing)
            val y = chartHeight - barHeight

            // Draw bar with rounded top
            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
            )

            // Draw label centered under bar
            if (index % labelStep == 0) {
                val labelX = x + barWidth / 2
                val labelY = size.height - 4.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(
                    entry.label,
                    labelX,
                    labelY,
                    textPaint
                )
            }
        }
    }
}

@Composable
fun LineChart(
    data: List<LineChartEntry>,
    modifier: Modifier = Modifier
) {
    if (data.size < 2) return

    val maxValue = data.maxOf { it.value }.toFloat()
    val minValue = data.minOf { it.value }.toFloat()
    val range = if (maxValue == minValue) 1f else maxValue - minValue

    val lineColor = com.bose.expensetracker.ui.theme.AccentPurple
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val chartHeight = size.height - 20f
            val chartWidth = size.width
            val stepX = chartWidth / (data.size - 1)

            val path = Path()
            data.forEachIndexed { index, entry ->
                val x = index * stepX
                val y = chartHeight - ((entry.value - minValue) / range) * chartHeight

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }

                // Draw dots
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { entry ->
                Text(
                    entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1
                )
            }
        }
    }
}

data class PieChartEntry(
    val label: String,
    val value: Float,
    val color: Color
)

data class BarChartEntry(
    val label: String,
    val value: Float
)

data class LineChartEntry(
    val label: String,
    val value: Float
)
