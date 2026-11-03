package ir.hesabyar.finance.data

import android.database.sqlite.SQLiteDatabase
import ir.hesabyar.finance.sms.AccountIdentity
import ir.hesabyar.finance.sms.BankRegistry

/** Repair only proven duplicates: same full identifier, or legacy sender-based unidentified buckets. */
object SmsAccountReconciler {
    private data class Row(val id:Long,val bank:String,val identity:String,val balance:Long,val anchor:Long,val excluded:Boolean)
    fun migrate(db:SQLiteDatabase) {
        val rows=db.rawQuery("SELECT id,bank_name,sms_identity,balance,balance_at,excluded FROM accounts",null).use{c->buildList{
            while(c.moveToNext()) add(Row(c.getLong(0),BankRegistry.canonicalName(c.getString(1)),c.getString(2),c.getLong(3),c.getLong(4),c.getInt(5)!=0))
        }}
        rows.forEach { r -> db.execSQL("UPDATE accounts SET bank_name=? WHERE id=?",arrayOf<Any>(r.bank,r.id)) }
        rows.filter{it.bank.isNotBlank() && it.identity.isNotBlank()}.groupBy { it.bank to AccountIdentity.normalize(it.identity) }.forEach { (key,group) ->
            val target=group.minBy{it.id}.id
            db.execSQL("UPDATE accounts SET sms_identity=? WHERE id=?",arrayOf<Any>(key.second,target))
            group.filter{it.id!=target}.forEach{merge(db,it.id,target)}
            if(key.second!=AccountIdentity.UNIDENTIFIED) db.execSQL("INSERT OR IGNORE INTO account_aliases(bank_name,identity,account_id) VALUES(?,?,?)",arrayOf<Any>(key.first,key.second,target))
            else db.execSQL("UPDATE accounts SET name=?,card_last4='' WHERE id=?",arrayOf<Any>(key.first+" • بدون شناسه حساب",target))
        }
    }
    /** Latest known bank snapshot wins. Add only transactions after it, never sum duplicate snapshots. */
    fun merge(db:SQLiteDatabase,source:Long,target:Long) {
        fun row(id:Long):Row=db.rawQuery("SELECT id,bank_name,sms_identity,balance,balance_at,excluded FROM accounts WHERE id=?",arrayOf(id.toString())).use{c->check(c.moveToFirst());Row(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4),c.getInt(5)!=0)}
        val a=row(source);val b=row(target);require(a.bank==b.bank)
        fun later(id:Long,at:Long):Long=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amount ELSE -amount END),0) FROM transactions WHERE account_id=? AND occurred_at>?",arrayOf(id.toString(),at.toString())).use{it.moveToFirst();it.getLong(0)}
        val newest=listOf(a,b).maxWith(compareBy<Row>{it.anchor}.thenBy{it.id})
        val mergedBalance=if(newest.anchor==0L) Math.addExact(a.balance,b.balance) else {
            val baseline=Math.subtractExact(newest.balance,later(newest.id,newest.anchor))
            Math.addExact(baseline,Math.addExact(later(a.id,newest.anchor),later(b.id,newest.anchor)))
        }
        db.execSQL("UPDATE transactions SET account_id=? WHERE account_id=?",arrayOf<Any>(target,source))
        db.execSQL("UPDATE installments SET account_id=? WHERE account_id=?",arrayOf<Any>(target,source))
        db.execSQL("UPDATE account_aliases SET account_id=? WHERE account_id=?",arrayOf<Any>(target,source))
        db.execSQL("UPDATE accounts SET balance=?,balance_at=?,excluded=? WHERE id=?",arrayOf<Any>(mergedBalance,newest.anchor,if(a.excluded||b.excluded)1 else 0,target))
        db.delete("accounts","id=?",arrayOf(source.toString()))
    }
}
