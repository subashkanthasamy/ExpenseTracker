package com.bose.expensetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.bose.expensetracker.data.preferences.ThemePreferences

private val LightColorScheme = lightColorScheme(
    primary = AccentPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7FF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = AccentOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0D0),
    onSecondaryContainer = Color(0xFF3E1500),
    tertiary = IncomeGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4F8D4),
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = Color(0xFFF8F8FC),
    surfaceContainerHigh = Color(0xFFEEEEF4),
    outline = CardBorder,
    outlineVariant = Color(0xFFE8E8EE)
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A2D6B),
    onPrimaryContainer = Color(0xFFEDE7FF),
    secondary = AccentOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF5C2E1A),
    onSecondaryContainer = Color(0xFFFFE0D0),
    tertiary = IncomeGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF1B5E20),
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = Color(0xFF1A1A24),
    surfaceContainerHigh = Color(0xFF252532),
    outline = CardBorderDark,
    outlineVariant = Color(0xFF2E2E3A)
)

@Composable
fun ExpenseTrackerTheme(
    themePreferences: ThemePreferences? = null,
    content: @Composable () -> Unit
) {
    val themeMode by themePreferences?.getThemeMode()?.collectAsState(initial = ThemePreferences.THEME_SYSTEM)
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(ThemePreferences.THEME_SYSTEM) }

    val isDark = when (themeMode) {
        ThemePreferences.THEME_LIGHT -> false
        ThemePreferences.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
