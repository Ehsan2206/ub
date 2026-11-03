package ir.hesabyar.finance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteDatabase
import ir.hesabyar.finance.data.*
import ir.hesabyar.finance.sms.SmsParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun reset() {context.deleteDatabase("hesabyar.db")}
    private fun p(time:Long,amount:Long=100,balance:Long?=null)=SmsParser.parse("200033","برداشت:$amount ریال\nحساب:12345678"+(balance?.let{"\nمانده:$it ریال"}?:""),time)!!
    @Test fun snapshotsDuplicateDeletionAndExclusion() {
        val r=FinanceRepository(context)
        assertTrue(r.acceptSms(p(100,100,900)))
        assertFalse(r.acceptSms(p(100,100,900)))
        assertEquals(900L,r.accounts().first{it.bankName.isNotBlank()}.balance)
        r.acceptSms(p(90,50,1000));assertEquals(900L,r.accounts().first{it.bankName.isNotBlank()}.balance)
        r.acceptSms(p(110,100));assertEquals(800L,r.accounts().first{it.bankName.isNotBlank()}.balance)
        val tx=r.transactions().first{it.transaction.occurredAt==110L}.transaction
        r.deleteTransaction(tx.id);assertEquals(900L,r.accounts().first{it.bankName.isNotBlank()}.balance)
        assertFalse(r.acceptSms(p(110,100)))
        val a=r.accounts().first{it.bankName.isNotBlank()};r.setAccountExcluded(a.id,true)
        assertEquals(0L,r.dashboard().totalBalance);assertEquals(0L,r.report(0,10000).expense)
        assertFalse(r.acceptSms(p(120,10,890)))
        r.setAccountExcluded(a.id,false);assertEquals(900L,r.dashboard().totalBalance)
    }
    @Test fun snapshotOnlyAndLaterTransactions() {
        val r=FinanceRepository(context)
        r.acceptSms(p(100,100,900),true);assertTrue(r.transactions().isEmpty())
        r.acceptSms(p(120,50));assertEquals(850L,r.accounts().first{it.bankName.isNotBlank()}.balance)
        r.acceptSms(p(110,20,880),true);assertEquals(830L,r.accounts().first{it.bankName.isNotBlank()}.balance)
        r.acceptSms(p(100,100,900));assertEquals(830L,r.accounts().first{it.bankName.isNotBlank()}.balance)
    }
    @Test fun peopleLedgerAndLinkedDeletion() {
        val r=FinanceRepository(context);val id=r.addPerson(Person(name="شخص آزمایشی"))
        r.addLedger(id,1000,10,"طلب");r.addLedger(id,-250,11,"تسویه")
        assertEquals(750L,r.personBalances().single().net)
        val tx=r.addTransaction(FinanceTransaction(amount=100,type=TransactionType.EXPENSE,occurredAt=20,reason="سهم",accountId=null,categoryId=null,personId=null,source=TransactionSource.MANUAL),listOf(SplitDraft(id,100,SplitRelation.OWES_ME)))
        assertEquals(850L,r.personBalances().single().net);r.deleteTransaction(tx);assertEquals(750L,r.personBalances().single().net)
        r.deleteLedger(r.ledger(id).first().id);assertEquals(1000L,r.personBalances().single().net)
    }
    @Test fun migrationConvertsTomanExactlyOnce() {
        // Construct the actual v1 schema by creating current tables and rebuilding only accounts.
        val helper=FinanceDb(context);val db=helper.writableDatabase
        db.execSQL("DROP TABLE deleted_sms")
        db.execSQL("ALTER TABLE accounts RENAME TO old_accounts")
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,type TEXT NOT NULL,bank_name TEXT NOT NULL DEFAULT '',card_last4 TEXT NOT NULL DEFAULT '',balance INTEGER NOT NULL DEFAULT 0,group_name TEXT NOT NULL DEFAULT 'شخصی')")
        db.execSQL("INSERT INTO accounts(name,type,balance) VALUES('قدیمی','بانک',123)")
        db.execSQL("DROP TABLE old_accounts")
        db.execSQL("INSERT INTO person_ledger(person_id,amount_signed,occurred_at,note) VALUES(1,42,1,'test')")
        db.version=1;helper.close()
        val r=FinanceRepository(context);assertEquals(1230L,r.accounts().single().balance)
        FinanceDb(context).readableDatabase.use { migrated -> assertEquals(2,migrated.version);migrated.rawQuery("SELECT amount_signed FROM person_ledger",null).use{it.moveToFirst();assertEquals(420L,it.getLong(0))} }
        assertEquals(1230L,r.accounts().single().balance)
    }
}
