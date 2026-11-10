package com.lauren.td

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                TdApp(
                    onEnableDetection = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onAllowOverlay = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    },
                    onTestPrompt = {
                        TransactionOverlayManager.show(
                            context = this,
                            transaction = TransactionData(
                                reference = "TEST_${System.currentTimeMillis()}",
                                amount = 5.00,
                                type = "TEST",
                                otherParty = "Test Person",
                                transactionCost = 0.00,
                                time = System.currentTimeMillis(),
                                source = "TEST"
                            )
                        )
                    },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TdApp(
    onEnableDetection: () -> Unit,
    onAllowOverlay: () -> Unit,
    onTestPrompt: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val db = remember { TdDatabase.get(context) }

    if (showSettings) {
        SettingsScreen(
            onBack = { showSettings = false },
            onEnableDetection = onEnableDetection,
            onAllowOverlay = onAllowOverlay,
            onRequestNotifications = onRequestNotifications,
            onTestPrompt = onTestPrompt,
            categoryDao = db.categoryDao()
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("🏠") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("💰") },
                    label = { Text("Budgets") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("🎯") },
                    label = { Text("Funds") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Text("📋") },
                    label = { Text("History") }
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TopBar(onSettingsClick = { showSettings = true })
            Box(Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> HomeTab(
                        txDao = db.transactionDao(),
                        budgetDao = db.budgetDao(),
                        fundDao = db.fundDao()
                    )
                    1 -> BudgetsTab(
                        txDao = db.transactionDao(),
                        budgetDao = db.budgetDao(),
                        categoryDao = db.categoryDao()
                    )
                    2 -> FundsTab(
                        txDao = db.transactionDao(),
                        fundDao = db.fundDao()
                    )
                    3 -> HistoryTab(
                        txDao = db.transactionDao(),
                        fundDao = db.fundDao()
                    )
                }
            }
        }
    }
}

@Composable
fun TopBar(onSettingsClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("TD", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onSettingsClick) { Text("⚙ Settings") }
    }
    HorizontalDivider()
}

// ---------- HOME ----------

@Composable
fun HomeTab(
    txDao: TransactionDao,
    budgetDao: BudgetDao,
    fundDao: FundDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userName = remember { getSavedName(context) }
    val startOfMonth = remember { startOfCurrentMonth() }

    val summary by txDao.getMonthlySummaryFlow(startOfMonth).collectAsState(
        initial = emptySummary()
    )
    val budgets by budgetDao.getAllFlow().collectAsState(initial = emptyList())
    val spends by txDao.getMonthlySpendingByCategory(startOfMonth)
        .collectAsState(initial = emptyList())
    val activeFund by fundDao.getActiveFlow().collectAsState(initial = null)
    val fundSpends by txDao.getFundSpendingFlow().collectAsState(initial = emptyList())

    val spendMap = spends.associate { it.category to it.total }
    val insight = InsightGenerator.generate(summary, budgets, spendMap)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (activeFund != null) {
            item {
                val spent = fundSpends
                    .filter { it.fund == activeFund?.name }
                    .sumOf { it.total }
                ActiveFundBanner(
                    fundName = activeFund!!.name,
                    spent = spent,
                    target = activeFund!!.targetAmount,
                    onStop = {
                        scope.launch { fundDao.clearActive() }
                    }
                )
            }
        }

        item {
            Text(
                greeting(userName),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                insight,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        item { MonthlySummaryCard(summary) }

        if (budgets.isNotEmpty()) {
            item { TopBudgetCard(budgets, spendMap) }
        }
    }
}

@Composable
fun ActiveFundBanner(
    fundName: String,
    spent: Double,
    target: Double?,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Tracking: $fundName",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF0D47A1)
                )
                Text(
                    if (target != null && target > 0)
                        "KSh ${String.format("%.0f", spent)} of ${String.format("%.0f", target)}"
                    else "KSh ${String.format("%.0f", spent)} so far",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF0D47A1)
                )
            }
            TextButton(onClick = onStop) { Text("End") }
        }
    }
}

@Composable
fun MonthlySummaryCard(summary: TransactionSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "THIS MONTH",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "KSh ${String.format("%,.0f", summary.grandTotal)}",
                style = MaterialTheme.typography.displaySmall
            )
            if (summary.unplannedTotal > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "KSh ${String.format("%,.0f", summary.unplannedTotal)} unplanned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE65100)
                )
            }
        }
    }
}

