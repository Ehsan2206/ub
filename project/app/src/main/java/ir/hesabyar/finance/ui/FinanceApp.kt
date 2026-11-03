package ir.hesabyar.finance.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ir.hesabyar.finance.ui.screens.*

private enum class MainTab(val title: String) {
    HOME("خانه"), TRANSACTIONS("تراکنش‌ها"), PEOPLE("اشخاص"), REPORTS("گزارش‌ها"), MORE("حساب‌ها")
}

@Composable
fun FinanceApp(vm: FinanceViewModel) {
    val context = LocalContext.current
    var showSmsImport by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(MainTab.HOME) }
    var showAddTransaction by remember { mutableStateOf(false) }
    var showSmsDisclosure by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }

    fun hasSmsPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    var smsAllowed by remember { mutableStateOf(hasSmsPermission()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { vm.refresh() }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter("ir.hesabyar.finance.DATA_CHANGED"), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        smsAllowed = result[Manifest.permission.RECEIVE_SMS] == true
        localMessage = if (smsAllowed) "ثبت خودکار پیامک‌های جدید فعال شد" else "بدون مجوز پیامک، ثبت دستی همچنان فعال است."
    }
    val historyLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) showSmsImport=true else localMessage="برای ورود تاریخچه، مجوز خواندن پیامک لازم است."
    }
    fun openHistory() {
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED) showSmsImport=true
        else historyLauncher.launch(Manifest.permission.READ_SMS)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        localMessage = if (granted) "یادآوری اقساط فعال شد" else "مجوز اعلان داده نشد؛ اقساط ثبت می‌شوند اما اعلان نمایش داده نمی‌شود."
    }

    LaunchedEffect(vm.toast, localMessage) {
        val message = localMessage ?: vm.toast
        if (!message.isNullOrBlank()) {
            snack.showSnackbar(message)
            if (localMessage != null) localMessage = null else vm.consumeToast()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                NavigationBar(tonalElevation = 4.dp) {
                    MainTab.entries.forEach { item ->
                        val icon = when (item) {
                            MainTab.HOME -> Icons.Rounded.Home
                            MainTab.TRANSACTIONS -> Icons.Rounded.ReceiptLong
                            MainTab.PEOPLE -> Icons.Rounded.Groups
                            MainTab.REPORTS -> Icons.Rounded.BarChart
                            MainTab.MORE -> Icons.Rounded.MoreHoriz
                        }
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(icon, null) },
                            label = { Text(item.title, fontSize = 10.sp) },
                            alwaysShowLabel = true
                        )
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = {
                if (tab == MainTab.HOME || tab == MainTab.TRANSACTIONS) {
                    FloatingActionButton(
                        onClick = { showAddTransaction = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "ثبت تراکنش")
                    }
                }
            }
        ) { padding ->
            when (tab) {
                MainTab.HOME -> HomeScreen(
                    padding = padding,
                    summary = vm.dashboard,
                    recent = vm.transactions.filter { row -> vm.accounts.none { it.id==row.transaction.accountId && it.excluded } }.take(6),
                    people = vm.people,
                    smsAllowed = smsAllowed,
                    onRequestSms = { showSmsDisclosure = true },
                    onOpenTransactions = { tab = MainTab.TRANSACTIONS },
                    onOpenPeople = { tab = MainTab.PEOPLE },
                    onImportSms = { openHistory() }
                )
                MainTab.TRANSACTIONS -> TransactionsScreen(padding, vm.transactions, vm.accounts, vm.people.map { it.person },vm::deleteTransaction)
                MainTab.PEOPLE -> PeopleScreen(padding,vm.people,vm.ledgers,vm::addPerson,vm::addLedger,vm::deleteLedger)
                MainTab.REPORTS -> ReportsScreen(padding, vm.accounts, vm.people.map { it.person }, vm.report, vm::loadReport)
                MainTab.MORE -> MoreScreen(
                    padding,
                    vm.accounts,
                    vm.people.map { it.person },
                    vm.categories,
                    vm.installments,
                    vm.bankEntries,
                    vm::addAccount,
                    vm::addCategory,
                    vm::addInstallmentPlan,
                    vm::markInstallmentPaid,
                    vm::importBank,
                    onExclude=vm::excludeAccount,
                    onSms={openHistory()},
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else localMessage = "مجوز اعلان فعال است"
                    }
                )
            }
        }

        if(vm.busy && !showSmsImport) LinearProgressIndicator(modifier=Modifier.fillMaxSize().wrapContentHeight(androidx.compose.ui.Alignment.Top))
        if(showSmsImport) SmsImportDialog(vm.busy,vm.senderRules,{showSmsImport=false},vm::importSms,vm::saveSender,vm::removeSender)
        vm.smsResult?.let { result -> AlertDialog(onDismissRequest=vm::clearSmsResult,title={Text("نتیجه بررسی پیامک‌ها")},text={Text(result)},confirmButton={Button(onClick=vm::clearSmsResult){Text("متوجه شدم")}}) }
        if (showAddTransaction) {
            AddTransactionDialog(
                accounts = vm.accounts,
                people = vm.people.map { it.person },
                categories = vm.categories,
                onDismiss = { showAddTransaction = false },
                onSave = { tx, splits -> vm.addTransaction(tx, splits); showAddTransaction = false }
            )
        }

        if (showSmsDisclosure) {
            AlertDialog(
                onDismissRequest = { showSmsDisclosure = false },
                icon = { Icon(Icons.Rounded.Sms, null) },
                title = { Text("دسترسی به پیامک‌های بانکی") },
                text = { Text("حساب‌یار فقط پیامک‌های بانکی جدیدی را که بعد از فعال‌سازی این قابلیت دریافت می‌شوند روی همین دستگاه بررسی می‌کند. برای ورود پیامک‌های قدیمی می‌توانید جداگانه بازه یا آخرین مانده را انتخاب کنید. هیچ پیامکی به سرور ارسال نمی‌شود. این دسترسی اختیاری است و ثبت دستی بدون آن هم کار می‌کند.") },
                confirmButton = {
                    Button(onClick = {
                        showSmsDisclosure = false
                        smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
                    }) { Text("ادامه و دادن مجوز") }
                },
                dismissButton = { TextButton(onClick = { showSmsDisclosure = false }) { Text("فعلاً نه") } }
            )
        }
    }
}
