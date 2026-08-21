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
import com.example.sizhang.data.NotificationDisplaySettings
import com.example.sizhang.data.TransactionEntity
import com.example.sizhang.ui.BudgetCalculator
import com.example.sizhang.ui.BudgetSummary
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BudgetNotificationText(
    val lines: List<String>,
) {
    val title: String? get() = lines.firstOrNull()
    val details: String get() = lines.drop(1).joinToString("\n")

    companion object {
        fun create(
            summary: BudgetSummary,
            settings: NotificationDisplaySettings,
        ): BudgetNotificationText {
            if (!settings.enabled) return BudgetNotificationText(emptyList())

            val lines = buildList {
                if (settings.showTodaySpent) {
                    add("今日已消费 ${formatMoney(summary.todaySpentCents)}")
                }
                if (settings.showTodayAvailable) {
                    add("今日可用 ${formatMoney(summary.todayAvailableCents)}")
                }
                if (settings.showTomorrowAvailable) {
                    add("明日预计可花 ${formatOptionalMoney(summary.tomorrowAvailableCents)}")
                }
                if (settings.showCurrentBalance) {
                    add("当前余额 ${formatOptionalMoney(summary.currentBalanceCents)}")
                }
                if (settings.showCycleSpent) {
                    add("本期已消费 ${formatMoney(summary.monthSpentCents)}")
                }
                if (settings.showDistributableBalance) {
                    add("可分配余额 ${formatMoney(summary.monthRemainingCents)}")
                }
                if (settings.showTargetBalance) {
                    add("目标结余 ${formatMoney(summary.targetEndingBalanceCents)}")
                }
                if (settings.showReservedAmount) {
                    add("预留金额 ${formatMoney(summary.reservedCents)}")
                }
                if (settings.showRemainingDays) {
                    add("周期剩余 ${summary.remainingCycleDays} 天")
                }
            }
            return BudgetNotificationText(lines)
        }

        private fun formatMoney(cents: Long): String =
            "¥${BigDecimal.valueOf(cents, 2).toPlainString()}"

        private fun formatOptionalMoney(cents: Long?): String =
            cents?.let(::formatMoney) ?: "--"
    }
}

private data class NotificationSourceData(
    val transactions: List<TransactionEntity>,
    val budget: BudgetConfig,
    val legacyBalance: AccountBalance,
    val bankAccounts: List<BankAccountEntity>,
    val notificationSettings: NotificationDisplaySettings,
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
            repository.notificationSettings,
        ) { transactions, budget, legacyBalance, bankAccounts, notificationSettings ->
            NotificationSourceData(
                transactions = transactions,
                budget = budget,
                legacyBalance = legacyBalance,
                bankAccounts = bankAccounts,
                notificationSettings = notificationSettings,
            )
        }

        collectionJob = scope.launch {
            combine(sourceData, dailyClock()) { data, nowMillis ->
                val accountBalance = effectiveBalance(
                    legacyBalance = data.legacyBalance,
                    bankAccounts = data.bankAccounts,
                )
                val summary = BudgetCalculator.calculate(
                    transactions = data.transactions,
                    config = data.budget,
                    nowMillis = nowMillis,
                    currentBalanceCents = accountBalance.amountCents,
                    dayStartBalanceCents = accountBalance.dayStartAmountCents,
                )
                BudgetNotificationText.create(summary, data.notificationSettings)
            }.distinctUntilChanged().collect { text ->
                latestText = text.takeIf { it.lines.isNotEmpty() }
                if (text.lines.isEmpty()) cancel() else post(text)
            }
        }
    }

    fun refresh() {
        latestText?.let(::post) ?: cancel()
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
                description = "显示自定义预算摘要"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    @SuppressLint("MissingPermission")
    private fun post(text: BudgetNotificationText) {
        val title = text.title ?: return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_budget_notification)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
        if (text.details.isNotEmpty()) {
            builder
                .setContentText(text.details)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.details))
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val CHANNEL_ID = "budget_status"
        private const val NOTIFICATION_ID = 1230
    }
}
