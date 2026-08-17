package com.example.sizhang.sms

import com.example.sizhang.data.TransactionKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class ParsedBankTransaction(
    val amountCents: Long,
    val kind: TransactionKind,
    val occurredAt: Long,
    val merchant: String?,
    val cardLast4: String?,
    val bank: String,
    val sender: String,
    val fingerprint: String,
    val currency: String,
)

data class BankSmsParseResult(
    val transaction: ParsedBankTransaction? = null,
    val resultCode: String,
    val balanceAfterCents: Long? = null,
    val balanceObservedAt: Long = 0,
    val bank: String? = null,
    val cardLast4: String? = null,
)

object BankSmsParser {
    private data class SupportedBank(
        val name: String,
        val serviceNumber: String,
        val aliases: List<String>,
    )

    private val supportedBanks = listOf(
        SupportedBank("中国工商银行", "95588", listOf("中国工商银行", "工商银行", "工行", "ICBC")),
        SupportedBank("中国建设银行", "95533", listOf("中国建设银行", "建设银行", "建行", "CCB")),
        SupportedBank("中国农业银行", "95599", listOf("中国农业银行", "农业银行", "农行", "ABC")),
        SupportedBank("中国银行", "95566", listOf("中国银行股份有限公司", "中国银行", "中行", "BOC")),
        SupportedBank("招商银行", "95555", listOf("招商银行", "招行", "CMB")),
        SupportedBank("交通银行", "95559", listOf("交通银行", "交行", "BOCOM")),
        SupportedBank("邮储银行", "95580", listOf("中国邮政储蓄银行", "邮政储蓄银行", "邮储银行", "邮储", "PSBC")),
    )
    private val refundWords = listOf("退款", "退货", "冲正", "撤销", "退回")
    private val expenseWords = listOf(
        "消费", "支付", "扣款", "扣费", "扣收", "代扣", "支出", "取现", "取款",
        "支取", "转出", "出账", "申购", "POS", "快捷付", "银联交易", "发生交易",
        "交易成功", "交易",
    )
    private val hardIgnoreWords = listOf(
        "验证码", "动态口令", "校验码", "登录密码", "短信密码", "激活码",
    )
    private val nonPurchaseWords = listOf("还款成功", "账单分期")
    private val incomeWords = listOf(
        "工资", "代发", "转入", "汇入", "存入", "收入", "入账", "结息", "赎回",
    )
    private val failedWords = listOf("失败", "未成功", "交易拒绝", "未完成")

    private val keywordAmountRegex = Regex(
        """(?:消费|支付|扣款|扣费|扣收|代扣|退款|退货|冲正|撤销|取现|取款|支取|转出|支出|出账|存入|汇入|收入|入账|工资|结息|赎回|申购|交易金额|金额)[^0-9]{0,32}(?:人民币|RMB|CNY|￥|¥)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:元)?""",
        RegexOption.IGNORE_CASE,
    )
    private val currencyAmountRegex = Regex(
        """(?:人民币|RMB|CNY|￥|¥)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:元)?""",
        RegexOption.IGNORE_CASE,
    )
    private val foreignCurrencyAmountRegex = Regex(
        """([A-Z]{3})\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:元)?""",
        RegexOption.IGNORE_CASE,
    )
    private val balanceRegex = Regex(
        """(?:交易后余额|当前余额|账户余额|余额)\s*(?:为|[:：])?\s*(?:人民币|RMB|CNY|￥|¥)?\s*([-+]?[0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:元)?""",
        RegexOption.IGNORE_CASE,
    )
    private val cardRegex = Regex("""(?:尾号|末四位|卡号末四位|账号末四位)[^0-9]{0,8}([0-9]{4})(?![0-9])""")
    private val directCreditCardRegex = Regex("""信用卡\s*([0-9]{4})(?![0-9])""")
    private val parenthesizedCardRegex = Regex("""(?:卡|账户|账号)[（(\[](?:[*xX·•]+)?([0-9]{4})[）)\]]""")
    private val maskedCardRegex = Regex("""(?:卡|账户|账号)[^，。,；;\n]{0,12}[*xX·•]{2,}([0-9]{4})(?![0-9])""")
    private val merchantRegexes = listOf(
        Regex("""(?:商户名称|商户|交易对方|收款方|对方户名)\s*[:：为]?\s*([^，。,；;\n]{2,40})"""),
        Regex("""(?:在|于)\s*([^，。,；;\n]{2,32}?)\s*(?:消费|支付|发生交易)"""),
    )
    private val incomeChannelRegex = Regex("""收入[（(]([^）)\n]{2,30})[）)]""")
    private val fullDateTimeRegex = Regex(
        """(20[0-9]{2})[年/\-.]([01]?[0-9])[月/\-.]([0-3]?[0-9])日?\s*([0-2]?[0-9])[:：]([0-5][0-9])(?::([0-5][0-9]))?""",
    )
    private val fullDateRegex = Regex(
        """(20[0-9]{2})[年/\-.]([01]?[0-9])[月/\-.]([0-3]?[0-9])日?""",
    )
    private val monthDayTimeRegex = Regex(
        """([01]?[0-9])\s*月\s*([0-3]?[0-9])\s*日\s*([0-2]?[0-9])[:：]([0-5][0-9])(?::([0-5][0-9]))?""",
    )
    private val monthDayRegex = Regex("""([01]?[0-9])\s*月\s*([0-3]?[0-9])\s*日""")
    private val numericMonthDayTimeRegex = Regex(
        """(?<![0-9])([01]?[0-9])-([0-3]?[0-9])\s*([0-2]?[0-9])[:：]([0-5][0-9])(?::([0-5][0-9]))?""",
    )
    private val numericMonthDayRegex = Regex("""(?<![0-9])([01]?[0-9])-([0-3]?[0-9])(?![0-9])""")

