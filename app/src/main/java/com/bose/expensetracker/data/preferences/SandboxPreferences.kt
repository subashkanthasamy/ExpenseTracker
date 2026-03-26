package com.bose.expensetracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

object SandboxConstants {
    const val SANDBOX_USER_ID = "sandbox_user"
    const val SANDBOX_HOUSEHOLD_ID = "sandbox_household"
    const val SANDBOX_DISPLAY_NAME = "Demo User"
}

@Singleton
class SandboxPreferences @Inject constructor(
    private val context: Context
) {
    private val key = booleanPreferencesKey("is_sandbox_active")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var isSandboxCached: Boolean = false
        private set

    init {
        scope.launch {
            context.dataStore.data.collect { prefs ->
                isSandboxCached = prefs[key] ?: false
            }
        }
    }

    fun isSandboxActive(): Flow<Boolean> {
        return context.dataStore.data.map { it[key] ?: false }
    }

    suspend fun setSandboxActive(active: Boolean) {
        isSandboxCached = active
        context.dataStore.edit { it[key] = active }
    }
}