@Composable
fun TopBudgetCard(budgets: List<BudgetEntity>, spendMap: Map<String, Double>) {
    val usages = budgets.mapNotNull { b ->
        if (b.monthlyLimit <= 0.0) return@mapNotNull null
        val spent = spendMap[b.category] ?: 0.0
        Triple(b, spent, spent / b.monthlyLimit)
    }
    if (usages.isEmpty()) return
    val top = usages.maxByOrNull { it.third } ?: return
    val (budget, spent, ratio) = top

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(budget.category, style = MaterialTheme.typography.titleSmall)
                Text(
                    "KSh ${String.format("%.0f", spent)} of ${String.format("%.0f", budget.monthlyLimit)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
            BudgetProgressBar(spent = spent, limit = budget.monthlyLimit)
            Spacer(Modifier.height(4.dp))
            val pct = (ratio * 100).toInt()
            val remaining = budget.monthlyLimit - spent
            Text(
                if (remaining >= 0) "$pct% used — KSh ${String.format("%.0f", remaining)} left"
                else "$pct% used — KSh ${String.format("%.0f", -remaining)} over",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    spent > budget.monthlyLimit -> Color(0xFFD32F2F)
                    ratio >= 0.8 -> Color(0xFFF57C00)
                    else -> Color(0xFF388E3C)
                }
            )
        }
    }
}

// ---------- BUDGETS ----------

@Composable
fun BudgetsTab(
    txDao: TransactionDao,
    budgetDao: BudgetDao,
    categoryDao: CategoryDao
) {
    val startOfMonth = remember { startOfCurrentMonth() }
    val categories by categoryDao.getAllFlow().collectAsState(initial = emptyList())
    val budgets by budgetDao.getAllFlow().collectAsState(initial = emptyList())
    val spends by txDao.getMonthlySpendingByCategory(startOfMonth)
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var dialogFor by remember { mutableStateOf<String?>(null) }

    val spendMap = spends.associate { it.category to it.total }
    val budgetMap = budgets.associate { it.category to it.monthlyLimit }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Budgets", style = MaterialTheme.typography.headlineMedium)
            Text("Monthly limits for the categories you care about.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
        items(categories) { cat ->
            val spent = spendMap[cat.name] ?: 0.0
            val limit = budgetMap[cat.name]
            BudgetRow(
                category = cat.name,
                spent = spent,
                limit = limit,
                onClickSet = { dialogFor = cat.name }
            )
        }
    }

    dialogFor?.let { cat ->
        SetBudgetDialog(
            categoryName = cat,
            currentLimit = budgetMap[cat],
            onDismiss = { dialogFor = null },
            onSave = { value ->
                scope.launch {
                    budgetDao.upsert(BudgetEntity(category = cat, monthlyLimit = value))
                }
                dialogFor = null
            }
        )
    }
}

@Composable
fun BudgetRow(category: String, spent: Double, limit: Double?, onClickSet: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (limit == null) "No budget" else "KSh ${String.format("%.0f", limit)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Spent this month: KSh ${String.format("%.0f", spent)}", style = MaterialTheme.typography.bodySmall)
            if (limit != null && limit > 0) {
                Spacer(Modifier.height(8.dp))
                BudgetProgressBar(spent = spent, limit = limit)
                Spacer(Modifier.height(4.dp))
                val pct = ((spent / limit) * 100).toInt()
                val remaining = limit - spent
                Text(
                    if (remaining >= 0) "$pct% used — KSh ${String.format("%.0f", remaining)} left"
                    else "$pct% used — KSh ${String.format("%.0f", -remaining)} over",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        spent > limit -> Color(0xFFD32F2F)
                        pct >= 80 -> Color(0xFFF57C00)
                        else -> Color(0xFF388E3C)
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClickSet) { Text(if (limit == null) "Set budget" else "Edit budget") }
        }
    }
}

@Composable
fun BudgetProgressBar(spent: Double, limit: Double) {
    val ratio = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
    val color = when {
        spent > limit -> Color(0xFFD32F2F)
        ratio >= 0.8f -> Color(0xFFF57C00)
        else -> Color(0xFF388E3C)
    }
    Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0xFFE0E0E0), RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxWidth(ratio).height(8.dp).background(color, RoundedCornerShape(4.dp)))
    }
}

