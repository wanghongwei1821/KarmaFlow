package com.example.sizhang.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

private val Context.accountBalanceDataStore by preferencesDataStore(name = "account_balance")

enum class BalanceSource {
    SMS,
    MANUAL,
}

data class AccountBalance(
    val amountCents: Long? = null,
    val updatedAt: Long = 0,
    val source: BalanceSource? = null,
    val dayStartAmountCents: Long? = null,
    val snapshotEpochDay: Long? = null,
)

class AccountBalanceStore(private val context: Context) {
    private object Keys {
        val amount = longPreferencesKey("amount_cents")
        val updatedAt = longPreferencesKey("updated_at")
        val source = stringPreferencesKey("source")
        val dayStartAmount = longPreferencesKey("day_start_amount_cents")
        val snapshotEpochDay = longPreferencesKey("snapshot_epoch_day")
    }

    val balance: Flow<AccountBalance> = context.accountBalanceDataStore.data.map { preferences ->
        AccountBalance(
            amountCents = preferences[Keys.amount],
            updatedAt = preferences[Keys.updatedAt] ?: 0,
            source = preferences[Keys.source]?.let { value ->
                runCatching { BalanceSource.valueOf(value) }.getOrNull()
            },
            dayStartAmountCents = preferences[Keys.dayStartAmount],
            snapshotEpochDay = preferences[Keys.snapshotEpochDay],
        )
    }

    suspend fun updateFromSms(amountCents: Long, smsReceivedAt: Long) {
        if (amountCents < 0 || smsReceivedAt <= 0) return
        context.accountBalanceDataStore.edit { preferences ->
            val currentUpdatedAt = preferences[Keys.updatedAt] ?: 0
            if (smsReceivedAt > currentUpdatedAt) {
                ensureSnapshotForDay(preferences, epochDay(smsReceivedAt), amountCents)
                preferences[Keys.amount] = amountCents
                preferences[Keys.updatedAt] = smsReceivedAt
                preferences[Keys.source] = BalanceSource.SMS.name
            }
        }
    }

    suspend fun updateManually(amountCents: Long, updatedAt: Long = System.currentTimeMillis()) {
        if (amountCents < 0) return
        context.accountBalanceDataStore.edit { preferences ->
            preferences[Keys.dayStartAmount] = amountCents
            preferences[Keys.snapshotEpochDay] = epochDay(updatedAt)
            preferences[Keys.amount] = amountCents
            preferences[Keys.updatedAt] = updatedAt
            preferences[Keys.source] = BalanceSource.MANUAL.name
        }
    }

    suspend fun ensureDailySnapshot(nowMillis: Long = System.currentTimeMillis()) {
        context.accountBalanceDataStore.edit { preferences ->
            val currentAmount = preferences[Keys.amount] ?: return@edit
            ensureSnapshotForDay(preferences, epochDay(nowMillis), currentAmount)
        }
    }

    suspend fun refreshDailySnapshot(nowMillis: Long = System.currentTimeMillis()) {
        context.accountBalanceDataStore.edit { preferences ->
            val currentAmount = preferences[Keys.amount] ?: return@edit
            preferences[Keys.dayStartAmount] = currentAmount
            preferences[Keys.snapshotEpochDay] = epochDay(nowMillis)
        }
    }

    private fun ensureSnapshotForDay(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        epochDay: Long,
        fallbackAmountCents: Long,
    ) {
        if (preferences[Keys.snapshotEpochDay] == epochDay) return
        preferences[Keys.dayStartAmount] = preferences[Keys.amount] ?: fallbackAmountCents
        preferences[Keys.snapshotEpochDay] = epochDay
    }

    private fun epochDay(timestamp: Long): Long = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()
}
