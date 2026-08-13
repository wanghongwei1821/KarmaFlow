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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.example.sizhang.data.BudgetConfig
import com.example.sizhang.data.BudgetItem
import com.example.sizhang.data.BalanceSource
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
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshClock()
    }
}

@Composable
private fun LedgerScreen(
    state: LedgerUiState,
    onSaveBudget: (BudgetConfig) -> Unit,
    onSaveBalance: (Long) -> Unit,
    onSyncRecentSms: () -> Unit,
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
    }
    var showBudgetEditor by rememberSaveable { mutableStateOf(false) }
    var showBalanceEditor by rememberSaveable { mutableStateOf(false) }
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
    LaunchedEffect(readSmsPermissionGranted) {
        if (readSmsPermissionGranted) onSyncRecentSms()
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
                                Text("短信与权限", style = MaterialTheme.typography.headlineSmall)
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
                item { TodayCard(state) }
                item { AccountOverviewCard(state, onEdit = { showBalanceEditor = true }) }
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
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("账", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
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
        "recorded" -> "短信监听正常" to "最近一条 95566 消费短信已自动记账"
        "income_recorded" -> "收入已自动记账" to "收入不计入消费，并已更新短信中的交易后余额"
        "duplicate" -> "短信监听正常" to "最近一条短信与已有账目重复，未再次添加"
        "amount_not_found" -> "已收到 95566，但未找到金额" to "这条中行短信格式尚未覆盖"
        "no_expense_signal" -> "已收到 95566，但不像消费" to "未发现消费、支付、POS、出账等字样"
        "security_code" -> "已收到 95566 验证码" to "验证码已按安全规则过滤"
        "failed_transaction" -> "已收到失败交易通知" to "失败交易不会记入支出"
        "repayment" -> "已收到还款通知" to "还款通知不会记入消费"
        "sync_no_new" -> "短信同步完成" to "找到的中行消费短信都已记过，没有重复添加"
        "sync_balance" -> "余额同步完成" to "已从最新的中行短信读取账户余额"
        "sync_unrecognized" -> "已读到中行短信，但未识别消费" to "可能需要继续补充短信格式"
        "sync_none" -> "短信同步完成" to "最近45天未找到发件号码含95566的短信"
        "sync_error" -> "短信同步失败" to "手机短信服务暂时不可读取，请稍后重试"
        else -> "95566 短信监听已开启" to "等待下一条新短信；安装前的历史短信不会自动导入"
    }
    val displayedTitle = if (monitor.resultCode?.startsWith("sync_added:") == true) {
        "短信补记成功"
    } else title
    val displayedDetail = if (monitor.resultCode?.startsWith("sync_added:") == true) {
        "已补记 ${monitor.resultCode.substringAfter(':')} 笔中行收支"
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
                    Text("95566 服务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(if (canSync) "立即同步" else "同步最近中行短信")
                }
                TextButton(onClick = onOpenSettings) { Text("检查权限") }
            }
            if (!canSync) {
                Text(
                    "点击同步后才会申请读取权限；仅扫描最近45天发件号码含95566的短信，正文不会保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TodayCard(state: LedgerUiState) {
    val balanceDaily = state.summary.currentBalanceDailyCents
    val plannedDaily = state.summary.originalBalanceDailyCents
        ?: state.summary.dailyTargetCents
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(30.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 23.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "按账户余额：每天可花",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
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
            Text(
                balanceDaily?.let(::formatMoney) ?: "尚未获取余额",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = if (balanceDaily != null) 43.sp else 28.sp,
                fontWeight = FontWeight.Bold,
            )
            if (balanceDaily == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "设置账户余额后，将按余额和周期剩余天数计算",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .72f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .16f))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Metric(
                    "按原预算计划：每天目标",
                    formatMoney(plannedDaily),
                    light = true,
                    modifier = Modifier.weight(1f),
                )
                Metric("今日已消费", formatMoney(state.summary.todaySpentCents), light = true, modifier = Modifier.weight(1f))
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
                    Text("中国银行余额", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        balance.amountCents?.let(::formatMoney) ?: "等待短信更新",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                TextButton(onClick = onEdit) { Text("编辑") }
            }
            if (balance.amountCents != null) {
                val source = when (balance.source) {
                    BalanceSource.SMS -> "95566 自动更新"
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
                Text(
                    "目标留存 ${formatMoney(state.summary.targetEndingBalanceCents)}",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium,
                )
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
                OverviewMetric("起始", startingBalance?.let(::formatMoney) ?: "未设置", Modifier.weight(1f))
                OverviewMetric("净支出", formatMoney(state.summary.monthSpentCents), Modifier.weight(1f))
                OverviewMetric("预留", formatMoney(state.summary.reservedCents), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier, light: Boolean = false) {
    Column(modifier) {
        Text(
            label,
            color = if (light) MaterialTheme.colorScheme.onPrimary.copy(alpha = .72f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            value,
            color = if (light) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BudgetEditor(
    initial: BudgetConfig,
    onDismiss: () -> Unit,
    onSave: (BudgetConfig) -> Unit,
) {
    var monthly by rememberSaveable(initial) { mutableStateOf(centsToEditText(initial.monthlyBudgetCents)) }
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
    val parsedMonthly = parseMoneyToCents(monthly)
    val parsedStartingBalance = parseOptionalMoneyToCents(cycleStartingBalance)
    val parsedTargetEndingBalance = parseMoneyToCents(targetEndingBalance)
    val parsedItems = itemDrafts.map { draft ->
        parseMoneyToCents(draft.amount)?.let { amount ->
            BudgetItem(draft.id, draft.name.trim(), amount)
        }
    }
    val itemsValid = parsedItems.all { it != null } &&
        itemDrafts.all { it.name.trim().isNotEmpty() }
    val reservedTotal = parsedItems.filterNotNull().sumOf { it.amountCents }
    val targetFitsStartingBalance = parsedStartingBalance.value == null ||
        (parsedTargetEndingBalance != null && parsedTargetEndingBalance <= parsedStartingBalance.value)
    val valid = parsedMonthly != null && parsedStartingBalance.isValid &&
        parsedTargetEndingBalance != null && targetFitsStartingBalance &&
        cycleEndEpochDay >= cycleStartEpochDay && itemsValid &&
        reservedTotal + parsedTargetEndingBalance <= parsedMonthly

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
                MoneyField("每周期总预算", monthly, { monthly = it })
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
                MoneyField("周期起始账户余额（可留空）", cycleStartingBalance, {
                    cycleStartingBalance = it
                })
                MoneyField("目标结余", targetEndingBalance, { targetEndingBalance = it })
                Text(
                    "目标结余是周期结束时希望账户中保留的钱，不会分配到每日额度。",
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
                    if (valid) "可花余额扣除目标结余后，会按当前周期剩余天数动态分配。"
                    else "请检查日期和金额；目标结余不能超过起始余额或可用预算。",
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
                        BudgetConfig(
                            monthlyBudgetCents = parsedMonthly ?: return@Button,
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
                    "余额仅保存在本机。以后收到时间更新、且包含“交易后余额”的95566短信时，会自动更新这里的数字。",
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
            if (candidate.matches(Regex("""[0-9]{0,8}(?:\.[0-9]{0,2})?"""))) {
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
