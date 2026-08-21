package com.example.sizhang.data

import com.example.sizhang.sms.ParsedBankTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

class LedgerRepository(
    private val transactionDao: TransactionDao,
    private val bankAccountDao: BankAccountDao,
    private val budgetStore: BudgetStore,
    private val smsMonitorStore: SmsMonitorStore,
    private val accountBalanceStore: AccountBalanceStore,
    private val notificationSettingsStore: NotificationSettingsStore,
) {
    val transactions: Flow<List<TransactionEntity>> = transactionDao.observeAll()
    val bankAccounts: Flow<List<BankAccountEntity>> = bankAccountDao.observeAll()
    val budgetConfig: Flow<BudgetConfig> = budgetStore.config
    val smsMonitor: Flow<SmsMonitorState> = smsMonitorStore.state
    val accountBalance: Flow<AccountBalance> = accountBalanceStore.balance
    val notificationSettings: Flow<NotificationDisplaySettings> = notificationSettingsStore.settings

    suspend fun saveSmsTransaction(parsed: ParsedBankTransaction): Boolean {
        val cardLast4 = UNKNOWN_CARD_LAST4
        bankAccountDao.insert(
            BankAccountEntity(
                accountKey = bankAccountKey(parsed.bank, cardLast4),
                bank = parsed.bank,
                cardLast4 = cardLast4,
                updatedAt = parsed.occurredAt,
            ),
        )
        val rowId = transactionDao.insert(
            TransactionEntity(
                amountCents = parsed.amountCents,
                kind = parsed.kind,
                occurredAt = parsed.occurredAt,
                merchant = parsed.merchant,
                cardLast4 = null,
                bank = parsed.bank,
                sender = parsed.sender,
                fingerprint = parsed.fingerprint,
                currency = parsed.currency,
            ),
        )
        return rowId != -1L
    }

    suspend fun updateBudget(config: BudgetConfig) = budgetStore.update(config)

    suspend fun updateNotificationSettings(settings: NotificationDisplaySettings) =
        notificationSettingsStore.update(settings)

    suspend fun updateBalanceFromSms(
        amountCents: Long,
        observedAt: Long,
        bank: String? = null,
    ) {
        if (observedAt <= 0) return
        if (bank == null) {
            accountBalanceStore.updateFromSms(amountCents, observedAt)
            return
        }
        val resolvedCardLast4 = UNKNOWN_CARD_LAST4
        val snapshotEpochDay = epochDay(observedAt)
        val accountKey = bankAccountKey(bank, resolvedCardLast4)
        bankAccountDao.insert(
            BankAccountEntity(
                accountKey = accountKey,
                bank = bank,
                cardLast4 = resolvedCardLast4,
                balanceCents = amountCents,
                updatedAt = observedAt,
                dayStartBalanceCents = amountCents,
                snapshotEpochDay = snapshotEpochDay,
            ),
        )
        bankAccountDao.updateBalanceIfNewer(
            accountKey = accountKey,
            balanceCents = amountCents,
            observedAt = observedAt,
            snapshotEpochDay = snapshotEpochDay,
        )
    }

    suspend fun updateBalanceManually(amountCents: Long) =
        accountBalanceStore.updateManually(amountCents)

    suspend fun ensureDailyBalanceSnapshot(nowMillis: Long = System.currentTimeMillis()) {
        accountBalanceStore.ensureDailySnapshot(nowMillis)
        bankAccountDao.ensureDailySnapshots(epochDay(nowMillis))
    }

    suspend fun refreshDailyBalanceSnapshot(nowMillis: Long = System.currentTimeMillis()) {
        accountBalanceStore.refreshDailySnapshot(nowMillis)
        bankAccountDao.refreshDailySnapshots(epochDay(nowMillis))
    }

    suspend fun recordSmsAttempt(sender: String, resultCode: String) =
        smsMonitorStore.record(sender, resultCode)

    suspend fun delete(transaction: TransactionEntity) = transactionDao.delete(transaction)

    suspend fun setTransactionExcluded(id: Long, excluded: Boolean) =
        transactionDao.setExcluded(id, excluded)

    private fun epochDay(timestamp: Long): Long = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()
}
