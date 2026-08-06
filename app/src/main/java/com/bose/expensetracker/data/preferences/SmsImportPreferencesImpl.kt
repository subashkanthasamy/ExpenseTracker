package com.bose.expensetracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Android DataStore implementation of the shared [SmsImportPreferences] interface. */
class SmsImportPreferencesImpl(
    private val context: Context
) : SmsImportPreferences {

    private fun smsImportKey(uid: String) = booleanPreferencesKey("sms_import_enabled_$uid")

    override fun isSmsImportEnabled(uid: String): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[smsImportKey(uid)] ?: false
        }
    }

    override suspend fun setSmsImportEnabled(uid: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[smsImportKey(uid)] = enabled
        }
    }
}
