package com.example.sizhang.data

import com.example.sizhang.sms.ParsedBankTransaction
import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val transactionDao: TransactionDao,
    private val budgetStore: BudgetStore,
    private val smsMonitorStore: SmsMonitorStore,
    private val accountBalanceStore: AccountBalanceStore,
) {
    val transactions: Flow<List<TransactionEntity>> = transactionDao.observeAll()
    val budgetConfig: Flow<BudgetConfig> = budgetStore.config
    val smsMonitor: Flow<SmsMonitorState> = smsMonitorStore.state
    val accountBalance: Flow<AccountBalance> = accountBalanceStore.balance

    suspend fun saveSmsTransaction(parsed: ParsedBankTransaction): Boolean {
        val rowId = transactionDao.insert(
            TransactionEntity(
                amountCents = parsed.amountCents,
                kind = parsed.kind,
                occurredAt = parsed.occurredAt,
                merchant = parsed.merchant,
                cardLast4 = parsed.cardLast4,
                bank = parsed.bank,
                sender = parsed.sender,
                fingerprint = parsed.fingerprint,
                currency = parsed.currency,
            ),
        )
        return rowId != -1L
    }

    suspend fun updateBudget(config: BudgetConfig) = budgetStore.update(config)

    suspend fun updateBalanceFromSms(amountCents: Long, observedAt: Long) =
        accountBalanceStore.updateFromSms(amountCents, observedAt)

    suspend fun updateBalanceManually(amountCents: Long) =
        accountBalanceStore.updateManually(amountCents)

    suspend fun recordSmsAttempt(sender: String, resultCode: String) =
        smsMonitorStore.record(sender, resultCode)

    suspend fun delete(transaction: TransactionEntity) = transactionDao.delete(transaction)
}
