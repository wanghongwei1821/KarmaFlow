package com.example.sizhang.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sizhang.PrivateLedgerApplication
import com.example.sizhang.data.BudgetConfig
import com.example.sizhang.data.AccountBalance
import com.example.sizhang.data.TransactionEntity
import com.example.sizhang.data.SmsMonitorState
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
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as PrivateLedgerApplication).repository
    private val now = MutableStateFlow(System.currentTimeMillis())
    private var smsSyncJob: Job? = null

    init {
        viewModelScope.launch { repository.ensureDailyBalanceSnapshot(now.value) }
    }

    val uiState = combine(
        repository.transactions,
        repository.budgetConfig,
        repository.smsMonitor,
        repository.accountBalance,
        now,
    ) { transactions, budget, smsMonitor, accountBalance, currentTime ->
        LedgerUiState(
            transactions = transactions,
            budget = budget,
            summary = BudgetCalculator.calculate(
                transactions = transactions,
                config = budget,
                nowMillis = currentTime,
                currentBalanceCents = accountBalance.amountCents,
                dayStartBalanceCents = accountBalance.dayStartAmountCents,
            ),
            smsMonitor = smsMonitor,
            accountBalance = accountBalance,
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
}
