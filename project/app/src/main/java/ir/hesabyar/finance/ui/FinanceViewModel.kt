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
    private var reportBounds=DateUtil.jalaliMonthRange()
    private var reportAccount:Long?=null
    private var reportPerson:Long?=null

    var ledgers by mutableStateOf<Map<Long,List<LedgerEntry>>>(emptyMap()); private set
    var senderRules by mutableStateOf<Map<String,String>>(emptyMap()); private set
    var smsResult by mutableStateOf<String?>(null); private set
    var accounts by mutableStateOf<List<Account>>(emptyList()); private set
    var people by mutableStateOf<List<PersonBalance>>(emptyList()); private set
    var categories by mutableStateOf<List<Category>>(emptyList()); private set
    var transactions by mutableStateOf<List<TransactionRow>>(emptyList()); private set
    var installments by mutableStateOf<List<Installment>>(emptyList()); private set
    var dashboard by mutableStateOf(DashboardSummary()); private set
    var report by mutableStateOf(ReportSummary()); private set
    var bankEntries by mutableStateOf<List<BankEntry>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var toast by mutableStateOf<String?>(null); private set

    init { refresh() }

    fun deleteTransaction(id:Long)=runBg("تراکنش حذف شد") { repo.deleteTransaction(id); refreshDirect() }
    fun excludeAccount(id:Long,excluded:Boolean)=runBg { repo.setAccountExcluded(id,excluded); refreshDirect() }
    fun saveSender(sender:String,bank:String)=runBg("سرشماره ذخیره شد") { ir.hesabyar.finance.sms.SmsImporter.saveSender(getApplication(),sender,bank); refreshDirect() }
    fun removeSender(sender:String)=runBg { ir.hesabyar.finance.sms.SmsImporter.removeSender(getApplication(),sender); refreshDirect() }
    fun addLedger(pid:Long,signed:Long,date:Long,note:String)=runBg("گردش شخص ثبت شد") { repo.addLedger(pid,signed,date,note); refreshDirect() }
    fun deleteLedger(id:Long)=runBg("گردش حذف شد") { repo.deleteLedger(id); refreshDirect() }
    fun importSms(from:Long,to:Long,latest:Boolean) = runBg {
        val result=ir.hesabyar.finance.sms.SmsImporter.import(getApplication(),repo,from,to,latest)
        refreshDirect()
        main.post { smsResult="${result.scanned} پیامک بررسی شد؛ ${result.changed} مورد به‌روز شد. ${result.unrecognized} پیامک مالی قابل تشخیص نبود. برای موارد ناشناخته، سرشماره را در بخش پیامک‌ها تعریف کنید." }
    }
    fun clearSmsResult() { smsResult=null }
    fun consumeToast() { toast = null }

    fun refresh() = runBg {
        val a = repo.accounts(); val p = repo.personBalances(); val c = repo.categories(); val t = repo.transactions(limit = 300)
        val i = repo.installments(); val d = repo.dashboard(); val b = repo.recentImportEntries()
        val r = repo.report(reportBounds.first, reportBounds.second,reportAccount,reportPerson)
        val l=p.associate { it.person.id to repo.ledger(it.person.id) }; val rules=ir.hesabyar.finance.sms.SmsImporter.custom(getApplication())
        main.post { ledgers=l; senderRules=rules; accounts=a; people=p; categories=c; transactions=t; installments=i; dashboard=d; bankEntries=b; report=r }
    }

    fun addCategory(category: Category) = runBg("دسته‌بندی اضافه شد") { repo.addCategory(category); refreshDirect() }
    fun addAccount(account: Account) = runBg("حساب اضافه شد") { repo.addAccount(account); refreshDirect() }
    fun addPerson(person: Person) = runBg("شخص اضافه شد") { repo.addPerson(person); refreshDirect() }

    fun addTransaction(tx: FinanceTransaction, splits: List<SplitDraft>) = runBg("تراکنش ثبت شد") {
        repo.addTransaction(tx, splits); refreshDirect()
    }

    fun settle(personId: Long, amount: Long, theyPaidMe: Boolean) = runBg("تسویه ثبت شد") {
        repo.settlePerson(personId, amount, theyPaidMe); refreshDirect()
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
        reportBounds=from to to;reportAccount=accountId;reportPerson=personId
        val r = repo.report(from, to, accountId, personId); main.post { report = r }
    }

    private fun refreshDirect() {
        val a = repo.accounts(); val p = repo.personBalances(); val c = repo.categories(); val t = repo.transactions(limit=300)
        val i = repo.installments(); val d = repo.dashboard(); val b = repo.recentImportEntries()
        val updatedReport=repo.report(reportBounds.first,reportBounds.second,reportAccount,reportPerson)
        main.post { report=updatedReport }
        val l=p.associate { it.person.id to repo.ledger(it.person.id) }; val rules=ir.hesabyar.finance.sms.SmsImporter.custom(getApplication())
        main.post { ledgers=l; senderRules=rules; accounts=a; people=p; categories=c; transactions=t; installments=i; dashboard=d; bankEntries=b }
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

    override fun onCleared() { executor.execute { repo.close() }; executor.shutdown(); super.onCleared() }
}
