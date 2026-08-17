package com.example.sizhang.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sizhang.PrivateLedgerApplication
import com.example.sizhang.data.BudgetConfig
import com.example.sizhang.data.AccountBalance
import com.example.sizhang.data.BalanceSource
import com.example.sizhang.data.BankAccountEntity
import com.example.sizhang.data.TransactionEntity
import com.example.sizhang.data.SmsMonitorState
import com.example.sizhang.data.UNKNOWN_CARD_LAST4
import com.example.sizhang.sms.SmsInboxSynchronizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class LedgerUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val budget: BudgetConfig = BudgetConfig(),
    val summary: BudgetSummary = BudgetSummary(),
    val smsMonitor: SmsMonitorState = SmsMonitorState(),
    val accountBalance: AccountBalance = AccountBalance(),
    val bankAccounts: List<BankAccountEntity> = emptyList(),
)

private data class LedgerData(
    val transactions: List<TransactionEntity>,
    val budget: BudgetConfig,
    val smsMonitor: SmsMonitorState,
    val legacyBalance: AccountBalance,
    val bankAccounts: List<BankAccountEntity>,
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as PrivateLedgerApplication).repository
    private val now = MutableStateFlow(System.currentTimeMillis())
    private var smsSyncJob: Job? = null

    init {
        viewModelScope.launch { repository.ensureDailyBalanceSnapshot(now.value) }
    }

    private val ledgerData = combine(
        repository.transactions,
        repository.budgetConfig,
        repository.smsMonitor,
        repository.accountBalance,
        repository.bankAccounts,
    ) { transactions, budget, smsMonitor, legacyBalance, bankAccounts ->
        LedgerData(transactions, budget, smsMonitor, legacyBalance, bankAccounts)
    }

    val uiState = combine(ledgerData, now) { data, currentTime ->
        val visibleBankAccounts = collapseToOneAccountPerBank(data.bankAccounts)
        val accountBalance = effectiveBalance(data.legacyBalance, visibleBankAccounts)
        LedgerUiState(
            transactions = data.transactions,
            budget = data.budget,
            summary = BudgetCalculator.calculate(
                transactions = data.transactions,
                config = data.budget,
                nowMillis = currentTime,
                currentBalanceCents = accountBalance.amountCents,
                dayStartBalanceCents = accountBalance.dayStartAmountCents,
            ),
            smsMonitor = data.smsMonitor,
            accountBalance = accountBalance,
            bankAccounts = visibleBankAccounts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(),
    )

    fun refreshClock() {
        val currentTime = System.currentTimeMillis()
        now.value = currentTime
        viewModelScope.launch { repository.ensureDailyBalanceSnapshot(currentTime) }
    }

    fun updateBudget(config: BudgetConfig) {
        viewModelScope.launch { repository.updateBudget(config) }
    }

    fun updateBalance(amountCents: Long) {
        viewModelScope.launch { repository.updateBalanceManually(amountCents) }
    }

    fun delete(transaction: TransactionEntity) {
        viewModelScope.launch { repository.delete(transaction) }
    }

    fun syncRecentSms() {
        if (smsSyncJob?.isActive == true) return
        smsSyncJob = viewModelScope.launch {
            SmsInboxSynchronizer(getApplication(), repository).syncRecent()
        }
    }

    private fun effectiveBalance(
        legacyBalance: AccountBalance,
        bankAccounts: List<BankAccountEntity>,
    ): AccountBalance {
        val accountsWithBalance = bankAccounts.filter { it.balanceCents != null }
        if (accountsWithBalance.isEmpty()) return legacyBalance

        val latestAccountUpdate = accountsWithBalance.maxOf { it.updatedAt }
        if (
            legacyBalance.source == BalanceSource.MANUAL &&
            legacyBalance.amountCents != null &&
            legacyBalance.updatedAt >= latestAccountUpdate
        ) {
            return legacyBalance
        }

        return AccountBalance(
            amountCents = accountsWithBalance.sumOf { it.balanceCents ?: 0L },
            updatedAt = latestAccountUpdate,
            source = BalanceSource.SMS,
            dayStartAmountCents = accountsWithBalance.sumOf { account ->
                account.dayStartBalanceCents ?: account.balanceCents ?: 0L
            },
            snapshotEpochDay = accountsWithBalance.mapNotNull { it.snapshotEpochDay }.maxOrNull(),
        )
    }

    private fun collapseToOneAccountPerBank(
        bankAccounts: List<BankAccountEntity>,
    ): List<BankAccountEntity> = bankAccounts
        .groupBy { it.bank }
        .map { (bank, accounts) ->
            val latest = accounts
                .filter { it.balanceCents != null }
                .maxByOrNull { it.updatedAt }
                ?: accounts.maxBy { it.updatedAt }
            latest.copy(
                accountKey = bank,
                cardLast4 = UNKNOWN_CARD_LAST4,
            )
        }
        .sortedBy { it.bank }
}
