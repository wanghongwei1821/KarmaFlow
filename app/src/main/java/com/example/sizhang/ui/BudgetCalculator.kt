package com.example.sizhang.ui

import com.example.sizhang.data.BudgetConfig
import com.example.sizhang.data.TransactionEntity
import com.example.sizhang.data.signedExpenseCents
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import kotlin.math.abs

data class DailySpendingPoint(
    val date: LocalDate,
    val actualCents: Long,
    val expectedCents: Long,
)

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
    val tomorrowAvailableCents: Long? = null,
    val tomorrowDate: LocalDate? = null,
    val tomorrowDistributableCents: Long? = null,
    val tomorrowRemainingDays: Int = 0,
    val dailySpending: List<DailySpendingPoint> = emptyList(),
)

object BudgetCalculator {
    fun calculate(
        transactions: List<TransactionEntity>,
        config: BudgetConfig,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        currentBalanceCents: Long? = null,
        dayStartBalanceCents: Long? = null,
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
        val reserved = config.reservedTotalCents
        val todayInCycle = !today.isBefore(cycleStart) && !today.isAfter(cycleEnd)
        val remainingDaysIncludingToday = if (todayInCycle) {
            java.time.temporal.ChronoUnit.DAYS.between(today, nextCycleStart).toInt()
        } else {
            0
        }
        val spendableBalance = currentBalanceCents?.let { balance ->
            balance - reserved - config.targetEndingBalanceCents
        }
        val cycleRemaining = spendableBalance ?: 0
        val totalCycleDays = java.time.temporal.ChronoUnit.DAYS.between(
            cycleStart,
            nextCycleStart,
        ).toInt().coerceAtLeast(1)
        val originalBalanceDaily = config.cycleStartingBalanceCents?.let { startingBalance ->
            divideMoneyRounded(
                startingBalance - reserved - config.targetEndingBalanceCents,
                totalCycleDays,
            )
        }
        val currentBalanceDaily = if (todayInCycle && remainingDaysIncludingToday > 0) {
            (dayStartBalanceCents ?: currentBalanceCents)?.let { balance ->
                divideMoneyRounded(
                    balance - reserved - config.targetEndingBalanceCents,
                    remainingDaysIncludingToday,
                )
            }
        } else {
            null
        }
        val daysFromTomorrow = (remainingDaysIncludingToday - 1).coerceAtLeast(0)
        val forecastDate = today.plusDays(1).takeIf { date ->
            todayInCycle && !date.isAfter(cycleEnd)
        }
        val tomorrowDistributable = if (forecastDate != null && daysFromTomorrow > 0) {
            currentBalanceCents?.let { balance ->
                balance - reserved - config.targetEndingBalanceCents
            }
        } else {
            null
        }
        val tomorrowAvailable = forecastTomorrowAvailable(
            distributableCents = tomorrowDistributable,
            remainingDays = daysFromTomorrow,
        )
        val chartEnd = when {
            today.isBefore(cycleStart) -> null
            today.isAfter(cycleEnd) -> cycleEnd
            else -> today
        }
        val dailySpending = chartEnd?.let { endDate ->
            val chartStart = maxOf(cycleStart, endDate.minusDays(30))
            val netSpendingByDate = cycleTransactions.groupBy { transaction ->
                Instant.ofEpochMilli(transaction.occurredAt)
                    .atZone(zoneId)
                    .toLocalDate()
            }.mapValues { (_, dailyTransactions) ->
                dailyTransactions.sumOf(TransactionEntity::signedExpenseCents)
            }
            val planBase = config.cycleStartingBalanceCents
                ?: currentBalanceCents?.plus(cycleSpent)
                ?: 0
            var spentBeforeDate = netSpendingByDate
                .filterKeys { date -> date.isBefore(chartStart) }
                .values
                .sum()
            generateSequence(chartStart) { date -> date.plusDays(1) }
                .takeWhile { date -> !date.isAfter(endDate) }
                .map { date ->
                    val remainingDays = java.time.temporal.ChronoUnit.DAYS
                        .between(date, nextCycleStart)
                        .toInt()
                        .coerceAtLeast(1)
                    val expected = divideMoneyRounded(
                        planBase - reserved - config.targetEndingBalanceCents - spentBeforeDate,
                        remainingDays,
                    )
                    val netActual = netSpendingByDate[date] ?: 0
                    spentBeforeDate += netActual
                    DailySpendingPoint(
                        date = date,
                        actualCents = netActual.coerceAtLeast(0),
                        expectedCents = expected,
                    )
                }
                .toList()
        }.orEmpty()
        val progressBase = config.cycleStartingBalanceCents
            ?: currentBalanceCents?.plus(cycleSpent)
        val used = if (progressBase == null || progressBase <= 0L) 0f else {
            (cycleSpent.toDouble() / progressBase).coerceIn(0.0, 1.0).toFloat()
        }
        val dailyTarget = currentBalanceDaily ?: 0

        return BudgetSummary(
            todaySpentCents = todaySpent,
            monthSpentCents = cycleSpent,
            monthRemainingCents = cycleRemaining,
            todayAvailableCents = if (todayInCycle) dailyTarget else 0,
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
            tomorrowAvailableCents = tomorrowAvailable,
            tomorrowDate = forecastDate,
            tomorrowDistributableCents = tomorrowDistributable,
            tomorrowRemainingDays = daysFromTomorrow,
            dailySpending = dailySpending,
        )
    }

    fun forecastTomorrowAvailable(
        distributableCents: Long?,
        remainingDays: Int,
        additionalSpendCents: Long = 0,
    ): Long? {
        if (distributableCents == null || remainingDays <= 0) return null
        return divideMoneyRounded(
            distributableCents - additionalSpendCents.coerceAtLeast(0),
            remainingDays,
        )
    }

    private fun divideMoneyRounded(total: Long, divisor: Int): Long {
        if (divisor <= 0) return 0
        val quotient = total / divisor
        val remainder = total % divisor
        val roundsAwayFromZero = abs(remainder) * 2 >= divisor
        return quotient + when {
            !roundsAwayFromZero -> 0
            total > 0 -> 1
            total < 0 -> -1
            else -> 0
        }
    }
}
