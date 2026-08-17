package com.bose.expensetracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bose.expensetracker.domain.model.PaymentMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The method chosen last time an expense was added.
 *
 * A device preference, not household data: it never reaches Firestore, so it sidesteps the
 * permission model entirely. Without it every entry costs a tap on the field a user changes
 * least often.
 */
@Singleton
class PaymentMethodPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val key = stringPreferencesKey("last_payment_method")

    /**
     * Last method used, or [PaymentMethod.UPI] — the most common instrument for a rupee
     * household. Never [PaymentMethod.UNSPECIFIED]: that is a state old rows are in, not one
     * to nudge someone into choosing.
     */
    suspend fun lastUsed(): PaymentMethod {
        val stored = context.dataStore.data.first()[key]
        val method = PaymentMethod.fromWire(stored)
        return if (method == PaymentMethod.UNSPECIFIED) PaymentMethod.UPI else method
    }

    suspend fun setLastUsed(method: PaymentMethod) {
        // Only a real choice is remembered; an untouched UNSPECIFIED must not become default.
        if (method == PaymentMethod.UNSPECIFIED) return
        context.dataStore.edit { it[key] = method.wire }
    }
}
