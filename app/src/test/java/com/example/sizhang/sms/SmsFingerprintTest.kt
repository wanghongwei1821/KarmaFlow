package com.example.sizhang.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.ZoneId

class SmsFingerprintTest {
    private val body = "【中国银行】账户消费人民币28.50元，商户：测试便利店。"
    private val timestamp = 1789461928000L

    @Test
    fun `live and inbox versions share identity but retain receipt time`() {
        val live = BankSmsParser.parseDetailed("95566", body, timestamp).transaction!!
        val inbox = BankSmsParser.parseDetailed(
            "95566", body, timestamp + 245,
            messageTimestamp = timestamp,
        ).transaction!!
        assertEquals(live.fingerprint, inbox.fingerprint)
        assertEquals(timestamp + 245, inbox.occurredAt)
    }

    @Test
    fun `identical purchases in separate messages remain distinct`() {
        val first = BankSmsParser.parseDetailed("95566", body, timestamp).transaction!!
        val second = BankSmsParser.parseDetailed("95566", body, timestamp + 60_000).transaction!!
        assertNotEquals(first.fingerprint, second.fingerprint)
    }
}
