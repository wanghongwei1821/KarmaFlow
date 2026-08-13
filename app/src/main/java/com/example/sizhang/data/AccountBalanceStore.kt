package com.example.sizhang.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.accountBalanceDataStore by preferencesDataStore(name = "account_balance")

enum class BalanceSource {
    SMS,
    MANUAL,
}

data class AccountBalance(
    val amountCents: Long? = null,
    val updatedAt: Long = 0,
    val source: BalanceSource? = null,
)

class AccountBalanceStore(private val context: Context) {
    private object Keys {
        val amount = longPreferencesKey("amount_cents")
        val updatedAt = longPreferencesKey("updated_at")
        val source = stringPreferencesKey("source")
    }

    val balance: Flow<AccountBalance> = context.accountBalanceDataStore.data.map { preferences ->
        AccountBalance(
            amountCents = preferences[Keys.amount],
            updatedAt = preferences[Keys.updatedAt] ?: 0,
            source = preferences[Keys.source]?.let { value ->
                runCatching { BalanceSource.valueOf(value) }.getOrNull()
            },
        )
    }

    suspend fun updateFromSms(amountCents: Long, smsReceivedAt: Long) {
        if (amountCents < 0 || smsReceivedAt <= 0) return
        context.accountBalanceDataStore.edit { preferences ->
            val currentUpdatedAt = preferences[Keys.updatedAt] ?: 0
            if (smsReceivedAt > currentUpdatedAt) {
                preferences[Keys.amount] = amountCents
                preferences[Keys.updatedAt] = smsReceivedAt
                preferences[Keys.source] = BalanceSource.SMS.name
            }
        }
    }

    suspend fun updateManually(amountCents: Long, updatedAt: Long = System.currentTimeMillis()) {
        if (amountCents < 0) return
        context.accountBalanceDataStore.edit { preferences ->
            preferences[Keys.amount] = amountCents
            preferences[Keys.updatedAt] = updatedAt
            preferences[Keys.source] = BalanceSource.MANUAL.name
        }
    }
}
