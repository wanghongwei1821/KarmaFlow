package com.example.sizhang.sms

import com.example.sizhang.data.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BankSmsParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val receivedAt = LocalDateTime.of(2026, 8, 12, 13, 0)
        .atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `parses Bank of China expense`() {
        val parsed = BankSmsParser.parse(
            sender = "95566",
            body = "【中国银行】您尾号1234账户08月12日12:35消费人民币28.50元，商户：XX便利店。",
            receivedAt = receivedAt,
            zoneId = zone,
        )

        assertNotNull(parsed)
        assertEquals(2_850L, parsed?.amountCents)
        assertEquals(TransactionKind.EXPENSE, parsed?.kind)
        assertEquals("XX便利店", parsed?.merchant)
        assertEquals("1234", parsed?.cardLast4)
        assertEquals(12, parsed?.occurredAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).hour })
    }

    @Test
    fun `parses refund as refund`() {
        val parsed = BankSmsParser.parse(
            sender = "+86 95566",
            body = "中国银行：您尾号9876的账户退款人民币108.20元，商户：某平台。",
            receivedAt = receivedAt,
            zoneId = zone,
        )

        assertEquals(TransactionKind.REFUND, parsed?.kind)
        assertEquals(10_820L, parsed?.amountCents)
    }

    @Test
    fun `ignores verification code`() {
        assertNull(
            BankSmsParser.parse(
                "95566",
                "【中国银行】验证码123456，您正在支付人民币28.50元，请勿泄露。",
                receivedAt,
                zone,
            ),
        )
    }

    @Test
    fun `security footer does not hide a real expense`() {
        val parsed = BankSmsParser.parse(
            "95566",
            "【中国银行】尾号1234账户消费人民币20.00元，商户：测试商店。谨防诈骗，请勿泄露个人信息。",
            receivedAt,
            zone,
        )

        assertEquals(2_000L, parsed?.amountCents)
    }

    @Test
    fun `parses Bank of China message that only says transaction`() {
        val parsed = BankSmsParser.parse(
            "95566",
            "尊敬的客户，您尾号4321信用卡于08月12日12:40交易人民币688.00元，商户名称：某某百货。",
            receivedAt,
            zone,
        )

        assertEquals(68_800L, parsed?.amountCents)
        assertEquals("4321", parsed?.cardLast4)
        assertEquals("某某百货", parsed?.merchant)
    }

    @Test
    fun `parses masked card and POS transaction from long sender`() {
        val parsed = BankSmsParser.parse(
            "106980095566",
            "【中国银行】您的账户****6677于08月12日12:41发生POS交易，金额CNY 123.45元。",
            receivedAt,
            zone,
        )

        assertEquals(12_345L, parsed?.amountCents)
        assertEquals("6677", parsed?.cardLast4)
    }

    @Test
    fun `reports why failed transaction was ignored`() {
        val result = BankSmsParser.parseDetailed(
            "95566",
            "中国银行：尾号1234交易人民币500.00元失败。",
            receivedAt,
            zone,
        )

        assertNull(result.transaction)
        assertEquals("failed_transaction", result.resultCode)
    }

    @Test
    fun `parses exact Bank of China debit card online payment format`() {
        val result = BankSmsParser.parseDetailed(
            "95566",
            "您的借记卡账户王**，于08月13日网上支付支取人民币6.00元,交易后余额7662.14【中国银行】",
            receivedAt,
            zone,
        )
        val parsed = result.transaction

        assertEquals(600L, parsed?.amountCents)
        assertEquals(766_214L, result.balanceAfterCents)
        assertEquals(receivedAt, result.balanceObservedAt)
        assertEquals(TransactionKind.EXPENSE, parsed?.kind)
        assertEquals("网上支付", parsed?.merchant)
        assertNull(parsed?.cardLast4)
    }

    @Test
    fun `extracts balance even when message is not an expense`() {
        val result = BankSmsParser.parseDetailed(
            "95566",
            "您的借记卡账户余额为人民币8,123.45元【中国银行】",
            receivedAt,
            zone,
        )

        assertNull(result.transaction)
        assertEquals("no_expense_signal", result.resultCode)
        assertEquals(812_345L, result.balanceAfterCents)
    }

    @Test
    fun `parses online payment income and its resulting balance`() {
        val result = BankSmsParser.parseDetailed(
            "95566",
            "您的借记卡账户王宏威，于08月13日网上支付收入人民币1349.40元,交易后余额7698.64【中国银行】",
            receivedAt,
            zone,
        )

        assertEquals("income_recorded", result.resultCode)
        assertEquals(TransactionKind.INCOME, result.transaction?.kind)
        assertEquals(134_940L, result.transaction?.amountCents)
        assertEquals("网上支付", result.transaction?.merchant)
        assertEquals(769_864L, result.balanceAfterCents)
    }

    @Test
    fun `parses interbank online banking income and its resulting balance`() {
        val result = BankSmsParser.parseDetailed(
            "95566",
            "您的借记卡账户王宏威，于08月12日收入(网银跨行)人民币10000.00元,交易后余额10309.78【中国银行】",
            receivedAt,
            zone,
        )

        assertEquals("income_recorded", result.resultCode)
        assertEquals(TransactionKind.INCOME, result.transaction?.kind)
        assertEquals(1_000_000L, result.transaction?.amountCents)
        assertEquals("网银跨行", result.transaction?.merchant)
        assertEquals(1_030_978L, result.balanceAfterCents)
    }

    @Test
    fun `parses UnionPay credit income and its resulting balance`() {
        val result = BankSmsParser.parseDetailed(
            "95566",
            "您的借记卡/账户王宏威于08月13日银联入账人民币1.99元（王宏威）,交易后余额7662.13【中国银行】",
            receivedAt,
            zone,
        )

        assertEquals("income_recorded", result.resultCode)
        assertEquals(TransactionKind.INCOME, result.transaction?.kind)
        assertEquals(199L, result.transaction?.amountCents)
        assertEquals("银联入账", result.transaction?.merchant)
        assertEquals(766_213L, result.balanceAfterCents)
    }

    @Test
    fun `parses credit card HKD purchase with date merchant and card number`() {
        val result = BankSmsParser.parseDetailed(
            "95566",
            "您的信用卡5742于2026年08月11日，在FRAMER消费HKD60.00元。【中国银行】",
            receivedAt,
            zone,
        )
        val transaction = result.transaction

        assertEquals("recorded", result.resultCode)
        assertEquals(TransactionKind.EXPENSE, transaction?.kind)
        assertEquals(6_000L, transaction?.amountCents)
        assertEquals("HKD", transaction?.currency)
        assertEquals("FRAMER", transaction?.merchant)
        assertEquals("5742", transaction?.cardLast4)
        val date = transaction?.occurredAt?.let {
            java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        }
        assertEquals(java.time.LocalDate.of(2026, 8, 11), date)
    }

    @Test
    fun `parses debit card POS withdrawal and resulting balance`() {
        val result = BankSmsParser.parseDetailed(
            "95566",
            "您的借记卡账户王宏威，于08月10日POS支取人民币13.49元,交易后余额361.53【中国银行】",
            receivedAt,
            zone,
        )
        val transaction = result.transaction

        assertEquals(TransactionKind.EXPENSE, transaction?.kind)
        assertEquals(1_349L, transaction?.amountCents)
        assertEquals("CNY", transaction?.currency)
        assertEquals(36_153L, result.balanceAfterCents)
        val date = transaction?.occurredAt?.let {
            java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        }
        assertEquals(java.time.LocalDate.of(2026, 8, 10), date)
    }

    @Test
    fun `ignores non bank sender`() {
        assertNull(
            BankSmsParser.parse(
                "10690000",
                "您尾号1234账户消费人民币28.50元，商户：XX便利店。",
                receivedAt,
                zone,
            ),
        )
    }

    @Test
    fun `same sms timestamp has same fingerprint`() {
        val body = "中国银行：尾号1234消费人民币10.00元，商户：测试商店。"
        val first = BankSmsParser.parse("95566", body, receivedAt, zone)
        val second = BankSmsParser.parse("95566", body, receivedAt, zone)

        assertEquals(first?.fingerprint, second?.fingerprint)
    }
}