@Composable
fun SetBudgetDialog(categoryName: String, currentLimit: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(currentLimit?.toInt()?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget for $categoryName") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Monthly limit (KSh)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.toDoubleOrNull()
                if (v != null && v > 0) onSave(v)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- FUNDS ----------

@Composable
fun FundsTab(
    txDao: TransactionDao,
    fundDao: FundDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val funds by fundDao.getAllFlow().collectAsState(initial = emptyList())
    val fundSpends by txDao.getFundSpendingFlow().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    val byFund: Map<String, List<FundSpend>> = fundSpends.groupBy { it.fund }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Funds", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Money set aside for a purpose — a trip, a stock run, an event.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = { showAddDialog = true }) { Text("+ Add") }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (funds.isEmpty()) {
            item {
                Text(
                    "No funds yet. Tap + Add to create your first one.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(funds) { fund ->
                val breakdown = byFund[fund.name] ?: emptyList()
                val total = breakdown.sumOf { it.total }
                FundCard(
                    fund = fund,
                    breakdown = breakdown,
                    total = total,
                    onStart = {
                        scope.launch { fundDao.setActive(fund.name) }
                    },
                    onStop = {
                        scope.launch { fundDao.clearActive() }
                    },
                    onDelete = {
                        scope.launch {
                            val count = txDao.countTransactionsForFund(fund.name)
                            if (count > 0) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Cannot delete — $count transaction${if (count == 1) "" else "s"} use this fund",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                fundDao.delete(fund)
                            }
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddFundDialog(
            existing = funds.map { it.name }.toSet(),
            onDismiss = { showAddDialog = false },
            onAdd = { name, target ->
                scope.launch {
                    fundDao.insert(FundEntity(name = name, targetAmount = target))
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun FundCard(
    fund: FundEntity,
    breakdown: List<FundSpend>,
    total: Double,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fund.name, style = MaterialTheme.typography.titleMedium)
                    if (fund.isActive) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .background(Color(0xFF2E7D32), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    if (fund.targetAmount != null && fund.targetAmount > 0)
                        "KSh ${String.format("%.0f", total)} of ${String.format("%.0f", fund.targetAmount)}"
                    else "KSh ${String.format("%.0f", total)} spent",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (fund.targetAmount != null && fund.targetAmount > 0) {
                Spacer(Modifier.height(8.dp))
                BudgetProgressBar(spent = total, limit = fund.targetAmount)
                Spacer(Modifier.height(4.dp))
                val pct = ((total / fund.targetAmount) * 100).toInt()
                val remaining = fund.targetAmount - total
                Text(
                    if (remaining >= 0) "$pct% used — KSh ${String.format("%.0f", remaining)} left"
                    else "$pct% used — KSh ${String.format("%.0f", -remaining)} over",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        total > fund.targetAmount -> Color(0xFFD32F2F)
                        pct >= 80 -> Color(0xFFF57C00)
                        else -> Color(0xFF388E3C)
                    }
                )
            }

            if (breakdown.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Where the money went",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                for (row in breakdown) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(row.category, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "KSh ${String.format("%.0f", row.total)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No transactions assigned to this fund yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (fund.isActive) {
                    Button(onClick = onStop) { Text("End tracking") }
                } else {
                    Button(onClick = onStart) { Text("Start tracking") }
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
fun AddFundDialog(
    existing: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (String, Double?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New fund") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Fund name (e.g. Gekomba Stock)") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Target amount (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                val trimmed = name.trim()
                if (trimmed.isNotEmpty() && existing.contains(trimmed)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This fund already exists.",
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                if (trimmed.isNotEmpty() && !existing.contains(trimmed)) {
                    onAdd(trimmed, target.toDoubleOrNull())
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- HISTORY ----------

@Composable
fun HistoryTab(txDao: TransactionDao, fundDao: FundDao) {
    val scope = rememberCoroutineScope()
    val transactions by txDao.getAllFlow().collectAsState(initial = emptyList())
    val funds by fundDao.getAllFlow().collectAsState(initial = emptyList())
    var assignFor by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("History", style = MaterialTheme.typography.headlineMedium)
            Text("Tap a transaction to assign or change its fund.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
        if (transactions.isEmpty()) {
            item { Text("Nothing yet. When a transaction is detected, it will appear here.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(transactions) { tx ->
                TransactionRow(tx = tx, onClick = { assignFor = tx.reference })
            }
        }
    }

    assignFor?.let { ref ->
        val current = transactions.firstOrNull { it.reference == ref }
        AssignFundDialog(
            currentFund = current?.fund,
            funds = funds,
            onDismiss = { assignFor = null },
            onSelect = { chosen ->
                scope.launch { txDao.setFund(ref, chosen) }
                assignFor = null
            }
        )
    }
}

@Composable
fun AssignFundDialog(
    currentFund: String?,
    funds: List<FundEntity>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign to fund") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    TextButton(onClick = { onSelect(null) }) {
                        Text(if (currentFund == null) "• None (current)" else "None")
                    }
                }
                items(funds) { f ->
                    TextButton(onClick = { onSelect(f.name) }) {
                        Text(if (f.name == currentFund) "• ${f.name} (current)" else f.name)
                    }
                }
                if (funds.isEmpty()) {
                    item {
                        Text(
                            "No funds yet. Create one in the Funds tab.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun TransactionRow(tx: TransactionEntity, onClick: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("KSh ${String.format("%.2f", tx.amount)}", style = MaterialTheme.typography.titleMedium)
                Text(tx.category ?: "Uncategorised", style = MaterialTheme.typography.bodyMedium)
            }
            if (tx.otherParty != null) {
                Text(tx.otherParty, style = MaterialTheme.typography.bodySmall)
            }
            Text(timeFormat.format(Date(tx.time)), style = MaterialTheme.typography.bodySmall)
            Text(intentLabel(tx), style = MaterialTheme.typography.bodySmall)
            if (tx.fund != null) {
                Text(
                    "Fund: ${tx.fund}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1976D2)
                )
            }
        }
    }
}

private fun intentLabel(tx: TransactionEntity): String = when {
    tx.intent != null -> "Intent: ${tx.intent}"
    tx.status == "SKIPPED" -> "Intent: skipped"
    tx.status == "AWAITING_INTENT" -> "Intent: pending"
    else -> "Not categorised"
}

// ---------- SETTINGS ----------

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEnableDetection: () -> Unit,
    onAllowOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onTestPrompt: () -> Unit,
    categoryDao: CategoryDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by categoryDao.getAllFlow().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf(getSavedName(context) ?: "") }

    val hasNotifAccess = remember { hasNotificationAccess(context) }
    val hasOverlay = remember { hasOverlayPermission(context) }
    val hasNotifPerm = remember { hasNotificationPermission(context) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(8.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("Personalize", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        label = { Text("Your name (used in greetings)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        saveName(context, nameDraft.trim())
                        android.widget.Toast.makeText(
                            context,
                            "Name saved",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }) { Text("Save") }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Permissions", style = MaterialTheme.typography.titleMedium)
            }
            item {
                PermissionRow(
                    label = "Notification access (M-Pesa detection)",
                    granted = hasNotifAccess,
                    buttonLabel = "Enable",
                    onClick = onEnableDetection
                )
            }
            item {
                PermissionRow(
                    label = "Display over other apps (overlay)",
                    granted = hasOverlay,
                    buttonLabel = "Enable",
                    onClick = onAllowOverlay
                )
            }
            item {
                PermissionRow(
                    label = "Post notifications",
                    granted = hasNotifPerm,
                    buttonLabel = "Enable",
                    onClick = onRequestNotifications
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Categories", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showAddDialog = true }) { Text("+ Add") }
                }
                Text(
                    "Categories appear as options in the transaction overlay.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            items(categories) { cat ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = {
                        scope.launch {
                            val count = categoryDao.countTransactionsUsing(cat.name)
                            if (count > 0) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Cannot delete — $count transaction${if (count == 1) "" else "s"} use this category",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                categoryDao.delete(cat)
                            }
                        }
                    }) { Text("Delete") }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Developer", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Button(onClick = onTestPrompt, modifier = Modifier.fillMaxWidth()) {
                    Text("Test TD Prompt")
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text(
                    "TD records M-Pesa transactions on your device. Data stays on this phone.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            existing = categories.map { it.name }.toSet(),
            onDismiss = { showAddDialog = false },
            onAdd = { name ->
                scope.launch { categoryDao.insert(CategoryEntity(name = name)) }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun PermissionRow(
    label: String,
    granted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (granted) {
                Text(
                    "✓ Granted",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF388E3C)
                )
            }
        }
        if (!granted) {
            Button(onClick = onClick) { Text(buttonLabel) }
        }
    }
}

@Composable
fun AddCategoryDialog(existing: Set<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Category name") },
                    singleLine = true
                )
                val trimmed = text.trim()
                if (trimmed.isNotEmpty() && existing.contains(trimmed)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This category already exists.",
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = text.trim()
                if (trimmed.isNotEmpty() && !existing.contains(trimmed)) onAdd(trimmed)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- helpers ----------

private fun greeting(name: String?): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val base = when (h) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
    return if (name.isNullOrBlank()) base else "$base, $name"
}

private fun startOfCurrentMonth(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun emptySummary() = TransactionSummary(
    grandTotal = 0.0, totalCount = 0,
    plannedTotal = 0.0, plannedCount = 0,
    unplannedTotal = 0.0, unplannedCount = 0,
    unexpectedTotal = 0.0, unexpectedCount = 0,
    unknownTotal = 0.0, unknownCount = 0
)

private fun hasNotificationAccess(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabled.contains(context.packageName, ignoreCase = true)
}

private fun hasOverlayPermission(context: Context): Boolean =
    Settings.canDrawOverlays(context)

private fun hasNotificationPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else true

private fun getSavedName(context: Context): String? =
    context.getSharedPreferences("td_prefs", Context.MODE_PRIVATE)
        .getString("user_name", null)

private fun saveName(context: Context, name: String) {
    context.getSharedPreferences("td_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("user_name", name.ifBlank { null })
        .apply()
}