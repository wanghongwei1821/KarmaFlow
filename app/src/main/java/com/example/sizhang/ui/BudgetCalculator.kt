package com.example.sizhang.ui

import com.example.sizhang.data.BudgetConfig
import com.example.sizhang.data.TransactionEntity
import com.example.sizhang.data.signedExpenseCents
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

data class BudgetSummary(
    val todaySpentCents: Long = 0,
    val monthSpentCents: Long = 0,
    val monthRemainingCents: Long = 0,
    val todayAvailableCents: Long = 0,
    val dailyTargetCents: Long = 0,
    val reservedCents: Long = 0,
    val usedFraction: Float = 0f,
    val cycleStartDate: LocalDate? = null,
    val cycleEndDate: LocalDate? = null,
    val originalBalanceDailyCents: Long? = null,
    val currentBalanceDailyCents: Long? = null,
    val currentBalanceCents: Long? = null,
    val totalCycleDays: Int = 0,
    val remainingCycleDays: Int = 0,
    val targetEndingBalanceCents: Long = 0,
)

object BudgetCalculator {
    fun calculate(
        transactions: List<TransactionEntity>,
        config: BudgetConfig,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        currentBalanceCents: Long? = null,
    ): BudgetSummary {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val cycleStart = LocalDate.ofEpochDay(config.cycleStartEpochDay)
        val cycleEnd = LocalDate.ofEpochDay(config.cycleEndEpochDay)
            .coerceAtLeast(cycleStart)
        val nextCycleStart = cycleEnd.plusDays(1)
        val cycleStartMillis = cycleStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val nextCycleMillis = nextCycleStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val todayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val tomorrowStart = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val cycleTransactions = transactions.filter { it.occurredAt in cycleStartMillis until nextCycleMillis }
        val cycleSpent = cycleTransactions.sumOf(TransactionEntity::signedExpenseCents)
        val todaySpent = if (!today.isBefore(cycleStart) && !today.isAfter(cycleEnd)) {
            cycleTransactions
                .filter { it.occurredAt in todayStart until tomorrowStart }
                .sumOf(TransactionEntity::signedExpenseCents)
        } else {
            0
        }
        val spentBeforeToday = cycleSpent - todaySpent
        val reserved = config.reservedTotalCents
        val todayInCycle = !today.isBefore(cycleStart) && !today.isAfter(cycleEnd)
        val remainingDaysIncludingToday = if (todayInCycle) {
            java.time.temporal.ChronoUnit.DAYS.between(today, nextCycleStart).toInt()
        } else {
            0
        }
        val dailyTarget = if (remainingDaysIncludingToday > 0) {
            ((config.monthlyBudgetCents - reserved - config.targetEndingBalanceCents - spentBeforeToday) /
                remainingDaysIncludingToday).coerceAtLeast(0)
        } else {
            0
        }
        val cycleRemaining = config.monthlyBudgetCents - reserved -
            config.targetEndingBalanceCents - cycleSpent
        val totalCycleDays = java.time.temporal.ChronoUnit.DAYS.between(
            cycleStart,
            nextCycleStart,
        ).toInt().coerceAtLeast(1)
        val originalBalanceDaily = config.cycleStartingBalanceCents?.let { startingBalance ->
            divideMoneyRounded(
                (startingBalance - config.targetEndingBalanceCents).coerceAtLeast(0),
                totalCycleDays,
            )
        }
        val currentBalanceDaily = if (todayInCycle && remainingDaysIncludingToday > 0) {
            currentBalanceCents?.let { balance ->
                divideMoneyRounded(
                    (balance - config.targetEndingBalanceCents).coerceAtLeast(0),
                    remainingDaysIncludingToday,
                )
            }
        } else {
            null
        }
        val used = if (config.monthlyBudgetCents == 0L) {
            if (reserved + cycleSpent > 0) 1f else 0f
        } else {
            ((reserved + cycleSpent).toDouble() / config.monthlyBudgetCents)
                .coerceIn(0.0, 1.0).toFloat()
        }

        return BudgetSummary(
            todaySpentCents = todaySpent,
            monthSpentCents = cycleSpent,
            monthRemainingCents = cycleRemaining,
            todayAvailableCents = if (todayInCycle) dailyTarget - todaySpent else 0,
            dailyTargetCents = dailyTarget,
            reservedCents = reserved,
            usedFraction = used,
            cycleStartDate = cycleStart,
            cycleEndDate = cycleEnd,
            originalBalanceDailyCents = originalBalanceDaily,
            currentBalanceDailyCents = currentBalanceDaily,
            currentBalanceCents = currentBalanceCents,
            totalCycleDays = totalCycleDays,
            remainingCycleDays = remainingDaysIncludingToday,
            targetEndingBalanceCents = config.targetEndingBalanceCents,
        )
    }

    private fun divideMoneyRounded(total: Long, divisor: Int): Long {
        if (total <= 0 || divisor <= 0) return 0
        val quotient = total / divisor
        val remainder = total % divisor
        return quotient + if (remainder * 2 >= divisor) 1 else 0
    }
}
