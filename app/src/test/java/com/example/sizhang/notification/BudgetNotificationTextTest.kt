package com.example.sizhang.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetNotificationTextTest {
    @Test
    fun createsOnlyTheThreeRequestedBudgetLines() {
        val text = BudgetNotificationText.create(
            todayAvailableCents = 18_650,
            todaySpentCents = 4_350,
            tomorrowAvailableCents = 17_220,
        )

        assertEquals("今日可用 ¥186.50", text.todayAvailable)
        assertEquals("今日已消费 ¥43.50", text.todaySpent)
        assertEquals("明日预计可花 ¥172.20", text.tomorrowAvailable)
    }
}
