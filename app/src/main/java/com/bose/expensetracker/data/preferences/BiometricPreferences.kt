package com.bose.expensetracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class BiometricPreferences @Inject constructor(
    private val context: Context
) {
    private fun biometricKey(uid: String) = booleanPreferencesKey("biometric_enabled_$uid")

    fun isBiometricEnabled(uid: String): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[biometricKey(uid)] ?: false
        }
    }

    suspend fun setBiometricEnabled(uid: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[biometricKey(uid)] = enabled
        }
    }
}
