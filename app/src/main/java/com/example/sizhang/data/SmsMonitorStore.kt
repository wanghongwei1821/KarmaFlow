package com.example.sizhang.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.smsMonitorDataStore by preferencesDataStore(name = "sms_monitor")

data class SmsMonitorState(
    val lastReceivedAt: Long = 0,
    val lastSender: String? = null,
    val resultCode: String? = null,
)

class SmsMonitorStore(private val context: Context) {
    private object Keys {
        val lastReceivedAt = longPreferencesKey("last_received_at")
        val lastSender = stringPreferencesKey("last_sender")
        val resultCode = stringPreferencesKey("result_code")
    }

    val state: Flow<SmsMonitorState> = context.smsMonitorDataStore.data.map { preferences ->
        SmsMonitorState(
            lastReceivedAt = preferences[Keys.lastReceivedAt] ?: 0,
            lastSender = preferences[Keys.lastSender],
            resultCode = preferences[Keys.resultCode],
        )
    }

    suspend fun record(sender: String, resultCode: String) {
        val safeSender = sender.filter(Char::isDigit).takeLast(11).ifBlank { "未知号码" }
        context.smsMonitorDataStore.edit { preferences ->
            preferences[Keys.lastReceivedAt] = System.currentTimeMillis()
            preferences[Keys.lastSender] = safeSender
            preferences[Keys.resultCode] = resultCode
        }
    }
}
