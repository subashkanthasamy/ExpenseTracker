package com.bose.expensetracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.PropaneTank
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bose.expensetracker.util.CategoryIcons
import com.bose.expensetracker.ui.theme.AccentPurple
import com.bose.expensetracker.ui.theme.ExpenseRed
import com.bose.expensetracker.ui.theme.IncomeGreen

@Composable
fun CircularProgressRing(
    percentage: Float,
    centerText: String,
    centerSubtext: String = "",
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    ringSize: Dp = 120.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    progressColor: Color = AccentPurple
) {
    var animatedTarget by remember { mutableFloatStateOf(0f) }
    val animatedPercentage by animateFloatAsState(
        targetValue = animatedTarget,
        animationSpec = tween(durationMillis = 800),
        label = "ring"
    )
    LaunchedEffect(percentage) {
        animatedTarget = percentage.coerceIn(0f, 100f)
    }

    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(ringSize)) {
            val stroke = strokeWidth.toPx()
            val arcSize = size.width - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = (animatedPercentage / 100f) * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${percentage.toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (centerSubtext.isNotEmpty()) {
                Text(
                    text = centerSubtext,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun WeeklyBarChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color = AccentPurple
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.second }.coerceAtLeast(1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { (label, value) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                val barHeight = ((value / maxValue) * 100).coerceAtLeast(4f)
                Canvas(
                    modifier = Modifier
                        .width(24.dp)
                        .height(barHeight.dp)
                ) {
                    drawRoundRect(
                        color = barColor,
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        size = Size(size.width, size.height)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CategoryProgressBar(
    icon: ImageVector,
    name: String,
    percentage: Float,
    modifier: Modifier = Modifier,
    color: Color = AccentPurple
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${percentage.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun SmartInsightCard(
    icon: ImageVector,
    title: String,
    amount: String,
    badgeLabel: String,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
    }
}

@Composable
fun FilterTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            FilterChip(
                label = label,
                isSelected = index == selectedIndex,
                onClick = { onTabSelected(index) }
            )
        }
    }
}

/**
 * Same chips as [FilterTabRow] but horizontally scrollable, for lists whose length isn't known
 * up front — a household can easily have more categories than fit on one line.
 */
@Composable
fun ScrollableFilterChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp)
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding
    ) {
        itemsIndexed(labels) { index, label ->
            FilterChip(
                label = label,
                isSelected = index == selectedIndex,
                onClick = { onSelected(index) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isSelected) {
                    Modifier.background(AccentPurple)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
fun TimelineItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    amount: Double,
    isLast: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Timeline dot + line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(AccentPurple)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(AccentPurple.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Transaction card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatAmount(amount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (amount >= 0) IncomeGreen else ExpenseRed
                )
            }
        }
    }
}

@Composable
fun CategoryIconTile(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isSelected) {
                    Modifier.background(AccentPurple.copy(alpha = 0.2f))
                        .border(1.5.dp, AccentPurple, RoundedCornerShape(16.dp))
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                }
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) AccentPurple else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun QuickSuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, AccentPurple.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = AccentPurple,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun InsightBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun DateGroupHeader(
    date: String,
    total: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = date.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = AccentPurple,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = total,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Material icon for a category.
 *
 * Replaces the old emoji lookup. Emoji were drawn by the system emoji font, so the same
 * category rendered as different art on Android and iOS, could not be tinted to follow the
 * theme, and read to TalkBack as "pizza" rather than the category name. These are vectors:
 * one shape, tintable, and optically matched to the rest of the UI.
 *
 * The name→key decision lives in shared [CategoryIcons] so iOS resolves identically; only the
 * key→drawable step below is platform-specific.
 */
fun categoryIcon(categoryName: String, storedIcon: String? = null): ImageVector =
    when (CategoryIcons.resolve(storedIcon, categoryName)) {
        "restaurant" -> Icons.Filled.Restaurant
        "shopping_cart" -> Icons.Filled.ShoppingCart
        "directions_car" -> Icons.Filled.DirectionsCar
        "home" -> Icons.Filled.Home
        "receipt_long" -> Icons.Filled.ReceiptLong
        "family_restroom" -> Icons.Filled.FamilyRestroom
        "movie" -> Icons.Filled.Movie
        "shopping_bag" -> Icons.Filled.ShoppingBag
        "medical_services" -> Icons.Filled.MedicalServices
        "school" -> Icons.Filled.School
        "flight" -> Icons.Filled.Flight
        "shield" -> Icons.Filled.Shield
        "card_giftcard" -> Icons.Filled.CardGiftcard
        "fitness_center" -> Icons.Filled.FitnessCenter
        "payments" -> Icons.Filled.Payments
        "trending_up" -> Icons.Filled.TrendingUp
        "autorenew" -> Icons.Filled.Autorenew
        // India / Tamil Nadu staples. A wrong Material name fails the build, so these are
        // self-checking — unlike the SF Symbol side, where a typo renders an empty frame.
        "eco" -> Icons.Filled.Eco
        "local_drink" -> Icons.Filled.LocalDrink
        "water_drop" -> Icons.Filled.WaterDrop
        "local_gas_station" -> Icons.Filled.LocalGasStation
        "bolt" -> Icons.Filled.Bolt
        "propane_tank" -> Icons.Filled.PropaneTank
        "wifi" -> Icons.Filled.Wifi
        else -> Icons.Filled.MoreHoriz
    }

