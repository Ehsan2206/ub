package ir.hesabyar.finance.data

import ir.hesabyar.finance.util.DateUtil

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FinanceRepository(context: Context) : java.io.Closeable {
    override fun close() { dbHelper.close() }
    private val dbHelper = FinanceDb(context.applicationContext)

    fun accounts(): List<Account> = dbHelper.readableDatabase.rawQuery(
        "SELECT id,name,type,bank_name,card_last4,balance,group_name,excluded,sms_identity FROM accounts ORDER BY id DESC", null
    ).use { c -> buildList { while (c.moveToNext()) add(c.toAccount()) } }

    fun people(): List<Person> = dbHelper.readableDatabase.rawQuery(
        "SELECT id,name,phone,group_name,note FROM people ORDER BY name COLLATE NOCASE", null
    ).use { c -> buildList { while (c.moveToNext()) add(c.toPerson()) } }

    fun categories(type: TransactionType? = null): List<Category> {
        val sql = if (type == null) "SELECT id,name,type,icon FROM categories ORDER BY id" else "SELECT id,name,type,icon FROM categories WHERE type=? ORDER BY id"
        val args = type?.let { arrayOf(it.name) }
        return dbHelper.readableDatabase.rawQuery(sql, args).use { c ->
            buildList { while (c.moveToNext()) add(Category(c.getLong(0), c.getString(1), TransactionType.valueOf(c.getString(2)), c.getString(3))) }
        }
    }


    fun addCategory(category: Category): Long {
        val cv = ContentValues().apply { put("name", category.name); put("type", category.type.name); put("icon", category.icon) }
        return dbHelper.writableDatabase.insert("categories", null, cv)
    }

    fun addAccount(account: Account): Long {
        val cv = ContentValues().apply {
            put("name", account.name); put("type", account.type); put("bank_name", account.bankName)
            put("card_last4", account.cardLast4); put("balance", account.balance); put("group_name", account.groupName); put("excluded", if(account.excluded) 1 else 0); put("sms_identity", account.smsIdentity)
        }
        return dbHelper.writableDatabase.insert("accounts", null, cv)
    }

    fun addPerson(person: Person): Long {
        val cv = ContentValues().apply {
            put("name", person.name); put("phone", person.phone); put("group_name", person.groupName); put("note", person.note)
        }
        return dbHelper.writableDatabase.insert("people", null, cv)
    }

    fun addTransaction(tx: FinanceTransaction, splits: List<SplitDraft> = emptyList()): Long {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put("amount", tx.amount); put("type", tx.type.name); put("occurred_at", tx.occurredAt)
                put("reason", tx.reason); tx.accountId?.let { put("account_id", it) }; tx.categoryId?.let { put("category_id", it) }
                tx.personId?.let { put("person_id", it) }; put("source", tx.source.name); put("note", tx.note); if (tx.externalKey.isNotBlank()) put("external_key", tx.externalKey) else putNull("external_key")
            }
            val id = db.insertWithOnConflict("transactions", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            if (id > 0) {
                tx.accountId?.let { accountId ->
                    val delta = if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
                    db.execSQL("UPDATE accounts SET balance=balance+? WHERE id=? AND balance_at<?", arrayOf<Any>(delta, accountId, tx.occurredAt))
                }
                splits.forEach { split ->
                    val signed = if (split.relation == SplitRelation.OWES_ME) split.amount else -split.amount
                    val lv = ContentValues().apply {
                        put("person_id", split.personId); put("amount_signed", signed); put("occurred_at", tx.occurredAt)
                        put("note", tx.reason.ifBlank { "حساب اشخاص" }); put("transaction_id", id)
                    }
                    db.insert("person_ledger", null, lv)
                }
                tx.personId?.takeIf { splits.isEmpty() }?.let { personId ->
                    // A normal transaction with a counterparty doesn't alter debt automatically.
                }
            }
            db.setTransactionSuccessful()
            id
        } finally { db.endTransaction() }
    }

    fun transactionByExternalKey(key: String): Long? {
        if (key.isBlank()) return null
        return dbHelper.readableDatabase.rawQuery("SELECT id FROM transactions WHERE external_key=? LIMIT 1", arrayOf(key)).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }

    fun transactions(filter: TransactionFilter = TransactionFilter(), limit: Int = 500): List<TransactionRow> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        filter.from?.let { where += "t.occurred_at>=?"; args += it.toString() }
        filter.to?.let { where += "t.occurred_at<=?"; args += it.toString() }
        filter.accountId?.let { where += "t.account_id=?"; args += it.toString() }
        filter.personId?.let { where += "(t.person_id=? OR EXISTS (SELECT 1 FROM person_ledger pl WHERE pl.transaction_id=t.id AND pl.person_id=?))"; args += it.toString(); args += it.toString() }
        filter.type?.let { where += "t.type=?"; args += it.name }
        filter.query.trim().takeIf { it.isNotBlank() }?.let { q -> where += "(t.reason LIKE ? OR t.note LIKE ?)"; args += "%$q%"; args += "%$q%" }
        val clause = if (where.isEmpty()) "" else "WHERE ${where.joinToString(" AND ")}"
        val sql = """
            SELECT t.id,t.amount,t.type,t.occurred_at,t.reason,t.account_id,t.category_id,t.person_id,t.source,t.note,t.external_key,
                   COALESCE(a.name,''),COALESCE(p.name,(SELECT GROUP_CONCAT(pp.name,'، ') FROM person_ledger pl JOIN people pp ON pp.id=pl.person_id WHERE pl.transaction_id=t.id),''),COALESCE(c.name,'')
            FROM transactions t
            LEFT JOIN accounts a ON a.id=t.account_id
            LEFT JOIN people p ON p.id=t.person_id
            LEFT JOIN categories c ON c.id=t.category_id
            $clause
            ORDER BY t.occurred_at DESC,t.id DESC LIMIT $limit
        """.trimIndent()
        return dbHelper.readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val tx = FinanceTransaction(
                        id = c.getLong(0), amount = c.getLong(1), type = TransactionType.valueOf(c.getString(2)), occurredAt = c.getLong(3),
                        reason = c.getString(4), accountId = c.longOrNull(5), categoryId = c.longOrNull(6), personId = c.longOrNull(7),
                        source = TransactionSource.valueOf(c.getString(8)), note = c.getString(9), externalKey = c.getString(10).orEmpty()
                    )
                    add(TransactionRow(tx, c.getString(11), c.getString(12), c.getString(13)))
                }
            }
        }
    }

    fun dashboard(): DashboardSummary {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val (monthStart, monthEnd) = DateUtil.jalaliMonthRange(today)
        fun sum(type: TransactionType, from: Long, to: Long): Long = dbHelper.readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE (account_id IS NULL OR account_id IN (SELECT id FROM accounts WHERE excluded=0)) AND type=? AND occurred_at BETWEEN ? AND ?",
            arrayOf(type.name, from.toString(), to.toString())
        ).use { c -> c.moveToFirst(); c.getLong(0) }
        val totalBalance = dbHelper.readableDatabase.rawQuery("SELECT COALESCE(SUM(balance),0) FROM accounts WHERE excluded=0", null).use { c -> c.moveToFirst(); c.getLong(0) }
        val balances = personBalances()
        return DashboardSummary(
            totalBalance = totalBalance,
            todayIncome = sum(TransactionType.INCOME, dayStart, dayEnd), todayExpense = sum(TransactionType.EXPENSE, dayStart, dayEnd),
            monthIncome = sum(TransactionType.INCOME, monthStart, monthEnd), monthExpense = sum(TransactionType.EXPENSE, monthStart, monthEnd),
            receivable = balances.filter { it.net > 0 }.sumOf { it.net }, payable = -balances.filter { it.net < 0 }.sumOf { it.net }
        )
    }

    fun personBalances(): List<PersonBalance> {
        val sql = """
            SELECT p.id,p.name,p.phone,p.group_name,p.note,COALESCE(SUM(l.amount_signed),0) net
            FROM people p LEFT JOIN person_ledger l ON p.id=l.person_id AND (l.transaction_id IS NULL OR EXISTS (SELECT 1 FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id WHERE t.id=l.transaction_id AND COALESCE(a.excluded,0)=0))
            GROUP BY p.id ORDER BY p.name COLLATE NOCASE
        """.trimIndent()
        return dbHelper.readableDatabase.rawQuery(sql, null).use { c ->
            buildList { while (c.moveToNext()) add(PersonBalance(Person(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4)), c.getLong(5))) }
        }
    }

    fun personLedger(personId: Long): List<Pair<Long, Pair<Long, String>>> = dbHelper.readableDatabase.rawQuery(
        "SELECT occurred_at,amount_signed,note FROM person_ledger WHERE person_id=? ORDER BY occurred_at DESC,id DESC", arrayOf(personId.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to (c.getLong(1) to c.getString(2))) } }

    fun settlePerson(personId: Long, amount: Long, theyPaidMe: Boolean, note: String = "تسویه") {
        val signed = if (theyPaidMe) -amount else amount
        val cv = ContentValues().apply { put("person_id", personId); put("amount_signed", signed); put("occurred_at", System.currentTimeMillis()); put("note", note) }
        dbHelper.writableDatabase.insert("person_ledger", null, cv)
    }

    fun addInstallment(item: Installment): Long {
        val cv = ContentValues().apply {
            put("title", item.title); put("amount", item.amount); put("due_at", item.dueAt)
            item.accountId?.let { put("account_id", it) }; item.personId?.let { put("person_id", it) }
            put("repeat_unit", item.repeatUnit); put("repeat_every", item.repeatEvery); put("reminder_days_before", item.reminderDaysBefore); put("status", item.status.name)
        }
        return dbHelper.writableDatabase.insert("installments", null, cv)
    }

    fun installments(openOnly: Boolean = false): List<Installment> {
        val where = if (openOnly) "WHERE status='OPEN'" else ""
        return dbHelper.readableDatabase.rawQuery("SELECT id,title,amount,due_at,account_id,person_id,repeat_unit,repeat_every,reminder_days_before,status FROM installments $where ORDER BY due_at", null).use { c ->
            buildList { while (c.moveToNext()) add(Installment(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3),c.longOrNull(4),c.longOrNull(5),c.getString(6),c.getInt(7),c.getInt(8),InstallmentStatus.valueOf(c.getString(9)))) }
        }
    }

    fun markInstallmentPaid(id: Long) { dbHelper.writableDatabase.execSQL("UPDATE installments SET status='PAID' WHERE id=?", arrayOf<Any>(id)) }

    fun report(from: Long, to: Long, accountId: Long? = null, personId: Long? = null): ReportSummary {
        val baseWhere = mutableListOf("t.occurred_at BETWEEN ? AND ?", "(t.account_id IS NULL OR t.account_id IN (SELECT id FROM accounts WHERE excluded=0))")
        val args = mutableListOf(from.toString(), to.toString())
        accountId?.let { baseWhere += "t.account_id=?"; args += it.toString() }
        personId?.let { baseWhere += "(t.person_id=? OR EXISTS (SELECT 1 FROM person_ledger pl WHERE pl.transaction_id=t.id AND pl.person_id=?))"; args += it.toString(); args += it.toString() }
        val where = baseWhere.joinToString(" AND ")
        var income = 0L; var expense = 0L
        dbHelper.readableDatabase.rawQuery("SELECT type,COALESCE(SUM(amount),0) FROM transactions t WHERE $where GROUP BY type", args.toTypedArray()).use { c ->
            while (c.moveToNext()) if (c.getString(0)=="INCOME") income=c.getLong(1) else expense=c.getLong(1)
        }
        val cats = dbHelper.readableDatabase.rawQuery(
            "SELECT COALESCE(c.name,'بدون دسته'),SUM(t.amount) FROM transactions t LEFT JOIN categories c ON c.id=t.category_id WHERE $where AND t.type='EXPENSE' GROUP BY c.name ORDER BY SUM(t.amount) DESC LIMIT 8",
            args.toTypedArray()
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getLong(1)) } }
        return ReportSummary(income, expense, income-expense, cats)
    }

    fun findMatch(amount: Long, type: TransactionType, occurredAt: Long, accountId: Long? = null, toleranceDays: Int = 2): Long? {
        val tol = toleranceDays * 86_400_000L
        val accountClause = if (accountId == null) "" else " AND account_id=?"
        val args = mutableListOf(amount.toString(), type.name, (occurredAt-tol).toString(), (occurredAt+tol).toString())
        if (accountId != null) args += accountId.toString()
        args += occurredAt.toString()
        return dbHelper.readableDatabase.rawQuery(
            "SELECT id FROM transactions WHERE amount=? AND type=? AND occurred_at BETWEEN ? AND ?$accountClause ORDER BY ABS(occurred_at-?) LIMIT 1",
            args.toTypedArray()
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }

    fun saveImport(name: String, entries: List<BankEntry>): ImportBatch {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val batchCv = ContentValues().apply { put("name", name); put("imported_at", System.currentTimeMillis()); put("total_rows", entries.size); put("matched_rows", entries.count { it.matchedTransactionId != null }) }
            val batchId = db.insert("import_batches", null, batchCv)
            entries.forEach { e ->
                val cv = ContentValues().apply {
                    put("batch_id", batchId); put("occurred_at", e.occurredAt); put("amount", e.amount); put("type", e.type.name); put("description", e.description)
                    e.balanceAfter?.let { put("balance_after", it) }; e.matchedTransactionId?.let { put("matched_transaction_id", it) }; put("fingerprint", e.fingerprint)
                }
                db.insertWithOnConflict("bank_entries", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
            ImportBatch(batchId, name, System.currentTimeMillis(), entries.size, entries.count { it.matchedTransactionId != null })
        } finally { db.endTransaction() }
    }

    fun recentImportEntries(limit: Int = 200): List<BankEntry> = dbHelper.readableDatabase.rawQuery(
        "SELECT id,batch_id,occurred_at,amount,type,description,balance_after,matched_transaction_id,fingerprint FROM bank_entries ORDER BY id DESC LIMIT $limit", null
    ).use { c -> buildList { while (c.moveToNext()) add(BankEntry(c.getLong(0),c.getLong(1),c.getLong(2),c.getLong(3),TransactionType.valueOf(c.getString(4)),c.getString(5),c.longOrNull(6),c.longOrNull(7),c.getString(8))) } }

    fun setAccountExcluded(id: Long, excluded: Boolean) {
        dbHelper.writableDatabase.execSQL("UPDATE accounts SET excluded=? WHERE id=?", arrayOf<Any>(if(excluded) 1 else 0, id))
    }

    fun ledger(personId: Long): List<LedgerEntry> = dbHelper.readableDatabase.rawQuery(
        "SELECT l.id,l.occurred_at,l.amount_signed,l.note,l.transaction_id FROM person_ledger l LEFT JOIN transactions t ON t.id=l.transaction_id LEFT JOIN accounts a ON a.id=t.account_id WHERE l.person_id=? AND COALESCE(a.excluded,0)=0 ORDER BY l.occurred_at DESC,l.id DESC", arrayOf(personId.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(LedgerEntry(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),c.longOrNull(4))) } }

    fun addLedger(personId: Long, signed: Long, date: Long, note: String) {
        require(signed != 0L)
        val cv=ContentValues().apply { put("person_id",personId); put("amount_signed",signed); put("occurred_at",date); put("note",note) }
        dbHelper.writableDatabase.insertOrThrow("person_ledger",null,cv)
    }

    fun deleteLedger(id: Long) {
        val db=dbHelper.writableDatabase
        val tx=db.rawQuery("SELECT transaction_id FROM person_ledger WHERE id=?",arrayOf(id.toString())).use { c -> if(c.moveToFirst()) c.longOrNull(0) else null }
        if(tx!=null) deleteTransaction(tx) else db.delete("person_ledger","id=?",arrayOf(id.toString()))
    }

    fun deleteTransaction(id: Long) {
        val db=dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT amount,type,account_id,occurred_at,external_key FROM transactions WHERE id=?",arrayOf(id.toString())).use { c ->
                if(c.moveToFirst()) {
                    val delta=if(c.getString(1)=="INCOME") -c.getLong(0) else c.getLong(0)
                    c.longOrNull(2)?.let { db.execSQL("UPDATE accounts SET balance=balance+? WHERE id=? AND balance_at<?",arrayOf<Any>(delta,it,c.getLong(3))) }
                    c.getString(4)?.takeIf { it.isNotBlank() }?.let { db.execSQL("INSERT OR IGNORE INTO deleted_sms(external_key) VALUES(?)",arrayOf<Any>(it)) }
                    db.delete("person_ledger","transaction_id=?",arrayOf(id.toString()))
                    db.execSQL("UPDATE bank_entries SET matched_transaction_id=NULL WHERE matched_transaction_id=?",arrayOf<Any>(id))
                    db.delete("transactions","id=?",arrayOf(id.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Import and snapshot reconciliation share a single SQLite transaction with the receiver. */
    fun acceptSms(p: ir.hesabyar.finance.sms.ParsedSms, snapshotOnly: Boolean = false): Boolean {
        val db=dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val deleted=db.rawQuery("SELECT 1 FROM deleted_sms WHERE external_key=?",arrayOf(p.externalKey)).use { it.moveToFirst() }
            if(deleted) return false
            val existing=accounts().firstOrNull { it.bankName==p.bankName && it.smsIdentity==p.accountIdentity && it.smsIdentity.isNotBlank() }
                ?: accounts().filter { it.bankName==p.bankName && it.cardLast4==p.accountLast4 && p.accountLast4.isNotBlank() && it.smsIdentity.isBlank() }.singleOrNull()
            if(existing?.excluded==true) return false
            val accountId=existing?.id ?: addAccount(Account(name=p.bankName + if(p.accountLast4.isBlank()) " • بدون شناسه" else " • " + p.accountLast4,bankName=p.bankName,cardLast4=p.accountLast4,smsIdentity=p.accountIdentity))
            if(existing!=null && existing.smsIdentity.isBlank()) db.execSQL("UPDATE accounts SET sms_identity=? WHERE id=?",arrayOf<Any>(p.accountIdentity,accountId))
            var changed=existing==null
            if(!snapshotOnly && p.amountRial!=null && p.type!=null) {
                val id=addTransaction(FinanceTransaction(amount=p.amountRial,type=p.type,occurredAt=p.timestamp,reason=p.reason,accountId=accountId,categoryId=null,personId=null,source=TransactionSource.SMS,externalKey=p.externalKey))
                changed=changed || id>0
            }
            p.balanceRial?.let { balance ->
                val anchor=db.rawQuery("SELECT balance_at FROM accounts WHERE id=?",arrayOf(accountId.toString())).use { it.moveToFirst();it.getLong(0) }
                if(p.timestamp>anchor) {
                    val later=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amount ELSE -amount END),0) FROM transactions WHERE account_id=? AND occurred_at>?",arrayOf(accountId.toString(),p.timestamp.toString())).use { it.moveToFirst(); it.getLong(0) }
                    db.execSQL("UPDATE accounts SET balance=?,balance_at=? WHERE id=?",arrayOf<Any>(Math.addExact(balance,later),p.timestamp,accountId))
                    changed=true
                }
            }
            db.setTransactionSuccessful()
            return changed
        } finally { db.endTransaction() }
    }

    private fun Cursor.toAccount() = Account(getLong(0),getString(1),getString(2),getString(3),getString(4),getLong(5),getString(6),getInt(7)!=0,getString(8))
    private fun Cursor.toPerson() = Person(getLong(0),getString(1),getString(2),getString(3),getString(4))
    private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
}
