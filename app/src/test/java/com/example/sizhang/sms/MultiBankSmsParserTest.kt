package com.example.sizhang.sms

import com.example.sizhang.data.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MultiBankSmsParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val receivedAt = LocalDateTime.of(2026, 8, 17, 23, 0)
        .atZone(zone).toInstant().toEpochMilli()

    private data class Case(
        val sender: String,
        val body: String,
        val bank: String,
        val kind: TransactionKind,
        val amountCents: Long,
        val balanceCents: Long,
        val cardLast4: String,
    )

    @Test
    fun `parses supported debit card formats across seven banks`() {
        val cases = listOf(
            Case("95588", "【工商银行】您尾号 1234 卡 08‑17 14:30 快捷支付支出人民币 200.00 元，余额 8652.30 元。如有疑问请致电 95588。", "中国工商银行", TransactionKind.EXPENSE, 20_000, 865_230, "1234"),
            Case("95588", "【工商银行】您尾号 1234 卡 08‑17 21:10POS 退货入账 399.00 元，余额 8362.30 元。如有疑问请致电 95588。", "中国工商银行", TransactionKind.REFUND, 39_900, 836_230, "1234"),
            Case("95588", "【工商银行】您尾号 1234 卡 08‑17 09:15 转账存入人民币 6500.00 元，余额 15152.30 元。如有疑问请致电 95588。", "中国工商银行", TransactionKind.INCOME, 650_000, 1_515_230, "1234"),
            Case("95588", "【工商银行】您尾号 1234 卡扣收短信服务费 3.00 元，余额 41799.45 元。如有疑问请致电 95588。", "中国工商银行", TransactionKind.EXPENSE, 300, 4_179_945, "1234"),
            Case("95588", "【工商银行】您尾号 1234 卡 08‑17 22:00 信用卡自动还款支出 5280.00 元，余额 37208.45 元。如有疑问请致电 95588。", "中国工商银行", TransactionKind.EXPENSE, 528_000, 3_720_845, "1234"),

            Case("95533", "【中国建设银行】您尾号 4321 账户 08 月 17 日 14:32 消费支出 356.80 元，当前余额 7234.11 元。客服 95533。", "中国建设银行", TransactionKind.EXPENSE, 35_680, 723_411, "4321"),
            Case("95533", "【中国建设银行】您尾号 4321 账户于 08‑17 10:20 收到转账汇入人民币 1200.00 元，余额 8434.11 元。客服 95533。", "中国建设银行", TransactionKind.INCOME, 120_000, 843_411, "4321"),
            Case("95533", "【中国建设银行】您尾号 4321 账户 08‑17 11:35 柜面取款 2000.00 元，当前余额 3689.81 元。客服 95533。", "中国建设银行", TransactionKind.EXPENSE, 200_000, 368_981, "4321"),
            Case("95533", "【中国建设银行】您尾号 4321 账户 08‑17 15:22 预授权消费 800.00 元，当前余额 1389.81 元。客服 95533。", "中国建设银行", TransactionKind.EXPENSE, 80_000, 138_981, "4321"),
            Case("95533", "【中国建设银行】您尾号 4321 账户 08‑17 19:30 交易冲正入账 1299.00 元，当前余额 2702.03 元。客服 95533。", "中国建设银行", TransactionKind.REFUND, 129_900, 270_203, "4321"),

            Case("95599", "【中国农业银行】您尾号 5678 账户 08‑17 15:05 完成转账支出 800.00 元，账户余额 4321.90 元。详询 95599。", "中国农业银行", TransactionKind.EXPENSE, 80_000, 432_190, "5678"),
            Case("95599", "【中国农业银行】您尾号 5678 账户 08‑17 10:40 柜面现金存入 8000.00 元，账户余额 8213.90 元。详询 95599。", "中国农业银行", TransactionKind.INCOME, 800_000, 821_390, "5678"),
            Case("95599", "【中国农业银行】您尾号 5678 账户 08‑17 06:45 代扣保险费 3650.00 元，账户余额 213.90 元。详询 95599。", "中国农业银行", TransactionKind.EXPENSE, 365_000, 21_390, "5678"),
            Case("95599", "【中国农业银行】您尾号 5678 账户 08‑17 16:30 定期转活期入账 20000.00 元，账户余额 28213.90 元。详询 95599。", "中国农业银行", TransactionKind.INCOME, 2_000_000, 2_821_390, "5678"),

            Case("95566", "【中国银行】您尾号 8765 账户 08‑17 16:10 快捷支付扣取 168.50 元，余额 5678.22 元。如有疑问致电 95566。", "中国银行", TransactionKind.EXPENSE, 16_850, 567_822, "8765"),
            Case("95566", "【中国银行】您尾号 8765 账户 08‑17 11:25 汇入人民币 2500.00 元，余额 8178.22 元。如有疑问致电 95566。", "中国银行", TransactionKind.INCOME, 250_000, 817_822, "8765"),
            Case("95566", "【中国银行】您尾号 8765 账户结息入账 22.36 元，余额 4248.08 元。如有疑问致电 95566。", "中国银行", TransactionKind.INCOME, 2_236, 424_808, "8765"),

            Case("95555", "【招商银行】您尾号 2345 的一卡通 08‑17 14:45 消费支出 59.90 元，余额 3456.78 元。咨询 95555。", "招商银行", TransactionKind.EXPENSE, 5_990, 345_678, "2345"),
            Case("95555", "【招商银行】您尾号 2345 账户入账人民币 4000.00 元，当前余额 7456.78 元。咨询 95555。", "招商银行", TransactionKind.INCOME, 400_000, 745_678, "2345"),
            Case("95555", "【招商银行】您尾号 2345 一卡通 08‑17 15:18 理财申购支出 10000.00 元，余额‑8060.22 元。咨询 95555。", "招商银行", TransactionKind.EXPENSE, 1_000_000, -806_022, "2345"),
            Case("95555", "【招商银行】您尾号 2345 一卡通 08‑17 16:20 理财赎回入账 15000.00 元，余额 6939.78 元。咨询 95555。", "招商银行", TransactionKind.INCOME, 1_500_000, 693_978, "2345"),

            Case("95559", "【交通银行】您尾号 3456 账户 08‑17 19:12POS 消费支出 865.00 元，余额 5255.50 元。客服 95559。", "交通银行", TransactionKind.EXPENSE, 86_500, 525_550, "3456"),
            Case("95559", "【交通银行】您尾号 3456 账户 08‑17 10:10 转账汇入 3000.00 元，余额 7990.50 元。客服 95559。", "交通银行", TransactionKind.INCOME, 300_000, 799_050, "3456"),
            Case("95559", "【交通银行】您尾号 3456 账户 08‑17 18:10ATM 取款 2000.00 元，余额 11590.50 元。客服 95559。", "交通银行", TransactionKind.EXPENSE, 200_000, 1_159_050, "3456"),

            Case("95580", "【邮储银行】您尾号 6789 账户 08‑17 18:35POS 消费支出 520.00 元，余额 9160.35 元，客服 95580。", "邮储银行", TransactionKind.EXPENSE, 52_000, 916_035, "6789"),
            Case("95580", "【邮储银行】您尾号 6789 账户 08‑17 09:40 转账收入 1500.00 元，余额 9680.35 元，客服 95580。", "邮储银行", TransactionKind.INCOME, 150_000, 968_035, "6789"),
            Case("95580", "【邮储银行】您尾号 6789 账户 07:30 代扣扣费 98.50 元，余额 9061.85 元，客服 95580。", "邮储银行", TransactionKind.EXPENSE, 9_850, 906_185, "6789"),
        )

        cases.forEach { case ->
            val result = BankSmsParser.parseDetailed(case.sender, case.body, receivedAt, zone)
            val transaction = result.transaction
            assertNotNull("Failed to parse: ${case.body}", transaction)
            assertEquals(case.bank, result.bank)
            assertEquals(case.bank, transaction?.bank)
            assertEquals(case.cardLast4, result.cardLast4)
            assertEquals(case.cardLast4, transaction?.cardLast4)
            assertEquals(case.kind, transaction?.kind)
            assertEquals(case.amountCents, transaction?.amountCents)
            assertEquals(case.balanceCents, result.balanceAfterCents)
        }
    }

    @Test
    fun `parses non breaking dash date and preserves operation time`() {
        val result = BankSmsParser.parseDetailed(
            "95588",
            "【工商银行】您尾号 1234 卡 08‑17 19:22POS 消费支出人民币 689.00 元，余额 7963.30 元。",
            receivedAt,
            zone,
        )
        val occurred = Instant.ofEpochMilli(result.transaction?.occurredAt ?: 0).atZone(zone)

        assertEquals(LocalDate.of(2026, 8, 17), occurred.toLocalDate())
        assertEquals(19, occurred.hour)
        assertEquals(22, occurred.minute)
    }

    @Test
    fun `does not parse unrelated sender with no supported bank signature`() {
        val result = BankSmsParser.parseDetailed(
            "10690000",
            "您尾号 1234 卡消费支出人民币 200.00 元，余额 8652.30 元。",
            receivedAt,
            zone,
        )

        assertEquals("not_supported_bank", result.resultCode)
        assertEquals(null, result.transaction)
    }
}
