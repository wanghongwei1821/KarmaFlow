package com.example.sizhang.ui

import com.example.sizhang.data.BudgetConfig
import com.example.sizhang.data.BudgetItem
import com.example.sizhang.data.TransactionEntity
import com.example.sizhang.data.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BudgetCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = at(2026, 8, 12, 15, 0)
    private val config = BudgetConfig(
        monthlyBudgetCents = 888_800,
        cycleStartEpochDay = java.time.LocalDate.of(2026, 8, 1).toEpochDay(),
        cycleEndEpochDay = java.time.LocalDate.of(2026, 8, 31).toEpochDay(),
        reservedItems = listOf(
            BudgetItem("rent", "房租", 240_000),
            BudgetItem("car", "租车", 30_000),
        ),
    )

    @Test
    fun `fixed costs are reserved and remaining amount is spread across remaining days`() {
        val summary = BudgetCalculator.calculate(emptyList(), config, now, zone)

        assertEquals(270_000, summary.reservedCents)
        assertEquals(618_800, summary.monthRemainingCents)
        assertEquals(30_940, summary.dailyTargetCents)
        assertEquals(30_940, summary.todayAvailableCents)
    }

    @Test
    fun `custom start and end dates are independent`() {
        val custom = config.copy(
            cycleStartEpochDay = java.time.LocalDate.of(2026, 8, 10).toEpochDay(),
            cycleEndEpochDay = java.time.LocalDate.of(2026, 9, 20).toEpochDay(),
        )
        val transactions = listOf(
            transaction(10_000, TransactionKind.EXPENSE, at(2026, 7, 20, 12, 0), "outside"),
            transaction(2_000, TransactionKind.EXPENSE, at(2026, 8, 10, 0, 0), "inside"),
        )
        val summary = BudgetCalculator.calculate(transactions, custom, now, zone)

        assertEquals(2_000L, summary.monthSpentCents)
        assertEquals(java.time.LocalDate.of(2026, 8, 10), summary.cycleStartDate)
        assertEquals(java.time.LocalDate.of(2026, 9, 20), summary.cycleEndDate)
    }

    @Test
    fun `transactions on both boundary dates are included`() {
        val custom = config.copy(
            cycleStartEpochDay = java.time.LocalDate.of(2026, 8, 10).toEpochDay(),
            cycleEndEpochDay = java.time.LocalDate.of(2026, 8, 12).toEpochDay(),
        )
        val transactions = listOf(
            transaction(100, TransactionKind.EXPENSE, at(2026, 8, 10, 0, 0), "start"),
            transaction(200, TransactionKind.EXPENSE, at(2026, 8, 12, 23, 59), "end"),
            transaction(400, TransactionKind.EXPENSE, at(2026, 8, 13, 0, 0), "after"),
        )
        val summary = BudgetCalculator.calculate(transactions, custom, now, zone)

        assertEquals(300L, summary.monthSpentCents)
    }

    @Test
    fun `outside selected cycle today available is zero`() {
        val custom = config.copy(
            cycleStartEpochDay = java.time.LocalDate.of(2026, 9, 1).toEpochDay(),
            cycleEndEpochDay = java.time.LocalDate.of(2026, 9, 30).toEpochDay(),
        )
        val summary = BudgetCalculator.calculate(emptyList(), custom, now, zone)

        assertEquals(0L, summary.todayAvailableCents)
        assertEquals(0L, summary.dailyTargetCents)
    }

    @Test
    fun `today spending reduces only today available`() {
        val transactions = listOf(
            transaction(10_000, TransactionKind.EXPENSE, at(2026, 8, 5, 12, 0), "old"),
            transaction(2_850, TransactionKind.EXPENSE, at(2026, 8, 12, 12, 0), "today"),
            transaction(500, TransactionKind.REFUND, at(2026, 8, 12, 13, 0), "refund"),
        )
        val summary = BudgetCalculator.calculate(transactions, config, now, zone)

        assertEquals(12_350, summary.monthSpentCents)
        assertEquals(2_350, summary.todaySpentCents)
        assertEquals(30_440, summary.dailyTargetCents)
        assertEquals(28_090, summary.todayAvailableCents)
    }

    @Test
    fun `balance is spread across remaining days while original plan uses all cycle days`() {
        val balanceCycle = config.copy(
            cycleStartEpochDay = java.time.LocalDate.of(2026, 8, 1).toEpochDay(),
            cycleEndEpochDay = java.time.LocalDate.of(2026, 8, 10).toEpochDay(),
            cycleStartingBalanceCents = 1_000,
        )

        val summary = BudgetCalculator.calculate(
            transactions = emptyList(),
            config = balanceCycle,
            nowMillis = at(2026, 8, 2, 12, 0),
            zoneId = zone,
            currentBalanceCents = 500,
        )

        assertEquals(10, summary.totalCycleDays)
        assertEquals(9, summary.remainingCycleDays)
        assertEquals(100L, summary.originalBalanceDailyCents)
        assertEquals(56L, summary.currentBalanceDailyCents)
        assertEquals(500L, summary.currentBalanceCents)
    }

    @Test
    fun `today balance allowance stays fixed until the next day`() {
        val balanceCycle = config.copy(
            cycleStartEpochDay = java.time.LocalDate.of(2026, 8, 1).toEpochDay(),
            cycleEndEpochDay = java.time.LocalDate.of(2026, 8, 10).toEpochDay(),
            targetEndingBalanceCents = 0,
        )

        val afterFirstExpense = BudgetCalculator.calculate(
            transactions = listOf(
                transaction(100, TransactionKind.EXPENSE, at(2026, 8, 2, 10, 0), "first"),
            ),
            config = balanceCycle,
            nowMillis = at(2026, 8, 2, 10, 0),
            zoneId = zone,
            currentBalanceCents = 900,
            dayStartBalanceCents = 1_000,
        )
        val afterSecondExpense = BudgetCalculator.calculate(
            transactions = listOf(
                transaction(100, TransactionKind.EXPENSE, at(2026, 8, 2, 10, 0), "first"),
                transaction(200, TransactionKind.EXPENSE, at(2026, 8, 2, 15, 0), "second"),
            ),
            config = balanceCycle,
            nowMillis = at(2026, 8, 2, 15, 0),
            zoneId = zone,
            currentBalanceCents = 700,
            dayStartBalanceCents = 1_000,
        )
        val nextDay = BudgetCalculator.calculate(
            transactions = emptyList(),
            config = balanceCycle,
            nowMillis = at(2026, 8, 3, 8, 0),
            zoneId = zone,
            currentBalanceCents = 700,
            dayStartBalanceCents = 700,
        )

        assertEquals(111L, afterFirstExpense.currentBalanceDailyCents)
        assertEquals(111L, afterSecondExpense.currentBalanceDailyCents)
        assertEquals(88L, nextDay.currentBalanceDailyCents)
        assertEquals(113L, afterFirstExpense.tomorrowAvailableCents)
        assertEquals(88L, afterSecondExpense.tomorrowAvailableCents)
        assertEquals(java.time.LocalDate.of(2026, 8, 3), afterSecondExpense.tomorrowDate)
        assertEquals(100L, nextDay.tomorrowAvailableCents)
    }

    @Test
    fun `daily spending curve compares expected and actual CNY spending`() {
        val transactions = listOf(
            transaction(1_000, TransactionKind.EXPENSE, at(2026, 8, 10, 9, 0), "expense-1"),
            transaction(200, TransactionKind.REFUND, at(2026, 8, 10, 11, 0), "refund"),
            transaction(300, TransactionKind.EXPENSE, at(2026, 8, 11, 12, 0), "expense-2"),
            transaction(5_000, TransactionKind.INCOME, at(2026, 8, 11, 13, 0), "income"),
            transaction(6_000, TransactionKind.EXPENSE, at(2026, 8, 11, 14, 0), "foreign")
                .copy(currency = "HKD"),
        )

        val summary = BudgetCalculator.calculate(transactions, config, now, zone)

        assertEquals(12, summary.dailySpending.size)
        assertEquals(
            800L,
            summary.dailySpending.first { it.date == java.time.LocalDate.of(2026, 8, 10) }.actualCents,
        )
        assertEquals(
            300L,
            summary.dailySpending.first { it.date == java.time.LocalDate.of(2026, 8, 11) }.actualCents,
        )
        assertEquals(
            0L,
            summary.dailySpending.first { it.date == java.time.LocalDate.of(2026, 8, 12) }.actualCents,
        )
        assertEquals(
            28_127L,
            summary.dailySpending.first { it.date == java.time.LocalDate.of(2026, 8, 10) }.expectedCents,
        )
        assertEquals(
            29_429L,
            summary.dailySpending.first { it.date == java.time.LocalDate.of(2026, 8, 11) }.expectedCents,
        )
    }

    @Test
    fun `income is listed but excluded from all spending totals`() {
        val transactions = listOf(
            transaction(10_000, TransactionKind.EXPENSE, at(2026, 8, 5, 12, 0), "expense"),
            transaction(1_000_000, TransactionKind.INCOME, at(2026, 8, 12, 10, 0), "income"),
        )

        val summary = BudgetCalculator.calculate(transactions, config, now, zone)

        assertEquals(10_000L, summary.monthSpentCents)
        assertEquals(0L, summary.todaySpentCents)
    }

    @Test
    fun `foreign currency expense is excluded from CNY budget totals`() {
        val foreign = transaction(
            6_000,
            TransactionKind.EXPENSE,
            at(2026, 8, 11, 12, 0),
            "hkd",
        ).copy(currency = "HKD")

        val summary = BudgetCalculator.calculate(listOf(foreign), config, now, zone)

        assertEquals(0L, summary.monthSpentCents)
    }

    @Test
    fun `target ending balance is excluded from original and current daily amounts`() {
        val balanceCycle = config.copy(
            monthlyBudgetCents = 10_000,
            cycleStartEpochDay = java.time.LocalDate.of(2026, 8, 1).toEpochDay(),
            cycleEndEpochDay = java.time.LocalDate.of(2026, 8, 10).toEpochDay(),
            cycleStartingBalanceCents = 1_000,
            targetEndingBalanceCents = 200,
            reservedItems = emptyList(),
        )

        val summary = BudgetCalculator.calculate(
            transactions = emptyList(),
            config = balanceCycle,
            nowMillis = at(2026, 8, 2, 12, 0),
            zoneId = zone,
            currentBalanceCents = 500,
        )

        assertEquals(80L, summary.originalBalanceDailyCents)
        assertEquals(33L, summary.currentBalanceDailyCents)
        assertEquals(200L, summary.targetEndingBalanceCents)
    }

    @Test
    fun `daily amount is zero when current balance is below target ending balance`() {
        val balanceCycle = config.copy(
            cycleStartEpochDay = java.time.LocalDate.of(2026, 8, 1).toEpochDay(),
            cycleEndEpochDay = java.time.LocalDate.of(2026, 8, 10).toEpochDay(),
            cycleStartingBalanceCents = 1_000,
            targetEndingBalanceCents = 600,
        )

        val summary = BudgetCalculator.calculate(
            emptyList(),
            balanceCycle,
            at(2026, 8, 2, 12, 0),
            zone,
            currentBalanceCents = 500,
        )

        assertEquals(0L, summary.currentBalanceDailyCents)
    }

    private fun transaction(
        amount: Long,
        kind: TransactionKind,
        time: Long,
        fingerprint: String,
    ) = TransactionEntity(
        amountCents = amount,
        kind = kind,
        occurredAt = time,
        merchant = null,
        cardLast4 = null,
        bank = "中国银行",
        sender = "95566",
        fingerprint = fingerprint,
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone).toInstant().toEpochMilli()
}
