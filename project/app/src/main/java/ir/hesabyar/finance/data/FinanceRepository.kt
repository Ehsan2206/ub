package ir.hesabyar.finance.data

import ir.hesabyar.finance.util.DateUtil
import ir.hesabyar.finance.sms.AccountIdentity
import ir.hesabyar.finance.sms.BankRegistry

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
            put("name", account.name); put("type", account.type); put("bank_name", if(account.bankName.isBlank()) "" else BankRegistry.canonicalName(account.bankName))
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
        require(tx.internalAmount in 0..tx.amount && (tx.internalAmount==0L || tx.type==TransactionType.EXPENSE))
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put("amount", tx.amount); put("internal_amount",tx.internalAmount); put("type", tx.type.name); put("occurred_at", tx.occurredAt)
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

    fun transaction(id: Long): FinanceTransaction? = dbHelper.readableDatabase.rawQuery(
        "SELECT id,amount,type,occurred_at,reason,account_id,category_id,person_id,source,note,external_key,internal_amount FROM transactions WHERE id=?", arrayOf(id.toString())
    ).use { c -> if(c.moveToFirst()) FinanceTransaction(c.getLong(0),c.getLong(1),TransactionType.valueOf(c.getString(2)),c.getLong(3),c.getString(4),c.longOrNull(5),c.longOrNull(6),c.longOrNull(7),TransactionSource.valueOf(c.getString(8)),c.getString(9),c.getString(10).orEmpty(),c.getLong(11)) else null }

    fun transactionSplits(id: Long): List<SplitDraft> = dbHelper.readableDatabase.rawQuery(
        "SELECT person_id,amount_signed FROM person_ledger WHERE transaction_id=? ORDER BY id", arrayOf(id.toString())
    ).use { c -> buildList { while(c.moveToNext()) { val signed=c.getLong(1); add(SplitDraft(c.getLong(0),kotlin.math.abs(signed),if(signed>=0) SplitRelation.OWES_ME else SplitRelation.I_OWE)) } } }

    fun updateTransaction(tx: FinanceTransaction, splits: List<SplitDraft>) {
        require(tx.amount>0) { "مبلغ باید بیشتر از صفر باشد" }
        require(tx.internalAmount in 0..tx.amount && (tx.internalAmount==0L || tx.type==TransactionType.EXPENSE)) { "مبلغ انتقال داخلی معتبر نیست" }
        require(splits.all { it.amount>0 } && splits.map { it.personId }.distinct().size==splits.size) { "سهم اشخاص معتبر نیست" }
        val db=dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val old=transaction(tx.id) ?: error("تراکنش پیدا نشد")
            fun exists(table:String,id:Long)=db.rawQuery("SELECT 1 FROM $table WHERE id=?",arrayOf(id.toString())).use { it.moveToFirst() }
            require(tx.accountId==null || exists("accounts",tx.accountId)) { "حساب انتخاب‌شده پیدا نشد" }
            require(tx.personId==null || exists("people",tx.personId)) { "شخص انتخاب‌شده پیدا نشد" }
            require(splits.all { exists("people",it.personId) }) { "شخص انتخاب‌شده پیدا نشد" }
            require(tx.categoryId==null || db.rawQuery("SELECT 1 FROM categories WHERE id=? AND type=?",arrayOf(tx.categoryId.toString(),tx.type.name)).use { it.moveToFirst() }) { "دسته‌بندی با نوع تراکنش هماهنگ نیست" }
            // Reverse the old contribution and apply the new one against each account's own snapshot.
            old.accountId?.let { id ->
                val reverse=if(old.type==TransactionType.INCOME) -old.amount else old.amount
                db.execSQL("UPDATE accounts SET balance=balance+? WHERE id=? AND balance_at<?",arrayOf<Any>(reverse,id,old.occurredAt))
            }
            val cv=ContentValues().apply {
                put("amount",tx.amount);put("internal_amount",tx.internalAmount);put("type",tx.type.name);put("occurred_at",tx.occurredAt);put("reason",tx.reason);put("note",tx.note)
                if(tx.accountId==null) putNull("account_id") else put("account_id",tx.accountId)
                if(tx.categoryId==null) putNull("category_id") else put("category_id",tx.categoryId)
                if(tx.personId==null || splits.isNotEmpty()) putNull("person_id") else put("person_id",tx.personId)
                // Keep the original fingerprint for deduplication; user edits must survive future SMS scans.
                put("source",if(old.source==TransactionSource.SMS) TransactionSource.MANUAL.name else old.source.name)
            }
            check(db.update("transactions",cv,"id=?",arrayOf(tx.id.toString()))==1)
            tx.accountId?.let { id ->
                val delta=if(tx.type==TransactionType.INCOME) tx.amount else -tx.amount
                db.execSQL("UPDATE accounts SET balance=balance+? WHERE id=? AND balance_at<?",arrayOf<Any>(delta,id,tx.occurredAt))
            }
            db.delete("person_ledger","transaction_id=?",arrayOf(tx.id.toString()))
            splits.forEach { split ->
                val lv=ContentValues().apply {
                    put("person_id",split.personId);put("amount_signed",if(split.relation==SplitRelation.OWES_ME) split.amount else -split.amount)
                    put("occurred_at",tx.occurredAt);put("note",tx.reason.ifBlank { "حساب اشخاص" });put("transaction_id",tx.id)
                }
                db.insertOrThrow("person_ledger",null,lv)
            }
            db.execSQL("UPDATE bank_entries SET matched_transaction_id=NULL WHERE matched_transaction_id=?",arrayOf<Any>(tx.id))
            db.setTransactionSuccessful()
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
                   COALESCE(a.name,''),COALESCE(p.name,(SELECT GROUP_CONCAT(pp.name,'، ') FROM person_ledger pl JOIN people pp ON pp.id=pl.person_id WHERE pl.transaction_id=t.id),''),COALESCE(c.name,''),t.internal_amount
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
                        source = TransactionSource.valueOf(c.getString(8)), note = c.getString(9), externalKey = c.getString(10).orEmpty(), internalAmount = c.getLong(14)
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
            "SELECT COALESCE(SUM(amount-internal_amount),0) FROM transactions WHERE (account_id IS NULL OR account_id IN (SELECT id FROM accounts WHERE excluded=0)) AND type=? AND occurred_at BETWEEN ? AND ?",
            arrayOf(type.name, from.toString(), to.toString())
        ).use { c -> c.moveToFirst(); c.getLong(0) }
        val totalBalance = dbHelper.readableDatabase.rawQuery("SELECT COALESCE(SUM(balance),0) FROM accounts a WHERE excluded=0 AND NOT(a.sms_identity='unidentified' AND EXISTS(SELECT 1 FROM accounts b WHERE b.bank_name=a.bank_name AND b.id<>a.id AND b.sms_identity<>'unidentified'))", null).use { c -> c.moveToFirst(); c.getLong(0) }
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
        dbHelper.readableDatabase.rawQuery("SELECT type,COALESCE(SUM(amount-internal_amount),0) FROM transactions t WHERE $where GROUP BY type", args.toTypedArray()).use { c ->
            while (c.moveToNext()) if (c.getString(0)=="INCOME") income=c.getLong(1) else expense=c.getLong(1)
        }
        val cats = dbHelper.readableDatabase.rawQuery(
            "SELECT COALESCE(c.name,'بدون دسته'),SUM(t.amount-t.internal_amount) FROM transactions t LEFT JOIN categories c ON c.id=t.category_id WHERE $where AND t.type='EXPENSE' GROUP BY c.name ORDER BY SUM(t.amount-t.internal_amount) DESC LIMIT 8",
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
            val prior=transactionByExternalKey(p.externalKey)?.let(::transaction)
            // User edits and explicitly excluded accounts must survive every rescan, including reclassification.
            if(prior!=null && prior.source!=TransactionSource.SMS) return false
            val priorAccount=prior?.accountId?.let { id -> accounts().firstOrNull{it.id==id} }
            if(priorAccount?.excluded==true) return false
            val bank=BankRegistry.canonicalName(p.bankName)
            val ids=(p.accountIdentities.ifEmpty{listOf(p.accountIdentity)}).map(AccountIdentity::normalize).filter{it!=AccountIdentity.UNIDENTIFIED}.distinct()
            val bankAccounts=accounts().filter{it.bankName==bank}
            fun mapped(identity:String):List<Long> = db.rawQuery("SELECT account_id FROM account_aliases WHERE bank_name=? AND identity=?",arrayOf(bank,identity)).use { c ->buildList{while(c.moveToNext())add(c.getLong(0))} }
            val primaryExact=ids.firstOrNull()?.takeIf(AccountIdentity::isFull)?.let(::mapped).orEmpty()
            val strongMapped=if(primaryExact.isNotEmpty())primaryExact else ids.filter(AccountIdentity::isFull).flatMap(::mapped).distinct()
            val aliasMatches=db.rawQuery("SELECT identity,account_id FROM account_aliases WHERE bank_name=?",arrayOf(bank)).use { c -> buildSet { while(c.moveToNext()) if(ids.any{AccountIdentity.matches(it,c.getString(0))})add(c.getLong(1)) } }
            val primaryFull=ids.firstOrNull()?.takeIf(AccountIdentity::isFull)
            val candidates=bankAccounts.filter { a ->
                val existingId=AccountIdentity.normalize(a.smsIdentity)
                // A suffix match cannot override a different complete account identifier.
                val conflictingFull=primaryFull!=null && AccountIdentity.isFull(existingId) && primaryFull!=existingId
                !conflictingFull && (a.id in aliasMatches || ids.any { AccountIdentity.matches(it,existingId) })
            }
            val suffix=if(ids.isNotEmpty() && strongMapped.isEmpty() && candidates.isEmpty()) bankAccounts.filter { it.smsIdentity.isBlank() && it.cardLast4==p.accountLast4 && p.accountLast4.isNotBlank() }.singleOrNull() else null
            val matched=when {strongMapped.size==1 -> bankAccounts.firstOrNull{it.id==strongMapped.single()}; strongMapped.size>1 -> null; candidates.size==1 -> candidates.single();else -> suffix}
            val ambiguous=strongMapped.size>1 || (strongMapped.isEmpty() && candidates.size>1)
            val identity=if(ids.isEmpty() || ambiguous) AccountIdentity.UNIDENTIFIED else ids.first()
            val existing=matched ?: bankAccounts.firstOrNull{AccountIdentity.normalize(it.smsIdentity)==identity && it.smsIdentity.isNotBlank()}
            if(existing?.excluded==true) return false
            val accountId=existing?.id ?: addAccount(Account(name=bank+if(identity==AccountIdentity.UNIDENTIFIED)" • بدون شناسه حساب" else " • "+p.accountLast4,bankName=bank,cardLast4=if(identity==AccountIdentity.UNIDENTIFIED)"" else p.accountLast4,smsIdentity=identity))
            if(existing!=null && (existing.smsIdentity.isBlank() || (AccountIdentity.isFull(identity) && !AccountIdentity.isFull(existing.smsIdentity)))) {
                db.execSQL("UPDATE accounts SET sms_identity=?,card_last4=? WHERE id=?",arrayOf<Any>(identity,p.accountLast4,accountId))
            }
            if(!ambiguous && identity!=AccountIdentity.UNIDENTIFIED) ids.forEach { alias ->
                // A previously unique masked suffix can become ambiguous when another account is discovered.
                val matchesOther=if(AccountIdentity.isFull(alias))false else bankAccounts.any { it.id!=accountId && AccountIdentity.matches(alias,AccountIdentity.normalize(it.smsIdentity)) }
                if(!matchesOther) db.execSQL("INSERT OR IGNORE INTO account_aliases(bank_name,identity,account_id) VALUES(?,?,?)",arrayOf<Any>(bank,alias,accountId))
            }
            var changed=existing==null
            if(p.amountRial!=null && p.type!=null) {
                if(prior==null && !snapshotOnly) {
                    val id=addTransaction(FinanceTransaction(amount=p.amountRial,type=p.type,occurredAt=p.timestamp,reason=p.reason,accountId=accountId,categoryId=null,personId=null,source=TransactionSource.SMS,externalKey=p.externalKey,internalAmount=p.internalAmount,note=p.details))
                    changed=changed || id>0
                } else if(prior!=null && (prior.accountId!=accountId || prior.amount!=p.amountRial || prior.type!=p.type || prior.internalAmount!=p.internalAmount)) {
                    prior.accountId?.let { id ->
                        val reverse=if(prior.type==TransactionType.INCOME)-prior.amount else prior.amount
                        db.execSQL("UPDATE accounts SET balance=balance+? WHERE id=? AND balance_at<?",arrayOf<Any>(reverse,id,prior.occurredAt))
                    }
                    val values=ContentValues().apply {
                        put("account_id",accountId);put("amount",p.amountRial);put("type",p.type.name);put("reason",p.reason)
                        put("internal_amount",p.internalAmount);put("note",p.details.ifBlank{prior.note})
                    }
                    db.update("transactions",values,"id=?",arrayOf(prior.id.toString()))
                    val delta=if(p.type==TransactionType.INCOME)p.amountRial else -p.amountRial
                    db.execSQL("UPDATE accounts SET balance=balance+? WHERE id=? AND balance_at<?",arrayOf<Any>(delta,accountId,p.timestamp))
                    db.execSQL("UPDATE bank_entries SET matched_transaction_id=NULL WHERE matched_transaction_id=?",arrayOf<Any>(prior.id))
                    // Remove only empty, automatically generated unidentified buckets after bank reclassification.
                    if(priorAccount!=null && priorAccount.id!=accountId && priorAccount.smsIdentity==AccountIdentity.UNIDENTIFIED && priorAccount.name.endsWith("بدون شناسه حساب")) {
                        val used=db.rawQuery("SELECT (SELECT COUNT(*) FROM transactions WHERE account_id=?)+(SELECT COUNT(*) FROM installments WHERE account_id=?)",arrayOf(priorAccount.id.toString(),priorAccount.id.toString())).use{it.moveToFirst();it.getLong(0)>0}
                        if(!used) {db.delete("account_aliases","account_id=?",arrayOf(priorAccount.id.toString()));db.delete("accounts","id=?",arrayOf(priorAccount.id.toString()))}
                    }
                    changed=true
                }
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
