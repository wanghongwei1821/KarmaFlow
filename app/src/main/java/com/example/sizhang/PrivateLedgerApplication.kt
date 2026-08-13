package com.example.sizhang

import android.app.Application
import com.example.sizhang.data.AppDatabase
import com.example.sizhang.data.AccountBalanceStore
import com.example.sizhang.data.BudgetStore
import com.example.sizhang.data.LedgerRepository
import com.example.sizhang.data.SmsMonitorStore
import com.example.sizhang.sms.SmsSyncScheduler

class PrivateLedgerApplication : Application() {
    val smsMonitorStore: SmsMonitorStore by lazy { SmsMonitorStore(this) }

    val repository: LedgerRepository by lazy {
        LedgerRepository(
            transactionDao = AppDatabase.getInstance(this).transactionDao(),
            budgetStore = BudgetStore(this),
            smsMonitorStore = smsMonitorStore,
            accountBalanceStore = AccountBalanceStore(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        SmsSyncScheduler.schedule(this)
    }
}
