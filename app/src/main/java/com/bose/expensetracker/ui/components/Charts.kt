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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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

    val barColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val barCount = data.size
            val spacing = 4.dp.toPx()
            val barWidth = (size.width - (spacing * (barCount + 1))) / barCount
            val chartHeight = size.height - 20f

            data.forEachIndexed { index, entry ->
                val barHeight = (entry.value / maxValue) * chartHeight
                val x = spacing + index * (barWidth + spacing)
                val y = chartHeight - barHeight

                drawRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight)
                )
            }
        }

        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
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

@Composable
fun LineChart(
    data: List<LineChartEntry>,
    modifier: Modifier = Modifier
) {
    if (data.size < 2) return

    val maxValue = data.maxOf { it.value }.toFloat()
    val minValue = data.minOf { it.value }.toFloat()
    val range = if (maxValue == minValue) 1f else maxValue - minValue

    val lineColor = MaterialTheme.colorScheme.primary
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
