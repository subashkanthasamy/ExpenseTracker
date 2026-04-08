package com.bose.expensetracker.data.preferences

import kotlinx.coroutines.flow.Flow

interface ThemePreferences {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }

    fun getThemeMode(): Flow<Int>
    suspend fun setThemeMode(mode: Int)
}

interface BiometricPreferences {
    fun isBiometricEnabled(uid: String): Flow<Boolean>
    suspend fun setBiometricEnabled(uid: String, enabled: Boolean)
}

interface SmsImportPreferences {
    fun isSmsImportEnabled(uid: String): Flow<Boolean>
    suspend fun setSmsImportEnabled(uid: String, enabled: Boolean)
}
