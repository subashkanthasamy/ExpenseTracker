package com.bose.expensetracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Android DataStore implementation of the shared [ThemePreferences] interface.
 *
 * The THEME_* constants live on that interface's companion — don't redeclare them here.
 */
class ThemePreferencesImpl(
    private val context: Context
) : ThemePreferences {

    private val themeKey = intPreferencesKey("theme_mode")

    override fun getThemeMode(): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[themeKey] ?: ThemePreferences.THEME_SYSTEM
        }
    }

    override suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = mode
        }
    }
}
