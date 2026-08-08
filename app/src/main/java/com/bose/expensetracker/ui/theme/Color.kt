package com.bose.expensetracker.ui.theme

import androidx.compose.ui.graphics.Color

// Legacy (keep for compatibility)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// New Design System
val GradientPurple = Color(0xFF7B61FF)
val GradientPink = Color(0xFFE040FB)
val GradientOrange = Color(0xFFFF8A65)

// Light theme colors
val BackgroundLight = Color(0xFFF5F5FA)
val SurfaceWhite = Color(0xFFFFFFFF)

// Dark theme colors. These belong to Theme.kt's DarkColorScheme and nowhere else — a screen
// referencing them directly renders dark in light mode, which is how the bugs above happened.
val BackgroundDark = Color(0xFF121218)
val SurfaceDark = Color(0xFF1E1E2A)

// Semantic colors (work on both light and dark)
val IncomeGreen = Color(0xFF4CAF50)
val ExpenseRed = Color(0xFFF44336)

val TextPrimary = Color(0xFF1A1A2E)
val TextSecondary = Color(0xFF9E9E9E)
val TextPrimaryDark = Color(0xFFE0E0E0)
val TextSecondaryDark = Color(0xFFAAAAAA)

val AccentPurple = Color(0xFF7B61FF)
val AccentOrange = Color(0xFFFF7043)

val NavActive = Color(0xFF7B61FF)
val NavInactive = Color(0xFFBDBDBD)

val CardBorder = Color(0xFFE8E8EE)
val CardBorderDark = Color(0xFF2E2E3A)

// Chat colors (Financial Coach). Only the user bubble is a fixed colour — it is the brand
// purple with white text, which reads the same in both themes. The bot bubble must come from
// the scheme: as a fixed dark it left themed text unreadable on it in light mode.
val ChatUserBubble = Color(0xFF7B61FF)

// Insight badge colors
val OverBudgetRed = Color(0xFFFF5252)
val SavingsGreen = Color(0xFF66BB6A)

// Score badge
val ScoreBadgeGreen = Color(0xFF2E7D32)
