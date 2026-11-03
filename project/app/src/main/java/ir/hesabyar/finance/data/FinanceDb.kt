package ir.hesabyar.finance.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FinanceDb(context: Context) : SQLiteOpenHelper(context, "hesabyar.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE accounts(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                bank_name TEXT NOT NULL DEFAULT '',
                card_last4 TEXT NOT NULL DEFAULT '',
                balance INTEGER NOT NULL DEFAULT 0,
                group_name TEXT NOT NULL DEFAULT 'شخصی'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE people(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL DEFAULT '',
                group_name TEXT NOT NULL DEFAULT 'دوستان',
                note TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE categories(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                icon TEXT NOT NULL DEFAULT '•'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE transactions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount INTEGER NOT NULL,
                type TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                reason TEXT NOT NULL,
                account_id INTEGER,
                category_id INTEGER,
                person_id INTEGER,
                source TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                external_key TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX tx_date_idx ON transactions(occurred_at)")
        db.execSQL("CREATE UNIQUE INDEX tx_external_key_idx ON transactions(external_key) WHERE external_key IS NOT NULL AND external_key <> ''")
        db.execSQL("CREATE INDEX tx_account_idx ON transactions(account_id)")
        db.execSQL("CREATE INDEX tx_person_idx ON transactions(person_id)")

        db.execSQL("""
            CREATE TABLE person_ledger(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                person_id INTEGER NOT NULL,
                amount_signed INTEGER NOT NULL,
                occurred_at INTEGER NOT NULL,
                note TEXT NOT NULL,
                transaction_id INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE installments(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                amount INTEGER NOT NULL,
                due_at INTEGER NOT NULL,
                account_id INTEGER,
                person_id INTEGER,
                repeat_unit TEXT NOT NULL DEFAULT 'ماه',
                repeat_every INTEGER NOT NULL DEFAULT 1,
                reminder_days_before INTEGER NOT NULL DEFAULT 1,
                status TEXT NOT NULL DEFAULT 'OPEN'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE import_batches(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                imported_at INTEGER NOT NULL,
                total_rows INTEGER NOT NULL,
                matched_rows INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE bank_entries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                batch_id INTEGER NOT NULL,
                occurred_at INTEGER NOT NULL,
                amount INTEGER NOT NULL,
                type TEXT NOT NULL,
                description TEXT NOT NULL,
                balance_after INTEGER,
                matched_transaction_id INTEGER,
                fingerprint TEXT NOT NULL UNIQUE
            )
        """.trimIndent())

        upgradeV2(db, false)
        upgradeV3(db)
        seed(db)
    }

    private fun seed(db: SQLiteDatabase) {
        db.execSQL("INSERT INTO accounts(name,type,balance,group_name) VALUES('کیف پول','نقدی',0,'شخصی')")
        val expense = listOf("خوراک", "رفت‌وآمد", "خرید", "قبوض", "تفریح", "سلامت", "آموزش", "خانه", "سایر")
        val income = listOf("حقوق", "فروش", "هدیه", "بازگشت وجه", "سرمایه‌گذاری", "سایر درآمدها")
        expense.forEach { db.execSQL("INSERT INTO categories(name,type,icon) VALUES(?, 'EXPENSE', '−')", arrayOf(it)) }
        income.forEach { db.execSQL("INSERT INTO categories(name,type,icon) VALUES(?, 'INCOME', '+')", arrayOf(it)) }
    }

    private fun upgradeV2(db: SQLiteDatabase, convert: Boolean) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE accounts ADD COLUMN sms_identity TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE accounts ADD COLUMN balance_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE deleted_sms(external_key TEXT PRIMARY KEY)")
        if (convert) {
            // v1 stores toman. Convert once, atomically in SQLiteOpenHelper's upgrade transaction.
            listOf("accounts" to "balance", "transactions" to "amount", "person_ledger" to "amount_signed", "installments" to "amount", "bank_entries" to "amount", "bank_entries" to "balance_after").forEach { (table, col) ->
                db.execSQL("UPDATE $table SET $col=$col*10")
            }
        }
    }
    private fun upgradeV3(db:SQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN internal_amount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE account_aliases(bank_name TEXT NOT NULL,identity TEXT NOT NULL,account_id INTEGER NOT NULL,PRIMARY KEY(bank_name,identity))")
        db.execSQL("CREATE INDEX account_alias_target ON account_aliases(account_id)")
        SmsAccountReconciler.migrate(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) upgradeV2(db, true)
        if (oldVersion < 3) upgradeV3(db)
    }
}
