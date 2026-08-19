package com.example.sizhang

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.example.sizhang.data.BudgetConfig
import com.example.sizhang.data.BudgetItem
import com.example.sizhang.data.BalanceSource
import com.example.sizhang.data.BankAccountEntity
import com.example.sizhang.data.TransactionEntity
import com.example.sizhang.data.TransactionKind
import com.example.sizhang.ui.BudgetCalculator
import com.example.sizhang.ui.LedgerUiState
import com.example.sizhang.ui.LedgerViewModel
import com.example.sizhang.ui.PrivateLedgerTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrivateLedgerTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LedgerScreen(
                    state = state,
                    onSaveBudget = viewModel::updateBudget,
                    onSaveBalance = viewModel::updateBalance,
                    onSyncRecentSms = viewModel::syncRecentSms,
                    onRefreshTodayAllowance = viewModel::refreshTodayAllowance,
                    onSetTransactionExcluded = viewModel::setTransactionExcluded,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshClock()
        viewModel.syncRecentSms()
    }
}

@Composable
private fun LedgerScreen(
    state: LedgerUiState,
    onSaveBudget: (BudgetConfig) -> Unit,
    onSaveBalance: (Long) -> Unit,
    onSyncRecentSms: () -> Unit,
    onRefreshTodayAllowance: () -> Unit,
    onSetTransactionExcluded: (TransactionEntity, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var smsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var readSmsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        smsPermissionGranted = granted
        permissionRequested = true
    }
    val readSmsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        readSmsPermissionGranted = granted
        if (granted) onSyncRecentSms()
    }
    var showBudgetEditor by rememberSaveable { mutableStateOf(false) }
    var showBalanceEditor by rememberSaveable { mutableStateOf(false) }
    var pendingTransactionId by rememberSaveable { mutableStateOf<Long?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        smsPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        readSmsPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val openSystemSettings = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth(.88f),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 32.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppMark()
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("KarmaFlow 管理", style = MaterialTheme.typography.headlineSmall)
                                Text("本地处理 · 不上传", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (!smsPermissionGranted) {
                        item {
                            PermissionCard(
                                permanentlyDenied = permissionRequested,
                                onRequest = {
                                    permissionRequested = true
                                    permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                },
                                onOpenSettings = openSystemSettings,
                            )
                        }
                    } else {
                        item {
                            SmsStatusCard(
                                state = state,
                                canSync = readSmsPermissionGranted,
                                onSync = {
                                    if (readSmsPermissionGranted) onSyncRecentSms()
                                    else readSmsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                                },
                                onOpenSettings = openSystemSettings,
                            )
                        }
                    }
                    if (state.bankAccounts.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("银行账户", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "已识别 ${state.bankAccounts.size} 家银行 · 余额按短信时间更新",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(
                            items = state.bankAccounts,
                            key = { account -> account.accountKey },
                        ) { account ->
                            BankAccountDrawerCard(account)
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { scope.launch { drawerState.close() } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("返回首页") }
                    }
                }
            }
        },
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 12.dp,
                    bottom = 36.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Header(
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onEditBudget = { showBudgetEditor = true },
                    )
                }
                item { TodayCard(state, onRefresh = onRefreshTodayAllowance) }
                item { TomorrowForecastCard(state) }
                if (state.summary.tomorrowAvailableCents != null) {
                    item { TodaySpendPreviewCard(state) }
                }
                item { AccountOverviewCard(state, onEdit = { showBalanceEditor = true }) }
                item { SpendingTrendCard(state) }
                item { HistoryHeader(state.transactions.size) }
                if (state.transactions.isEmpty()) {
                    item { EmptyHistoryCard() }
                } else {
                    items(
                        items = state.transactions,
                        key = { transaction -> "history-${transaction.id}" },
                    ) { transaction ->
                        HistoryTransactionCard(
                            transaction = transaction,
                            onToggle = { pendingTransactionId = transaction.id },
                        )
                    }
                }
            }
        }
    }

    if (showBudgetEditor) {
        BudgetEditor(
            initial = state.budget,
            onDismiss = { showBudgetEditor = false },
            onSave = {
                onSaveBudget(it)
                showBudgetEditor = false
            },
        )
    }
    if (showBalanceEditor) {
        BalanceEditor(
            initialCents = state.accountBalance.amountCents,
            onDismiss = { showBalanceEditor = false },
            onSave = { amount ->
                onSaveBalance(amount)
                showBalanceEditor = false
            },
        )
    }
    val pendingTransaction = state.transactions.firstOrNull { it.id == pendingTransactionId }
    if (pendingTransaction != null) {
        val willExclude = !pendingTransaction.isExcluded
        AlertDialog(
            onDismissRequest = { pendingTransactionId = null },
            title = { Text(if (willExclude) "取消计入这笔收支？" else "恢复计入这笔收支？") },
            text = {
                Text(
                    if (willExclude) {
                        "原始短信记录仍会保留，但这笔收支将不再参与今日消费、周期统计和曲线计算。"
                    } else {
                        "这笔收支会重新参与今日消费、周期统计和曲线计算。"
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetTransactionExcluded(pendingTransaction, willExclude)
                        pendingTransactionId = null
                    },
                ) { Text(if (willExclude) "取消计入" else "恢复计入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTransactionId = null }) { Text("返回") }
            },
        )
    }
}

@Composable
private fun Header(onOpenMenu: () -> Unit, onEditBudget: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenMenu) {
            Text("☰", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        AppMark()
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "今日财务概览",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onEditBudget) { Text("预算") }
    }
}

