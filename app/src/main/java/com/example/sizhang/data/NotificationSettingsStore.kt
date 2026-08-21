package com.example.sizhang.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationSettingsDataStore by preferencesDataStore(
    name = "notification_settings",
)

data class NotificationDisplaySettings(
    val enabled: Boolean = true,
    val showTodaySpent: Boolean = true,
    val showTodayAvailable: Boolean = false,
    val showTomorrowAvailable: Boolean = false,
    val showCurrentBalance: Boolean = false,
    val showCycleSpent: Boolean = false,
    val showDistributableBalance: Boolean = false,
    val showTargetBalance: Boolean = false,
    val showReservedAmount: Boolean = false,
    val showRemainingDays: Boolean = false,
) {
    val selectedCount: Int
        get() = listOf(
            showTodaySpent,
            showTodayAvailable,
            showTomorrowAvailable,
            showCurrentBalance,
            showCycleSpent,
            showDistributableBalance,
            showTargetBalance,
            showReservedAmount,
            showRemainingDays,
        ).count { it }
}

class NotificationSettingsStore(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val todaySpent = booleanPreferencesKey("today_spent")
        val todayAvailable = booleanPreferencesKey("today_available")
        val tomorrowAvailable = booleanPreferencesKey("tomorrow_available")
        val currentBalance = booleanPreferencesKey("current_balance")
        val cycleSpent = booleanPreferencesKey("cycle_spent")
        val distributableBalance = booleanPreferencesKey("distributable_balance")
        val targetBalance = booleanPreferencesKey("target_balance")
        val reservedAmount = booleanPreferencesKey("reserved_amount")
        val remainingDays = booleanPreferencesKey("remaining_days")
    }

    val settings: Flow<NotificationDisplaySettings> =
        context.notificationSettingsDataStore.data.map { preferences ->
            NotificationDisplaySettings(
                enabled = preferences[Keys.enabled] ?: true,
                showTodaySpent = preferences[Keys.todaySpent] ?: true,
                showTodayAvailable = preferences[Keys.todayAvailable] ?: false,
                showTomorrowAvailable = preferences[Keys.tomorrowAvailable] ?: false,
                showCurrentBalance = preferences[Keys.currentBalance] ?: false,
                showCycleSpent = preferences[Keys.cycleSpent] ?: false,
                showDistributableBalance = preferences[Keys.distributableBalance] ?: false,
                showTargetBalance = preferences[Keys.targetBalance] ?: false,
                showReservedAmount = preferences[Keys.reservedAmount] ?: false,
                showRemainingDays = preferences[Keys.remainingDays] ?: false,
            )
        }

    suspend fun update(settings: NotificationDisplaySettings) {
        context.notificationSettingsDataStore.edit { preferences ->
            preferences[Keys.enabled] = settings.enabled
            preferences[Keys.todaySpent] = settings.showTodaySpent
            preferences[Keys.todayAvailable] = settings.showTodayAvailable
            preferences[Keys.tomorrowAvailable] = settings.showTomorrowAvailable
            preferences[Keys.currentBalance] = settings.showCurrentBalance
            preferences[Keys.cycleSpent] = settings.showCycleSpent
            preferences[Keys.distributableBalance] = settings.showDistributableBalance
            preferences[Keys.targetBalance] = settings.showTargetBalance
            preferences[Keys.reservedAmount] = settings.showReservedAmount
            preferences[Keys.remainingDays] = settings.showRemainingDays
        }
    }
}
