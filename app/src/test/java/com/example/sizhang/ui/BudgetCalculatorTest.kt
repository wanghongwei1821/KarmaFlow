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
    fun `without an account balance no periodic budget is invented`() {
        val summary = BudgetCalculator.calculate(emptyList(), config, now, zone)

        assertEquals(270_000, summary.reservedCents)
        assertEquals(0, summary.monthRemainingCents)
        assertEquals(0, summary.dailyTargetCents)
        assertEquals(0, summary.todayAvailableCents)
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
    fun `today spending changes totals but not the locked today allowance`() {
        val transactions = listOf(
            transaction(10_000, TransactionKind.EXPENSE, at(2026, 8, 5, 12, 0), "old"),
            transaction(2_850, TransactionKind.EXPENSE, at(2026, 8, 12, 12, 0), "today"),
            transaction(500, TransactionKind.REFUND, at(2026, 8, 12, 13, 0), "refund"),
        )
        val summary = BudgetCalculator.calculate(
            transactions,
            config,
            now,
            zone,
            currentBalanceCents = 700_000,
            dayStartBalanceCents = 700_000,
        )

        assertEquals(12_350, summary.monthSpentCents)
        assertEquals(2_350, summary.todaySpentCents)
        assertEquals(21_500, summary.dailyTargetCents)
        assertEquals(21_500, summary.todayAvailableCents)
    }

    @Test
    fun `balance is spread across remaining days while original plan uses all cycle days`() {
        val balanceCycle = config.copy(
            cycleStartEpochDay = java.time.LocalDate.of(2026, 8, 1).toEpochDay(),
            cycleEndEpochDay = java.time.LocalDate.of(2026, 8, 10).toEpochDay(),
            cycleStartingBalanceCents = 1_000,
            reservedItems = emptyList(),
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
            reservedItems = emptyList(),
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
        assertEquals(900L, afterFirstExpense.tomorrowDistributableCents)
        assertEquals(8, afterFirstExpense.tomorrowRemainingDays)
        assertEquals(
            88L,
            BudgetCalculator.forecastTomorrowAvailable(
                distributableCents = afterFirstExpense.tomorrowDistributableCents,
                remainingDays = afterFirstExpense.tomorrowRemainingDays,
                additionalSpendCents = 200,
            ),
        )
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

        val summary = BudgetCalculator.calculate(
            transactions,
            config.copy(
                cycleStartingBalanceCents = 100_000,
                reservedItems = emptyList(),
            ),
            now,
            zone,
            currentBalanceCents = 98_900,
        )

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
            4_545L,
            summary.dailySpending.first { it.date == java.time.LocalDate.of(2026, 8, 10) }.expectedCents,
        )
        assertEquals(
            4_724L,
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
    fun `daily and tomorrow amounts can be negative when target exceeds balance`() {
        val balanceCycle = config.copy(
            cycleStartEpochDay = java.time.LocalDate.of(2026, 8, 1).toEpochDay(),
            cycleEndEpochDay = java.time.LocalDate.of(2026, 8, 10).toEpochDay(),
            cycleStartingBalanceCents = 1_000,
            targetEndingBalanceCents = 600,
            reservedItems = emptyList(),
        )

        val summary = BudgetCalculator.calculate(
            emptyList(),
            balanceCycle,
            at(2026, 8, 2, 12, 0),
            zone,
            currentBalanceCents = 500,
        )

        assertEquals(-11L, summary.currentBalanceDailyCents)
        assertEquals(-13L, summary.tomorrowAvailableCents)
    }

    @Test
    fun `monthly budget no longer changes a balance driven allowance`() {
        val balanceCycle = config.copy(reservedItems = emptyList())
        val smallBudget = BudgetCalculator.calculate(
            emptyList(),
            balanceCycle.copy(monthlyBudgetCents = 1),
            now,
            zone,
            currentBalanceCents = 100_000,
            dayStartBalanceCents = 100_000,
        )
        val largeBudget = BudgetCalculator.calculate(
            emptyList(),
            balanceCycle.copy(monthlyBudgetCents = 100_000_000),
            now,
            zone,
            currentBalanceCents = 100_000,
            dayStartBalanceCents = 100_000,
        )

        assertEquals(smallBudget.todayAvailableCents, largeBudget.todayAvailableCents)
        assertEquals(5_000L, smallBudget.todayAvailableCents)
    }

    @Test
    fun `excluded transaction stays in history but is omitted from spending totals`() {
        val transactions = listOf(
            transaction(2_000, TransactionKind.EXPENSE, at(2026, 8, 12, 10, 0), "included"),
            transaction(9_000, TransactionKind.EXPENSE, at(2026, 8, 12, 11, 0), "excluded")
                .copy(isExcluded = true),
        )

        val summary = BudgetCalculator.calculate(transactions, config, now, zone)

        assertEquals(2_000L, summary.todaySpentCents)
        assertEquals(2_000L, summary.monthSpentCents)
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