    fun parse(
        sender: String,
        body: String,
        receivedAt: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ParsedBankTransaction? = parseDetailed(sender, body, receivedAt, zoneId).transaction

    fun parseDetailed(
        sender: String,
        body: String,
        receivedAt: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): BankSmsParseResult {
        val cleanBody = body
            .replace('\u00A0', ' ')
            .replace('\u2011', '-')
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u2212', '-')
            .trim()
        if (cleanBody.isBlank()) return BankSmsParseResult(resultCode = "empty")
        val supportedBank = identifyBank(sender, cleanBody)
            ?: return BankSmsParseResult(resultCode = "not_supported_bank")
        val cardLast4 = extractCardLast4(cleanBody)
        val balanceAfterCents = extractBalanceCents(cleanBody)
        fun result(
            resultCode: String,
            transaction: ParsedBankTransaction? = null,
        ) = BankSmsParseResult(
            transaction = transaction,
            resultCode = resultCode,
            balanceAfterCents = balanceAfterCents,
            balanceObservedAt = receivedAt,
            bank = supportedBank.name,
            cardLast4 = cardLast4,
        )
        if (hardIgnoreWords.any(cleanBody::contains)) {
            return result(resultCode = "security_code")
        }
        if (failedWords.any(cleanBody::contains)) {
            return result(resultCode = "failed_transaction")
        }

        val isRefund = refundWords.any(cleanBody::contains)
        val isIncome = incomeWords.any(cleanBody::contains)
        val hasExpenseSignal = expenseWords.any { cleanBody.contains(it, ignoreCase = true) }
        if (!isRefund && !isIncome && !hasExpenseSignal) {
            return result(resultCode = "no_expense_signal")
        }
        if (!isRefund && !isIncome && nonPurchaseWords.any(cleanBody::contains)) {
            return result(resultCode = "repayment")
        }

        val amount = extractAmount(cleanBody)
            ?: return result(resultCode = "amount_not_found")
        val amountCents = amount.second
        if (amountCents <= 0L) return result(resultCode = "invalid_amount")

        val occurredAt = extractOccurredAt(cleanBody, receivedAt, zoneId)
        val normalizedSender = sender.trim().ifBlank { supportedBank.serviceNumber }
        val parsed = ParsedBankTransaction(
            amountCents = amountCents,
            kind = when {
                isRefund -> TransactionKind.REFUND
                isIncome -> TransactionKind.INCOME
                else -> TransactionKind.EXPENSE
            },
            occurredAt = occurredAt,
            merchant = extractMerchant(cleanBody, isIncome),
            cardLast4 = cardLast4,
            bank = supportedBank.name,
            sender = normalizedSender,
            fingerprint = fingerprint(normalizedSender, cleanBody, receivedAt),
            currency = amount.first,
        )
        return result(
            transaction = parsed,
            resultCode = if (isIncome && !isRefund) "income_recorded" else "recorded",
        )
    }

    private fun identifyBank(sender: String, body: String): SupportedBank? {
        val senderDigits = sender.filter(Char::isDigit)
        return supportedBanks.firstOrNull { bank -> senderDigits.contains(bank.serviceNumber) }
            ?: supportedBanks.firstOrNull { bank ->
                bank.aliases.any { alias -> body.contains(alias, ignoreCase = true) }
            }
            ?: supportedBanks.firstOrNull { bank -> body.contains(bank.serviceNumber) }
    }

    private fun extractCardLast4(body: String): String? =
        cardRegex.find(body)?.groupValues?.get(1)
            ?: directCreditCardRegex.find(body)?.groupValues?.get(1)
            ?: parenthesizedCardRegex.find(body)?.groupValues?.get(1)
            ?: maskedCardRegex.find(body)?.groupValues?.get(1)

    private fun extractAmount(body: String): Pair<String, Long>? {
        foreignCurrencyAmountRegex.find(body)?.let { match ->
            val currency = match.groupValues[1].uppercase()
            if (currency != "CNY" && currency != "RMB") {
                return parseCents(match.groupValues[2])?.let { currency to it }
            }
        }
        val match = keywordAmountRegex.find(body) ?: currencyAmountRegex.find(body) ?: return null
        return parseCents(match.groupValues[1])?.let { "CNY" to it }
    }

    private fun extractBalanceCents(body: String): Long? {
        val match = balanceRegex.find(body) ?: return null
        return parseCents(match.groupValues[1])
    }

    private fun parseCents(value: String): Long? {
        return try {
            BigDecimal(value.replace(",", ""))
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun extractMerchant(body: String, isIncome: Boolean): String? {
        if (body.contains("网上支付")) return "网上支付"
        if (isIncome) {
            incomeChannelRegex.find(body)?.groupValues?.get(1)?.trim()?.let { return it }
            if (body.contains("银联入账")) return "银联入账"
            if (body.contains("工资")) return "工资收入"
            if (body.contains("利息入账")) return "利息入账"
        }
        return merchantRegexes.firstNotNullOfOrNull { regex ->
            regex.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('。', '，', ',', ';', '；')
                ?.takeIf { candidate ->
                    candidate.length in 2..40 &&
                        !candidate.contains("余额") &&
                        !candidate.contains("人民币")
                }
        }
    }

    private fun extractOccurredAt(body: String, receivedAt: Long, zoneId: ZoneId): Long {
        val received = Instant.ofEpochMilli(receivedAt).atZone(zoneId)
        try {
            fullDateTimeRegex.find(body)?.let { match ->
                return LocalDateTime.of(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                    match.groupValues[4].toInt(),
                    match.groupValues[5].toInt(),
                    match.groupValues[6].ifBlank { "0" }.toInt(),
                ).atZone(zoneId).toInstant().toEpochMilli()
            }
            monthDayTimeRegex.find(body)?.let { match ->
                var candidate = LocalDateTime.of(
                    received.year,
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                    match.groupValues[4].toInt(),
                    match.groupValues[5].ifBlank { "0" }.toInt(),
                ).atZone(zoneId)
                if (candidate.isAfter(received.plusDays(2))) candidate = candidate.minusYears(1)
                return candidate.toInstant().toEpochMilli()
            }
            numericMonthDayTimeRegex.find(body)?.let { match ->
                var candidate = LocalDateTime.of(
                    received.year,
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                    match.groupValues[4].toInt(),
                    match.groupValues[5].ifBlank { "0" }.toInt(),
                ).atZone(zoneId)
                if (candidate.isAfter(received.plusDays(2))) candidate = candidate.minusYears(1)
                return candidate.toInstant().toEpochMilli()
            }
            fullDateRegex.find(body)?.let { match ->
                return LocalDateTime.of(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                    12,
                    0,
                ).atZone(zoneId).toInstant().toEpochMilli()
            }
            monthDayRegex.find(body)?.let { match ->
                var candidate = LocalDateTime.of(
                    received.year,
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    12,
                    0,
                ).atZone(zoneId)
                if (candidate.isAfter(received.plusDays(2))) candidate = candidate.minusYears(1)
                return candidate.toInstant().toEpochMilli()
            }
            numericMonthDayRegex.find(body)?.let { match ->
                var candidate = LocalDateTime.of(
                    received.year,
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    12,
                    0,
                ).atZone(zoneId)
                if (candidate.isAfter(received.plusDays(2))) candidate = candidate.minusYears(1)
                return candidate.toInstant().toEpochMilli()
            }
        } catch (_: DateTimeException) {
            // A malformed date should not discard an otherwise valid transaction.
        } catch (_: NumberFormatException) {
            // Fall back to the SMS timestamp.
        }
        return receivedAt
    }

    private fun fingerprint(sender: String, body: String, receivedAt: Long): String {
        val normalizedBody = body.lowercase().replace(Regex("""\s+"""), "")
        val input = "$sender|$normalizedBody|$receivedAt"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
