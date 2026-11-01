package ir.hesabyar.finance.ui

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import ir.hesabyar.finance.data.*
import ir.hesabyar.finance.importer.BankFileParser
import ir.hesabyar.finance.notify.InstallmentScheduler
import ir.hesabyar.finance.util.DateUtil
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

class FinanceViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FinanceRepository(app)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var accounts by mutableStateOf<List<Account>>(emptyList()); private set
    var people by mutableStateOf<List<PersonBalance>>(emptyList()); private set
    var personHistories by mutableStateOf<Map<Long, List<PersonLedgerEntry>>>(emptyMap()); private set
    var categories by mutableStateOf<List<Category>>(emptyList()); private set
    var transactions by mutableStateOf<List<TransactionRow>>(emptyList()); private set
    var installments by mutableStateOf<List<Installment>>(emptyList()); private set
    var dashboard by mutableStateOf(DashboardSummary()); private set
    var report by mutableStateOf(ReportSummary()); private set
    var bankEntries by mutableStateOf<List<BankEntry>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var toast by mutableStateOf<String?>(null); private set

    init { refresh() }

    fun consumeToast() { toast = null }

    fun refresh() = runBg {
        repo.repairUnassignedSmsTransactions()
        val a = repo.accounts(); val p = repo.personBalances(); val h = repo.allPersonLedgerEntries().groupBy { it.personId }
        val c = repo.categories(); val t = repo.transactions(limit = 300)
        val i = repo.installments(); val d = repo.dashboard(); val b = repo.recentImportEntries()
        val range = monthRange(); val r = repo.report(range.first, range.second)
        main.post { accounts=a; people=p; personHistories=h; categories=c; transactions=t; installments=i; dashboard=d; bankEntries=b; report=r }
    }

    fun addCategory(category: Category) = runBg("دسته‌بندی اضافه شد") { repo.addCategory(category); refreshDirect() }
    fun addAccount(account: Account) = runBg("حساب اضافه شد") { repo.addAccount(account); refreshDirect() }
    fun addPerson(person: Person) = runBg("شخص اضافه شد") { repo.addPerson(person); refreshDirect() }

    fun addTransaction(tx: FinanceTransaction, splits: List<SplitDraft>) = runBg("تراکنش ثبت شد") {
        repo.addTransaction(tx, splits); refreshDirect()
    }

    fun updateTransaction(tx: FinanceTransaction) = runBg("تراکنش و موجودی اصلاح شد") {
        repo.updateTransaction(tx); refreshDirect()
    }

    fun addPersonLedger(personId: Long, amount: Long, relation: SplitRelation, note: String, occurredAt: Long) =
        runBg("طلب/بدهی ثبت شد") {
            repo.addPersonLedgerEntry(personId, amount, relation, note, occurredAt)
            refreshDirect()
        }

    fun settle(personId: Long, amount: Long, theyPaidMe: Boolean, note: String) = runBg("تسویه ثبت شد") {
        repo.settlePerson(personId, amount, theyPaidMe, note); refreshDirect()
    }

    fun addInstallmentPlan(item: Installment, count: Int) = runBg("برنامه اقساط ثبت شد") {
        val zone = ZoneId.systemDefault()
        val baseDate = java.time.Instant.ofEpochMilli(item.dueAt).atZone(zone).toLocalDate()
        val safeCount = count.coerceIn(1, 120)
        repeat(safeCount) { index ->
            val dueDate = DateUtil.plusJalaliMonths(baseDate, index.toLong() * item.repeatEvery.coerceAtLeast(1))
            val due = dueDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val titled = item.copy(title = if (safeCount > 1) "${item.title} (${index + 1}/$safeCount)" else item.title, dueAt = due)
            val id = repo.addInstallment(titled)
            if (id > 0) InstallmentScheduler.schedule(getApplication(), titled.copy(id=id))
        }
        refreshDirect()
    }

    fun markInstallmentPaid(id: Long) {
        val item = installments.firstOrNull { it.id == id && it.status == InstallmentStatus.OPEN } ?: return
        runBg("قسط پرداخت و در هزینه‌ها ثبت شد") {
            ir.hesabyar.finance.notify.InstallmentScheduler.cancel(getApplication(), id)
            repo.addTransaction(FinanceTransaction(amount=item.amount,type=TransactionType.EXPENSE,occurredAt=System.currentTimeMillis(),reason="پرداخت ${item.title}",accountId=item.accountId,categoryId=null,personId=item.personId,source=TransactionSource.MANUAL,note="ثبت خودکار از بخش اقساط"))
            repo.markInstallmentPaid(id)
            refreshDirect()
        }
    }

    fun importBank(uri: Uri, valuesAreRial: Boolean, accountId: Long?) = runBg {
        val result = BankFileParser.parse(getApplication(), uri, repo, valuesAreRial, accountId)
        val name = BankFileParser.queryName(getApplication(), uri)
        val batch = repo.saveImport(name, result.entries)
        refreshDirect()
        main.post {
            toast = if (result.entries.isEmpty()) "تراکنش قابل‌خواندن در فایل پیدا نشد" else "${batch.totalRows} ردیف وارد شد؛ ${batch.matchedRows} مورد تطبیق پیدا کرد"
        }
    }

    fun loadReport(from: Long, to: Long, accountId: Long? = null, personId: Long? = null) = runBg {
        val r = repo.report(from, to, accountId, personId); main.post { report = r }
    }

    private fun refreshDirect() {
        repo.repairUnassignedSmsTransactions()
        val a = repo.accounts(); val p = repo.personBalances(); val h = repo.allPersonLedgerEntries().groupBy { it.personId }
        val c = repo.categories(); val t = repo.transactions(limit=300)
        val i = repo.installments(); val d = repo.dashboard(); val b = repo.recentImportEntries()
        main.post { accounts=a; people=p; personHistories=h; categories=c; transactions=t; installments=i; dashboard=d; bankEntries=b }
    }

    private fun runBg(success: String? = null, block: () -> Unit) {
        busy = true
        executor.execute {
            try { block(); success?.let { main.post { toast=it } } }
            catch (t: Throwable) { main.post { toast = t.message ?: "خطایی رخ داد" } }
            finally { main.post { busy=false } }
        }
    }

    private fun monthRange(): Pair<Long,Long> = DateUtil.jalaliMonthRange()

    override fun onCleared() { executor.shutdown(); super.onCleared() }
}
