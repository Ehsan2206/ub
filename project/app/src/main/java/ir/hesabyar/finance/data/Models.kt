package ir.hesabyar.finance.data

enum class TransactionType { INCOME, EXPENSE }
enum class TransactionSource { MANUAL, SMS, BANK_IMPORT }
enum class SplitRelation { OWES_ME, I_OWE }
enum class InstallmentStatus { OPEN, PAID }

data class Account(
    val id: Long = 0,
    val name: String,
    val type: String = "بانک",
    val bankName: String = "",
    val cardLast4: String = "",
    val balance: Long = 0,
    val groupName: String = "شخصی",
    val excluded: Boolean = false,
    val smsIdentity: String = ""
)

data class Person(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val groupName: String = "دوستان",
    val note: String = ""
)

data class Category(
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val icon: String = "•"
)

data class FinanceTransaction(
    val id: Long = 0,
    val amount: Long,
    val type: TransactionType,
    val occurredAt: Long,
    val reason: String,
    val accountId: Long?,
    val categoryId: Long?,
    val personId: Long?,
    val source: TransactionSource,
    val note: String = "",
    val externalKey: String = ""
)

data class TransactionRow(
    val transaction: FinanceTransaction,
    val accountName: String = "",
    val personName: String = "",
    val categoryName: String = ""
)

data class SplitDraft(
    val personId: Long,
    val amount: Long,
    val relation: SplitRelation
)

data class PersonBalance(
    val person: Person,
    val net: Long
)

data class Installment(
    val id: Long = 0,
    val title: String,
    val amount: Long,
    val dueAt: Long,
    val accountId: Long?,
    val personId: Long?,
    val repeatUnit: String = "ماه",
    val repeatEvery: Int = 1,
    val reminderDaysBefore: Int = 1,
    val status: InstallmentStatus = InstallmentStatus.OPEN
)

data class DashboardSummary(
    val totalBalance: Long = 0,
    val todayIncome: Long = 0,
    val todayExpense: Long = 0,
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val receivable: Long = 0,
    val payable: Long = 0
)

data class ReportSummary(
    val income: Long = 0,
    val expense: Long = 0,
    val net: Long = 0,
    val byCategory: List<Pair<String, Long>> = emptyList()
)

data class BankEntry(
    val id: Long = 0,
    val batchId: Long = 0,
    val occurredAt: Long,
    val amount: Long,
    val type: TransactionType,
    val description: String,
    val balanceAfter: Long? = null,
    val matchedTransactionId: Long? = null,
    val fingerprint: String
)

data class ImportBatch(
    val id: Long = 0,
    val name: String,
    val importedAt: Long,
    val totalRows: Int,
    val matchedRows: Int
)

data class TransactionFilter(
    val from: Long? = null,
    val to: Long? = null,
    val accountId: Long? = null,
    val personId: Long? = null,
    val type: TransactionType? = null,
    val query: String = ""
)

data class LedgerEntry(val id: Long, val occurredAt: Long, val signed: Long, val note: String, val transactionId: Long?)
