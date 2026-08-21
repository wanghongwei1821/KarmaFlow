package com.example.sizhang.notification

import com.example.sizhang.data.NotificationDisplaySettings
import com.example.sizhang.ui.BudgetSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetNotificationTextTest {
    @Test
    fun defaultSettingsShowOnlyTodaySpent() {
        val text = BudgetNotificationText.create(
            summary = sampleSummary(),
            settings = NotificationDisplaySettings(),
        )

        assertEquals(listOf("今日已消费 ¥43.50"), text.lines)
        assertEquals("今日已消费 ¥43.50", text.title)
        assertEquals("", text.details)
    }

    @Test
    fun selectedMetricsAreRenderedInStableOrder() {
        val text = BudgetNotificationText.create(
            summary = sampleSummary(),
            settings = NotificationDisplaySettings(
                showTodaySpent = true,
                showTodayAvailable = true,
                showTomorrowAvailable = true,
                showCurrentBalance = true,
                showCycleSpent = true,
                showDistributableBalance = true,
                showTargetBalance = true,
                showReservedAmount = true,
                showRemainingDays = true,
            ),
        )

        assertEquals(
            listOf(
                "今日已消费 ¥43.50",
                "今日可用 ¥186.50",
                "明日预计可花 ¥172.20",
                "当前余额 ¥7662.14",
                "本期已消费 ¥1225.80",
                "可分配余额 ¥4962.14",
                "目标结余 ¥1000.00",
                "预留金额 ¥1700.00",
                "周期剩余 10 天",
            ),
            text.lines,
        )
    }

    @Test
    fun disabledNotificationProducesNoLines() {
        val text = BudgetNotificationText.create(
            summary = sampleSummary(),
            settings = NotificationDisplaySettings(enabled = false),
        )

        assertEquals(emptyList<String>(), text.lines)
    }

    private fun sampleSummary() = BudgetSummary(
        todaySpentCents = 4_350,
        monthSpentCents = 122_580,
        monthRemainingCents = 496_214,
        todayAvailableCents = 18_650,
        currentBalanceCents = 766_214,
        targetEndingBalanceCents = 100_000,
        reservedCents = 170_000,
        remainingCycleDays = 10,
        tomorrowAvailableCents = 17_220,
    )
}
