package com.example.sizhang

import android.app.Application
import com.example.sizhang.data.AppDatabase
import com.example.sizhang.data.AccountBalanceStore
import com.example.sizhang.data.BudgetStore
import com.example.sizhang.data.LedgerRepository
import com.example.sizhang.data.SmsMonitorStore
import com.example.sizhang.sms.SmsSyncScheduler
import java.security.KeyStore

class PrivateLedgerApplication : Application() {
    val smsMonitorStore: SmsMonitorStore by lazy { SmsMonitorStore(this) }

    val repository: LedgerRepository by lazy {
        LedgerRepository(
            transactionDao = AppDatabase.getInstance(this).transactionDao(),
            bankAccountDao = AppDatabase.getInstance(this).bankAccountDao(),
            budgetStore = BudgetStore(this),
            smsMonitorStore = smsMonitorStore,
            accountBalanceStore = AccountBalanceStore(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        removeLegacyAiCredentials()
        SmsSyncScheduler.schedule(this)
    }

    private fun removeLegacyAiCredentials() {
        deleteSharedPreferences("deepseek_private_settings")
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.run {
                if (containsAlias("karmaflow_deepseek_api_key")) {
                    deleteEntry("karmaflow_deepseek_api_key")
                }
            }
        }
    }
}
