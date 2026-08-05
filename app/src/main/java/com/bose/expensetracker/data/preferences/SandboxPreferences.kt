package com.bose.expensetracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private val Context.sandboxPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "sandbox_preferences"
)

class SandboxPreferences(private val context: Context) {

    companion object {
        private val IS_SANDBOX_ACTIVE = booleanPreferencesKey("is_sandbox_active")
    }

    // Synchronous snapshot — several call sites (Activity.onCreate, composables,
    // AuthRepository's non-suspend getters) can't await a Flow.
    @Volatile
    private var cachedSandboxActive = false

    val isSandboxCached: Boolean
        get() = cachedSandboxActive

    val isSandboxActive: Flow<Boolean>
        get() = context.sandboxPreferences.data
            .map { preferences -> preferences[IS_SANDBOX_ACTIVE] ?: false }
            .onEach { cachedSandboxActive = it }

    suspend fun setSandboxActive(active: Boolean) {
        context.sandboxPreferences.edit { preferences ->
            preferences[IS_SANDBOX_ACTIVE] = active
        }
        cachedSandboxActive = active
    }

    /** Primes [isSandboxCached] from disk. Call once at app startup. */
    suspend fun hydrate() {
        cachedSandboxActive = context.sandboxPreferences.data.first()[IS_SANDBOX_ACTIVE] ?: false
    }
}