@Composable
private fun AppMark() {
    Image(
        painter = painterResource(R.drawable.karmaflow_launcher_logo),
        contentDescription = "KarmaFlow",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp)),
    )
}

@Composable
private fun PermissionCard(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("信", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("开启短信自动记账", style = MaterialTheme.typography.titleMedium)
                    Text("等待授权", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Text(
                "仅监听新收到的银行短信；不会读取历史短信，也不会上传。验证码和非消费通知会被过滤。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequest) { Text("允许短信权限") }
                if (permanentlyDenied) {
                    TextButton(onClick = onOpenSettings) { Text("打开系统设置") }
                }
            }
        }
    }
}

@Composable
private fun SmsStatusCard(
    state: LedgerUiState,
    canSync: Boolean,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val monitor = state.smsMonitor
    val hasReceived = monitor.lastReceivedAt > 0
    val (title, detail) = when (monitor.resultCode) {
        "recorded" -> "短信监听正常" to "最近一条银行卡消费短信已自动记账"
        "income_recorded" -> "收入已自动记账" to "收入不计入消费，并已更新短信中的交易后余额"
        "duplicate" -> "短信监听正常" to "最近一条短信与已有账目重复，未再次添加"
        "amount_not_found" -> "已收到银行短信，但未找到金额" to "这条短信格式尚未覆盖"
        "no_expense_signal" -> "已收到银行短信，但不像收支" to "未发现消费、收入、退款等字样"
        "security_code" -> "已收到银行验证码" to "验证码已按安全规则过滤"
        "failed_transaction" -> "已收到失败交易通知" to "失败交易不会记入支出"
        "repayment" -> "已收到还款通知" to "还款通知不会记入消费"
        "sync_no_new" -> "短信同步完成" to "找到的银行卡收支都已记过，没有重复添加"
        "sync_balance" -> "余额同步完成" to "已从最新的银行卡短信读取账户余额"
        "sync_unrecognized" -> "已读到银行短信，但未识别收支" to "可能需要继续补充短信格式"
        "sync_none" -> "短信同步完成" to "最近45天未找到支持银行的短信"
        "sync_error" -> "短信同步失败" to "手机短信服务暂时不可读取，请稍后重试"
        else -> "银行短信监听已开启" to "等待下一条新短信；也可以主动同步最近45天"
    }
    val displayedTitle = if (monitor.resultCode?.startsWith("sync_added:") == true) {
        "短信补记成功"
    } else title
    val displayedDetail = if (monitor.resultCode?.startsWith("sync_added:") == true) {
        "已补记 ${monitor.resultCode.substringAfter(':')} 笔银行卡收支"
    } else detail
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(displayedTitle, style = MaterialTheme.typography.titleMedium)
                    Text("银行短信服务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(displayedDetail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (hasReceived) {
                Text(
                    "最近接收：${monitor.lastSender ?: "未知号码"} · ${formatDateTime(monitor.lastReceivedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "请确认系统设置 → 应用 → KarmaFlow → 权限中，短信权限为允许。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onSync, modifier = Modifier.weight(1f)) {
                    Text(if (canSync) "立即同步" else "同步最近银行短信")
                }
                TextButton(onClick = onOpenSettings) { Text("检查权限") }
            }
            if (!canSync) {
                Text(
                    "点击同步后才会申请读取权限；仅补扫最近45天支持银行的短信，正文不会保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BankAccountDrawerCard(account: BankAccountEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bankShortMark(account.bank),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(account.bank, style = MaterialTheme.typography.titleSmall)
                if (account.updatedAt > 0L) {
                    Text(
                        formatDateTime(account.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .76f),
                    )
                }
            }
            Text(
                text = account.balanceCents?.let(::formatMoney) ?: "余额待识别",
                style = if (account.balanceCents != null) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
                fontWeight = if (account.balanceCents != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (account.balanceCents != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun bankShortMark(bank: String): String = when (bank) {
    "中国工商银行" -> "工"
    "中国建设银行" -> "建"
    "中国农业银行" -> "农"
    "中国银行" -> "中"
    "招商银行" -> "招"
    "交通银行" -> "交"
    "邮储银行" -> "邮"
    else -> "卡"
}

@Composable
private fun TodayCard(state: LedgerUiState, onRefresh: () -> Unit) {
    val balanceDaily = state.summary.currentBalanceDailyCents
    val available = balanceDaily ?: state.summary.todayAvailableCents
    var showRefreshConfirmation by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(30.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 23.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (balanceDaily != null) "今日可用 · 已锁定" else "今日可支配",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (balanceDaily != null) {
                    TextButton(onClick = { showRefreshConfirmation = true }) {
                        Text(
                            "刷新额度",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = .13f), CircleShape)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (balanceDaily != null) "剩 ${state.summary.remainingCycleDays} 天" else "今天",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedMoneyText(
                cents = available,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 43.sp,
                fontWeight = FontWeight.Bold,
                label = "today-available",
            )
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .16f))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Metric(
                    if (state.summary.originalBalanceDailyCents != null) "原计划 / 天" else "预算目标",
                    state.summary.originalBalanceDailyCents ?: state.summary.dailyTargetCents,
                    light = true,
                    modifier = Modifier.weight(1f),
                )
                Metric(
                    "今日已消费",
                    state.summary.todaySpentCents,
                    light = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (showRefreshConfirmation) {
        AlertDialog(
            onDismissRequest = { showRefreshConfirmation = false },
            title = { Text("刷新今日可用？") },
            text = {
                Text("将按当前账户余额、目标结余、预留金额和剩余天数立即重新计算。刷新后，新额度会继续锁定到明天。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRefresh()
                        showRefreshConfirmation = false
                    },
                ) { Text("确认刷新") }
            },
            dismissButton = {
                TextButton(onClick = { showRefreshConfirmation = false }) { Text("暂不刷新") }
            },
        )
    }
}

@Composable
private fun TomorrowForecastCard(state: LedgerUiState) {
    val forecast = state.summary.tomorrowAvailableCents
    val forecastDate = state.summary.tomorrowDate
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("明", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        if (forecastDate != null) {
                            "${forecastDate.monthValue}月${forecastDate.dayOfMonth}日预估可花"
                        } else {
                            "明日预知"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (forecast != null) {
                        AnimatedMoneyText(
                            cents = forecast,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            label = "tomorrow-forecast",
                        )
                    } else {
                        Text(
                            "本周期暂无明日额度",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        if (forecast != null) {
                            "已结合今天的实时消费，继续消费会更新此预估"
                        } else {
                            "进入下一周期后会重新开始预测"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodaySpendPreviewCard(state: LedgerUiState) {
    val sliderMaxCents = 200_000L
    var previewExtraText by rememberSaveable(state.summary.tomorrowDate) { mutableStateOf("0") }
    val previewExtraCents = parseMoneyToCents(previewExtraText) ?: 0L
    val sliderCents = previewExtraCents.coerceIn(0L, sliderMaxCents)
    val previewFraction = (sliderCents.toDouble() / sliderMaxCents).toFloat()
    val previewTomorrowCents = BudgetCalculator.forecastTomorrowAvailable(
        distributableCents = state.summary.tomorrowDistributableCents,
        remainingDays = state.summary.tomorrowRemainingDays,
        additionalSpendCents = previewExtraCents,
    ) ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "今日消费预览",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "不会修改真实账目",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = previewExtraText,
                onValueChange = { candidate ->
                    if (candidate.matches(Regex("""[0-9]{0,9}(?:\.[0-9]{0,2})?"""))) {
                        previewExtraText = candidate
                    }
                },
                label = { Text("假设今天再花") },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text("滑块固定为 ¥0–¥2,000，也可手动输入更大金额") },
                modifier = Modifier.fillMaxWidth(),
            )
            Slider(
                value = previewFraction,
                onValueChange = { fraction ->
                    val rawCents = (sliderMaxCents * fraction.coerceIn(0f, 1f)).roundToLong()
                    val roundedCents = ((rawCents + 50L) / 100L) * 100L
                    previewExtraText = centsToEditText(roundedCents.coerceAtMost(sliderMaxCents))
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = .20f),
                ),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "¥0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "¥2,000",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "按以上消费，明日预计可花",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                AnimatedMoneyText(
                    cents = previewTomorrowCents,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    label = "preview-tomorrow",
                    durationMillis = 180,
                )
            }
        }
    }
}

@Composable
private fun SpendingTrendCard(state: LedgerUiState) {
    val points = state.summary.dailySpending
    val actualColor = MaterialTheme.colorScheme.primary
    val expectedColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val average = if (points.isEmpty()) 0L else points.sumOf { it.actualCents } / points.size
    val peak = points.maxOfOrNull { it.actualCents } ?: 0L
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text("每日预计与实际花费", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (points.isEmpty()) "周期开始后生成" else "最近 ${points.size} 天 · 人民币净消费",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (points.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(9.dp).background(actualColor, CircleShape))
                    Text("实际", style = MaterialTheme.typography.bodySmall)
                    Box(Modifier.size(9.dp).background(expectedColor, CircleShape))
                    Text("预计", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "实际日均 ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AnimatedMoneyText(
                            cents = average,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "trend-average",
                        )
                    }
                }
            }
            if (points.isEmpty()) {
                Text(
                    "当前预算周期尚未开始，之后每天的消费会在这里形成曲线。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp),
                ) {
                    val left = 8f
                    val right = size.width - 8f
                    val top = 10f
                    val bottom = size.height - 12f
                    val chartWidth = (right - left).coerceAtLeast(1f)
                    val chartHeight = (bottom - top).coerceAtLeast(1f)
                    val maximum = points.maxOf { point ->
                        maxOf(point.actualCents, point.expectedCents, 0L)
                    }
                    val minimum = points.minOf { point ->
                        minOf(point.actualCents, point.expectedCents, 0L)
                    }
                    val amountRange = (maximum - minimum).coerceAtLeast(1L).toFloat()
                    fun amountY(amount: Long): Float = bottom -
                        chartHeight * ((amount - minimum).toFloat() / amountRange)
                    repeat(4) { index ->
                        val y = top + chartHeight * index / 3f
                        drawLine(
                            color = gridColor.copy(alpha = .72f),
                            start = Offset(left, y),
                            end = Offset(right, y),
                            strokeWidth = 1.2f,
                        )
                    }
                    if (minimum < 0L && maximum > 0L) {
                        drawLine(
                            color = gridColor,
                            start = Offset(left, amountY(0L)),
                            end = Offset(right, amountY(0L)),
                            strokeWidth = 2f,
                        )
                    }
                    val actualPath = Path()
                    val expectedPath = Path()
                    points.forEachIndexed { index, point ->
                        val x = if (points.size == 1) {
                            left + chartWidth / 2f
                        } else {
                            left + chartWidth * index / (points.size - 1).toFloat()
                        }
                        val actualY = amountY(point.actualCents)
                        val expectedY = amountY(point.expectedCents)
                        if (index == 0) {
                            actualPath.moveTo(x, actualY)
                            expectedPath.moveTo(x, expectedY)
                        } else {
                            actualPath.lineTo(x, actualY)
                            expectedPath.lineTo(x, expectedY)
                        }
                    }
                    drawPath(
                        path = expectedPath,
                        color = expectedColor,
                        style = Stroke(
                            width = 3.2f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                    drawPath(
                        path = actualPath,
                        color = actualColor,
                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    points.forEachIndexed { index, point ->
                        val x = if (points.size == 1) {
                            left + chartWidth / 2f
                        } else {
                            left + chartWidth * index / (points.size - 1).toFloat()
                        }
                        val actualY = amountY(point.actualCents)
                        val expectedY = amountY(point.expectedCents)
                        drawCircle(color = expectedColor, radius = 3.6f, center = Offset(x, expectedY))
                        drawCircle(color = actualColor, radius = 4.5f, center = Offset(x, actualY))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    val first = points.first().date
                    val last = points.last().date
                    Text(
                        "${first.monthValue}/${first.dayOfMonth}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "最高 ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AnimatedMoneyText(
                            cents = peak,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "trend-peak",
                        )
                    }
                    Text(
                        "${last.monthValue}/${last.dayOfMonth}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("历史收支明细", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "共 $count 笔 · 可取消或恢复计入，原短信记录不会删除",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Text(
            "识别到银行收支短信后，明细会显示在这里。",
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryTransactionCard(
    transaction: TransactionEntity,
    onToggle: () -> Unit,
) {
    val kindLabel = when (transaction.kind) {
        TransactionKind.EXPENSE -> "支出"
        TransactionKind.REFUND -> "退款"
        TransactionKind.INCOME -> "收入"
    }
    val amountColor = when {
        transaction.isExcluded -> MaterialTheme.colorScheme.onSurfaceVariant
        transaction.kind == TransactionKind.EXPENSE -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (transaction.isExcluded) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (transaction.isExcluded) 0.dp else 1.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transaction.merchant?.takeIf { it.isNotBlank() } ?: "${kindLabel}交易",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${transaction.bank} · ${formatDateTime(transaction.occurredAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatTransactionAmount(transaction),
                        color = amountColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (transaction.isExcluded) "已取消计入" else kindLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (transaction.isExcluded) "不参与预算与消费统计" else "已计入本地账目",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onToggle) {
                    Text(if (transaction.isExcluded) "恢复计入" else "取消计入")
                }
            }
        }
    }
}

@Composable
private fun AccountOverviewCard(state: LedgerUiState, onEdit: () -> Unit) {
    val balance = state.accountBalance
    val startingBalance = state.budget.cycleStartingBalanceCents
    val currentBalance = balance.amountCents
    val progress = if (currentBalance != null && startingBalance != null && startingBalance > 0) {
        ((startingBalance - currentBalance).toDouble() / startingBalance).coerceIn(0.0, 1.0).toFloat()
    } else state.summary.usedFraction
    val cycleStart = state.summary.cycleStartDate
    val cycleEnd = state.summary.cycleEndDate
    val balanceTitle = when {
        balance.source == BalanceSource.MANUAL -> "手动总余额"
        state.bankAccounts.size > 1 -> "${state.bankAccounts.size} 家银行总余额"
        state.bankAccounts.size == 1 -> state.bankAccounts.first().bank
        else -> "账户余额"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("¥", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(balanceTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (balance.amountCents != null) {
                        AnimatedMoneyText(
                            cents = balance.amountCents,
                            style = MaterialTheme.typography.headlineMedium,
                            label = "account-balance",
                        )
                    } else {
                        Text("等待短信更新", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                TextButton(onClick = onEdit) { Text("编辑") }
            }
            if (balance.amountCents != null) {
                val source = when (balance.source) {
                    BalanceSource.SMS -> "银行短信自动更新"
                    BalanceSource.MANUAL -> "手动设置"
                    null -> "本地记录"
                }
                Text(
                    if (balance.updatedAt > 0) "$source · ${formatDateTime(balance.updatedAt)}" else source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本周期", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "目标留存 ",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    AnimatedMoneyText(
                        cents = state.summary.targetEndingBalanceCents,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelMedium,
                        label = "target-ending-balance",
                    )
                }
            }
            if (cycleStart != null && cycleEnd != null) {
                Text(
                    "${cycleStart.monthValue}月${cycleStart.dayOfMonth}日 — ${cycleEnd.monthValue}月${cycleEnd.dayOfMonth}日",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewMetric("起始", startingBalance, Modifier.weight(1f), "未设置")
                OverviewMetric("净支出", state.summary.monthSpentCents, Modifier.weight(1f))
                OverviewMetric("预留", state.summary.reservedCents, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    valueCents: Long?,
    modifier: Modifier = Modifier,
    missingValue: String = "—",
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (valueCents != null) {
            AnimatedMoneyText(
                cents = valueCents,
                style = MaterialTheme.typography.labelLarge,
                label = "overview-$label",
            )
        } else {
            Text(
                missingValue,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Metric(label: String, valueCents: Long, modifier: Modifier = Modifier, light: Boolean = false) {
    Column(modifier) {
        Text(
            label,
            color = if (light) MaterialTheme.colorScheme.onPrimary.copy(alpha = .72f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        AnimatedMoneyText(
            cents = valueCents,
            color = if (light) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            label = "metric-$label",
        )
    }
}

@Composable
private fun AnimatedMoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = TextStyle.Default,
    label: String,
    durationMillis: Int = 420,
) {
    val animatedCents by animateFloatAsState(
        targetValue = cents.toFloat(),
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = label,
    )
    Text(
        text = formatMoney(animatedCents.roundToLong()),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BudgetEditor(
    initial: BudgetConfig,
    onDismiss: () -> Unit,
    onSave: (BudgetConfig) -> Unit,
) {
    var cycleStartEpochDay by rememberSaveable(initial) { mutableStateOf(initial.cycleStartEpochDay) }
    var cycleEndEpochDay by rememberSaveable(initial) { mutableStateOf(initial.cycleEndEpochDay) }
    var cycleStartingBalance by rememberSaveable(initial) {
        mutableStateOf(initial.cycleStartingBalanceCents?.let(::centsToEditText).orEmpty())
    }
    var targetEndingBalance by rememberSaveable(initial) {
        mutableStateOf(centsToEditText(initial.targetEndingBalanceCents))
    }
    var itemDrafts by remember(initial) {
        mutableStateOf(
            initial.reservedItems.map {
                BudgetItemDraft(it.id, it.name, centsToEditText(it.amountCents))
            },
        )
    }
    val parsedStartingBalance = parseOptionalMoneyToCents(cycleStartingBalance)
    val parsedTargetEndingBalance = parseMoneyToCents(targetEndingBalance)
    val parsedItems = itemDrafts.map { draft ->
        parseMoneyToCents(draft.amount)?.let { amount ->
            BudgetItem(draft.id, draft.name.trim(), amount)
        }
    }
    val itemsValid = parsedItems.all { it != null } &&
        itemDrafts.all { it.name.trim().isNotEmpty() }
    val valid = parsedStartingBalance.isValid && parsedTargetEndingBalance != null &&
        cycleEndEpochDay >= cycleStartEpochDay && itemsValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预算设置") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "每日额度按当前账户余额计算，不再使用每周期总预算。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                DateField(
                    label = "周期开始日期",
                    epochDay = cycleStartEpochDay,
                    onDateSelected = { cycleStartEpochDay = it },
                )
                DateField(
                    label = "周期终止日期（包含当天）",
                    epochDay = cycleEndEpochDay,
                    onDateSelected = { cycleEndEpochDay = it },
                )
                MoneyField("周期起始账户余额（仅用于原计划对比，可留空）", cycleStartingBalance, {
                    cycleStartingBalance = it
                })
                MoneyField("目标结余", targetEndingBalance, { targetEndingBalance = it })
                Text(
                    "目标结余是周期结束时希望保留的钱，不设预算上限；高于当前余额时，每日额度会显示为负数。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("预留项目", fontWeight = FontWeight.Bold)
                itemDrafts.forEachIndexed { index, draft ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            OutlinedTextField(
                                value = draft.name,
                                onValueChange = { name ->
                                    if (name.length <= 30) {
                                        itemDrafts = itemDrafts.toMutableList().also {
                                            it[index] = draft.copy(name = name)
                                        }
                                    }
                                },
                                label = { Text("项目名称") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MoneyField("预留金额", draft.amount, { amount ->
                                itemDrafts = itemDrafts.toMutableList().also {
                                    it[index] = draft.copy(amount = amount)
                                }
                            })
                            TextButton(onClick = {
                                itemDrafts = itemDrafts.toMutableList().also { it.removeAt(index) }
                            }) { Text("删除此项目") }
                        }
                    }
                }
                OutlinedButton(
                    enabled = itemDrafts.size < 20,
                    onClick = {
                        itemDrafts = itemDrafts + BudgetItemDraft(
                            id = "item_${System.currentTimeMillis()}",
                            name = "",
                            amount = "0",
                        )
                    },
                ) { Text("新增预留项目") }
                Text(
                    if (valid) "当前余额扣除预留项目和目标结余后，会按周期剩余天数分配。"
                    else "请检查日期、项目名称和金额。",
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        initial.copy(
                            cycleStartEpochDay = cycleStartEpochDay,
                            cycleEndEpochDay = cycleEndEpochDay,
                            cycleStartingBalanceCents = parsedStartingBalance.value,
                            targetEndingBalanceCents = parsedTargetEndingBalance ?: return@Button,
                            reservedItems = parsedItems.filterNotNull(),
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BalanceEditor(
    initialCents: Long?,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var balance by rememberSaveable(initialCents) {
        mutableStateOf(initialCents?.let(::centsToEditText).orEmpty())
    }
    val parsedBalance = parseMoneyToCents(balance)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账户余额") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyField("当前账户余额", balance, { balance = it })
                Text(
                    "余额仅保存在本机。以后收到时间更新、且包含余额的支持银行短信时，会自动更新；手动值会保留到更新的银行短信到达。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = parsedBalance != null,
                onClick = { onSave(parsedBalance ?: return@Button) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private data class BudgetItemDraft(
    val id: String,
    val name: String,
    val amount: String,
)

@Composable
private fun DateField(
    label: String,
    epochDay: Long,
    onDateSelected: (Long) -> Unit,
) {
    val context = LocalContext.current
    val date = LocalDate.ofEpochDay(epochDay)
    OutlinedButton(
        onClick = {
            android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onDateSelected(LocalDate.of(year, month + 1, dayOfMonth).toEpochDay())
                },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            ).show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${date.year}年${date.monthValue}月${date.dayOfMonth}日")
        }
    }
}

@Composable
private fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            if (candidate.matches(Regex("""[0-9]{0,12}(?:\.[0-9]{0,2})?"""))) {
                onValueChange(candidate)
            }
        },
        label = { Text(label) },
        prefix = { Text("¥") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.CHINA).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

private val dateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun formatMoney(cents: Long): String = synchronized(currencyFormatter) {
    currencyFormatter.format(BigDecimal.valueOf(cents, 2))
}

private fun formatTransactionAmount(transaction: TransactionEntity): String {
    val sign = if (transaction.kind == TransactionKind.EXPENSE) "−" else "+"
    val amount = if (transaction.currency == "CNY") {
        formatMoney(transaction.amountCents)
    } else {
        "${transaction.currency} ${BigDecimal.valueOf(transaction.amountCents, 2).toPlainString()}"
    }
    return "$sign$amount"
}

private fun formatDateTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .format(dateTimeFormatter)

private fun centsToEditText(cents: Long): String = BigDecimal.valueOf(cents, 2)
    .stripTrailingZeros()
    .toPlainString()

private fun parseMoneyToCents(value: String): Long? = try {
    if (value.isBlank()) null else BigDecimal(value)
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()
        .takeIf { it >= 0 }
} catch (_: NumberFormatException) {
    null
} catch (_: ArithmeticException) {
    null
}

private data class OptionalMoneyResult(val isValid: Boolean, val value: Long?)

private fun parseOptionalMoneyToCents(value: String): OptionalMoneyResult =
    if (value.isBlank()) {
        OptionalMoneyResult(isValid = true, value = null)
    } else {
        val parsed = parseMoneyToCents(value)
        OptionalMoneyResult(isValid = parsed != null, value = parsed)
    }
