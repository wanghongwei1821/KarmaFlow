package com.example.sizhang.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.sizhang.R
import com.example.sizhang.data.AccountBalance
import com.example.sizhang.data.BalanceSource
import com.example.sizhang.data.BankAccountEntity
import com.example.sizhang.data.BudgetConfig
import com.example.sizhang.data.LedgerRepository
import com.example.sizhang.data.TransactionEntity
import com.example.sizhang.ui.BudgetCalculator
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BudgetNotificationText(
    val todayAvailable: String,
    val todaySpent: String,
    val tomorrowAvailable: String,
) {
    companion object {
        fun create(
            todayAvailableCents: Long,
            todaySpentCents: Long,
            tomorrowAvailableCents: Long?,
        ): BudgetNotificationText = BudgetNotificationText(
            todayAvailable = "今日可用 ${formatMoney(todayAvailableCents)}",
            todaySpent = "今日已消费 ${formatMoney(todaySpentCents)}",
            tomorrowAvailable = "明日预计可花 ${formatMoney(tomorrowAvailableCents ?: 0)}",
        )

        private fun formatMoney(cents: Long): String =
            "¥${BigDecimal.valueOf(cents, 2).toPlainString()}"
    }
}

private data class NotificationSourceData(
    val transactions: List<TransactionEntity>,
    val budget: BudgetConfig,
    val legacyBalance: AccountBalance,
    val bankAccounts: List<BankAccountEntity>,
)

class BudgetStatusNotifier(
    private val context: Context,
    private val repository: LedgerRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectionJob: Job? = null

    @Volatile
    private var latestText: BudgetNotificationText? = null

    fun start() {
        if (collectionJob?.isActive == true) return
        createChannel()

        val sourceData = combine(
            repository.transactions,
            repository.budgetConfig,
            repository.accountBalance,
            repository.bankAccounts,
        ) { transactions, budget, legacyBalance, bankAccounts ->
            NotificationSourceData(
                transactions = transactions,
                budget = budget,
                legacyBalance = legacyBalance,
                bankAccounts = bankAccounts,
            )
        }

        collectionJob = scope.launch {
            combine(sourceData, dailyClock()) { data, nowMillis ->
                val accountBalance = effectiveBalance(
                    legacyBalance = data.legacyBalance,
                    bankAccounts = data.bankAccounts,
                )
                BudgetCalculator.calculate(
                    transactions = data.transactions,
                    config = data.budget,
                    nowMillis = nowMillis,
                    currentBalanceCents = accountBalance.amountCents,
                    dayStartBalanceCents = accountBalance.dayStartAmountCents,
                )
            }.map { summary ->
                BudgetNotificationText.create(
                    todayAvailableCents = summary.todayAvailableCents,
                    todaySpentCents = summary.todaySpentCents,
                    tomorrowAvailableCents = summary.tomorrowAvailableCents,
                )
            }.distinctUntilChanged().collect { text ->
                latestText = text
                post(text)
            }
        }
    }

    fun refresh() {
        latestText?.let(::post)
    }

    private fun dailyClock() = flow {
        while (currentCoroutineContext().isActive) {
            val now = ZonedDateTime.now()
            emit(now.toInstant().toEpochMilli())
            val nextDay = now.toLocalDate()
                .plusDays(1)
                .atStartOfDay(now.zone)
                .plusSeconds(1)
            delay(Duration.between(now, nextDay).toMillis().coerceAtLeast(1_000))
        }
    }

    private fun effectiveBalance(
        legacyBalance: AccountBalance,
        bankAccounts: List<BankAccountEntity>,
    ): AccountBalance {
        val accountsWithBalance = bankAccounts
            .groupBy(BankAccountEntity::bank)
            .mapNotNull { (_, accounts) ->
                accounts.filter { it.balanceCents != null }.maxByOrNull { it.updatedAt }
            }
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
            amountCents = accountsWithBalance.sumOf { it.balanceCents ?: 0 },
            updatedAt = latestAccountUpdate,
            source = BalanceSource.SMS,
            dayStartAmountCents = accountsWithBalance.sumOf {
                it.dayStartBalanceCents ?: it.balanceCents ?: 0
            },
            snapshotEpochDay = accountsWithBalance.mapNotNull { it.snapshotEpochDay }.maxOrNull(),
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "预算状态",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示今日和明日可用额度"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    @SuppressLint("MissingPermission")
    private fun post(text: BudgetNotificationText) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val details = "${text.todaySpent}\n${text.tomorrowAvailable}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_budget_notification)
            .setContentTitle(text.todayAvailable)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "budget_status"
        private const val NOTIFICATION_ID = 1230
    }
}
