package com.example.sizhang.data

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.budgetDataStore by preferencesDataStore(name = "budget_config")

data class BudgetItem(
    val id: String,
    val name: String,
    val amountCents: Long,
)

data class BudgetConfig(
    val monthlyBudgetCents: Long = 888_800,
    val cycleStartEpochDay: Long = LocalDate.now().withDayOfMonth(1).toEpochDay(),
    val cycleEndEpochDay: Long = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toEpochDay(),
    val cycleStartingBalanceCents: Long? = null,
    val targetEndingBalanceCents: Long = 0,
    val reservedItems: List<BudgetItem> = listOf(
        BudgetItem("rent", "房租", 240_000),
        BudgetItem("car_rental", "租车", 30_000),
    ),
) {
    val reservedTotalCents: Long get() = reservedItems.sumOf { it.amountCents }
}

class BudgetStore(private val context: Context) {
    private object Keys {
        val monthlyBudget = longPreferencesKey("monthly_budget_cents")
        val rent = longPreferencesKey("rent_cents")
        val carRental = longPreferencesKey("car_rental_cents")
        val cycleStartDay = intPreferencesKey("cycle_start_day")
        val cycleStartDate = longPreferencesKey("cycle_start_epoch_day")
        val cycleEndDate = longPreferencesKey("cycle_end_epoch_day")
        val cycleStartingBalance = longPreferencesKey("cycle_starting_balance_cents")
        val targetEndingBalance = longPreferencesKey("target_ending_balance_cents")
        val reservedItems = stringPreferencesKey("reserved_items_v2")
    }

    val config: Flow<BudgetConfig> = context.budgetDataStore.data.map { preferences ->
        val legacyItems = listOf(
            BudgetItem("rent", "房租", preferences[Keys.rent] ?: 240_000),
            BudgetItem("car_rental", "租车", preferences[Keys.carRental] ?: 30_000),
        )
        val legacyCycle = legacyCycle(
            today = LocalDate.now(),
            requestedDay = (preferences[Keys.cycleStartDay] ?: 1).coerceIn(1, 31),
        )
        BudgetConfig(
            monthlyBudgetCents = preferences[Keys.monthlyBudget] ?: 888_800,
            cycleStartEpochDay = preferences[Keys.cycleStartDate] ?: legacyCycle.first.toEpochDay(),
            cycleEndEpochDay = preferences[Keys.cycleEndDate] ?: legacyCycle.second.toEpochDay(),
            cycleStartingBalanceCents = preferences[Keys.cycleStartingBalance],
            targetEndingBalanceCents = preferences[Keys.targetEndingBalance] ?: 0,
            reservedItems = preferences[Keys.reservedItems]
                ?.let(::decodeItems)
                ?: legacyItems,
        )
    }

    suspend fun update(config: BudgetConfig) {
        context.budgetDataStore.edit { preferences ->
            preferences[Keys.monthlyBudget] = config.monthlyBudgetCents.coerceAtLeast(0)
            preferences[Keys.cycleStartDate] = config.cycleStartEpochDay
            preferences[Keys.cycleEndDate] = config.cycleEndEpochDay.coerceAtLeast(config.cycleStartEpochDay)
            config.cycleStartingBalanceCents?.let { amount ->
                preferences[Keys.cycleStartingBalance] = amount.coerceAtLeast(0)
            } ?: preferences.remove(Keys.cycleStartingBalance)
            preferences[Keys.targetEndingBalance] = config.targetEndingBalanceCents.coerceAtLeast(0)
            preferences[Keys.reservedItems] = encodeItems(config.reservedItems)
        }
    }

    private fun encodeItems(items: List<BudgetItem>): String = items.joinToString("\n") { item ->
        val safeId = item.id.replace(Regex("""[^A-Za-z0-9_-]"""), "")
        val safeName = java.net.URLEncoder.encode(item.name, Charsets.UTF_8.name())
        "$safeId\t$safeName\t${item.amountCents.coerceAtLeast(0)}"
    }

    private fun decodeItems(value: String): List<BudgetItem> = value.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 3) return@mapNotNull null
        val amount = parts[2].toLongOrNull() ?: return@mapNotNull null
        val name = runCatching {
            java.net.URLDecoder.decode(parts[1], Charsets.UTF_8.name())
        }.getOrNull()?.trim().orEmpty()
        if (parts[0].isBlank() || name.isBlank()) return@mapNotNull null
        BudgetItem(parts[0], name.take(30), amount.coerceAtLeast(0))
    }.toList()

    private fun legacyCycle(today: LocalDate, requestedDay: Int): Pair<LocalDate, LocalDate> {
        fun dateInMonth(month: LocalDate): LocalDate =
            month.withDayOfMonth(requestedDay.coerceIn(1, month.lengthOfMonth()))

        val thisMonthStart = dateInMonth(today.withDayOfMonth(1))
        val start = if (today.isBefore(thisMonthStart)) {
            dateInMonth(today.minusMonths(1).withDayOfMonth(1))
        } else {
            thisMonthStart
        }
        val nextStart = dateInMonth(start.plusMonths(1).withDayOfMonth(1))
        return start to nextStart.minusDays(1)
    }
}
