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
    private fun shell(command:String):String {
        val script=java.io.File(context.getExternalFilesDir(null),"fixture-command.sh")
        script.writeText(command+" 2>&1\n")
        val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("sh ${script.absolutePath}")
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use{it.readText()}
    }
    @Test fun inboxImportHonorsInclusiveDatesAndLatestBalance() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission("ir.hesabyar.finance","android.permission.READ_SMS")
        shell("appops set com.android.shell WRITE_SMS allow")
        shell("appops set com.android.shell READ_SMS allow")
        val dates=listOf(11001L,11002L,11003L,11004L)
        try {
            dates.forEachIndexed { i,date ->
                val output=shell("content insert --uri content://sms/inbox --bind address:s:200033 --bind body:s:برداشت100ریالحساب77778888مانده${1000-i*100}ریال --bind date:l:$date")
                assertFalse(output,output.contains("Error",true)||output.contains("Exception"))
            }
            val r=FinanceRepository(context)
            val result=ir.hesabyar.finance.sms.SmsImporter.import(context,r,11002,11003,false)
            assertEquals("Inbox rows: "+shell("content query --uri content://sms/inbox --projection date:body"),2,result.scanned);assertEquals(2,r.transactions().size)
            assertEquals(setOf(11002L,11003L),r.transactions().map{it.transaction.occurredAt}.toSet())
            assertEquals(800L,r.accounts().first{it.bankName.isNotBlank()}.balance)
            ir.hesabyar.finance.sms.SmsImporter.import(context,r,11001,11004,true)
            assertEquals(2,r.transactions().size);assertEquals(700L,r.accounts().first{it.bankName.isNotBlank()}.balance)
        } finally { dates.forEach{shell("content delete --uri content://sms --where date=$it")} }
    }
    @Test fun reimportAttachesLegacyUnassignedSms() {
        val r=FinanceRepository(context);val parsed=p(10,100,900)
        r.addTransaction(FinanceTransaction(amount=100,type=TransactionType.EXPENSE,occurredAt=10,reason="قدیمی",accountId=null,categoryId=null,personId=null,source=TransactionSource.SMS,externalKey=parsed.externalKey))
        r.acceptSms(parsed)
        assertEquals(1,r.transactions().size);assertNotNull(r.transactions().single().transaction.accountId)
        assertEquals(900L,r.accounts().first{it.bankName.isNotBlank()}.balance)
    }
    @Test fun editReversesOldAccountAndReplacesPeopleSplitsAtomically() {
        FinanceRepository(context).use { r ->
            val a=r.addAccount(Account(name="حساب اول",balance=1000));val b=r.addAccount(Account(name="حساب دوم",balance=2000))
            val p1=r.addPerson(Person(name="شخص اول"));val p2=r.addPerson(Person(name="شخص دوم"))
            val id=r.addTransaction(FinanceTransaction(amount=100,type=TransactionType.EXPENSE,occurredAt=10,reason="قبلی",accountId=a,categoryId=null,personId=null,source=TransactionSource.MANUAL),listOf(SplitDraft(p1,50,SplitRelation.OWES_ME)))
            val edited=r.transaction(id)!!.copy(amount=250,type=TransactionType.INCOME,occurredAt=20,reason="اصلاح‌شده",accountId=b,note="یادداشت جدید")
            val shares=listOf(SplitDraft(p2,100,SplitRelation.I_OWE))
            r.updateTransaction(edited,shares)
            assertEquals(1000L,r.accounts().first{it.id==a}.balance);assertEquals(2250L,r.accounts().first{it.id==b}.balance)
            assertEquals(0L,r.personBalances().first{it.person.id==p1}.net);assertEquals(-100L,r.personBalances().first{it.person.id==p2}.net)
            assertEquals("اصلاح‌شده",r.ledger(p2).single().note);assertEquals(20L,r.ledger(p2).single().occurredAt)
            assertEquals(edited,r.transaction(id));assertEquals(shares,r.transactionSplits(id))
            r.updateTransaction(edited,shares);assertEquals(2250L,r.accounts().first{it.id==b}.balance)
            try {r.updateTransaction(edited.copy(accountId=999999),shares);fail("missing account accepted")} catch(expected:IllegalArgumentException) { }
            assertEquals(2250L,r.accounts().first{it.id==b}.balance);assertEquals(edited,r.transaction(id))
            r.updateTransaction(edited.copy(accountId=null,personId=p1),emptyList())
            assertEquals(2000L,r.accounts().first{it.id==b}.balance);assertEquals(0L,r.personBalances().first{it.person.id==p2}.net)
            assertTrue(r.transactionSplits(id).isEmpty());assertEquals(p1,r.transaction(id)!!.personId)
            assertEquals(1,r.transactions().size)
        }
    }
    @Test fun editSmsKeepsSnapshotAndFingerprintAndSurvivesRescan() {
        FinanceRepository(context).use { r ->
            r.acceptSms(p(100,100,900))
            val anchored=r.transactions().single().transaction
            r.updateTransaction(anchored.copy(amount=200),emptyList())
            assertEquals(900L,r.accounts().first{it.bankName.isNotBlank()}.balance)
            r.acceptSms(p(110,100));val future=r.transactions().first{it.transaction.occurredAt==110L}.transaction
            r.updateTransaction(future.copy(amount=150),emptyList())
            assertEquals(750L,r.accounts().first{it.bankName.isNotBlank()}.balance)
            assertEquals(future.externalKey,r.transaction(future.id)!!.externalKey)
            r.acceptSms(p(110,100));assertEquals(150L,r.transaction(future.id)!!.amount)
            r.updateTransaction(r.transaction(future.id)!!.copy(accountId=null),emptyList())
            r.acceptSms(p(110,100));assertNull(r.transaction(future.id)!!.accountId)
            assertEquals(900L,r.accounts().first{it.bankName.isNotBlank()}.balance)
            assertEquals(2,r.transactions().size)
        }
    }
    @Test fun sendersAreNotAccountsAndTwoBankAccountsRemainDistinct() {
        FinanceRepository(context).use { r ->
            fun sms(sender:String,id:String,time:Long,balance:Long)=SmsParser.parse(sender,"برداشت:100 ریال\nحساب:$id\nمانده:$balance ریال",time)!!
            r.acceptSms(sms("B.Pasargad","123.456.789",100,900))
            r.acceptSms(sms("BankPasargad","123456789",200,800))
            assertEquals(1,r.accounts().count{it.bankName=="بانک پاسارگاد"})
            r.acceptSms(sms("B.Pasargad","987654321",300,1200))
            assertEquals(2,r.accounts().count{it.bankName=="بانک پاسارگاد"})
            assertEquals(2000L,r.dashboard().totalBalance)
            r.acceptSms(SmsParser.parse("B.Pasargad","برداشت:100 ریال\nمانده:700 ریال",400)!!)
            r.acceptSms(SmsParser.parse("BankPasargad","برداشت:100 ریال\nمانده:600 ریال",500)!!)
            val unknown=r.accounts().filter{it.smsIdentity=="unidentified"}
            assertEquals(1,unknown.size);assertEquals(600L,unknown.single().balance)
            assertEquals(2000L,r.dashboard().totalBalance)
            val unknownTx=r.transactions().filter{it.transaction.occurredAt>=400}
            assertTrue(unknownTx.all{it.transaction.accountId==unknown.single().id})
        }
    }
    @Test fun accountCardAliasesAndSameSuffixDoNotMergeUnrelatedAccounts() {
        FinanceRepository(context).use { r ->
            r.acceptSms(SmsParser.parse("TejaratBank","برداشت:100 ریال\nحساب:11111234\nکارت:6037990012341234\nمانده:900 ریال",100)!!)
            r.acceptSms(SmsParser.parse("BankTejarat","برداشت:100 ریال\nکارت:6037990012341234\nمانده:800 ریال",200)!!)
            assertEquals(1,r.accounts().count{it.bankName=="بانک تجارت"})
            r.acceptSms(SmsParser.parse("TejaratBank","برداشت:100 ریال\nحساب:22221234\nمانده:2000 ریال",300)!!)
            assertEquals(2,r.accounts().count{it.bankName=="بانک تجارت"})
            r.acceptSms(SmsParser.parse("TejaratBank","برداشت:100 ریال\nکارت:603799**1234\nمانده:700 ریال",400)!!)
            assertEquals(2,r.accounts().count{it.bankName=="بانک تجارت"})
            assertEquals(2700L,r.dashboard().totalBalance)
        }
    }
    @Test fun bluRoundUpdatesAvailableBalanceButNotExpenseBySavingsPortion() {
        FinanceRepository(context).use { r ->
            val a=SmsParser.parse("BluBank","بلو\nواریز پول\nمشتری عزیز، 3,130,000ریال به حساب شما نشست.\nموجودی:10,720,458 ریال",100)!!
            val b=SmsParser.parse("987654321","بلو\nبرداشت رُند\nمشتری عزیز، 640,000 ریال از حساب شما پرید.\nو 10,000 ریال هم رند شد.\nموجودی:7,642,736 ریال",200)!!
            r.acceptSms(a);r.acceptSms(b);r.acceptSms(b)
            assertEquals(1,r.accounts().count{it.bankName=="بلوبانک سامان"})
            assertEquals(7642736L,r.dashboard().totalBalance)
            assertEquals(640000L,r.report(0,300).expense)
            assertEquals(3130000L,r.report(0,300).income)
            val tx=r.transactions().first{it.transaction.occurredAt==200L}.transaction
            assertEquals(650000L,tx.amount);assertEquals(10000L,tx.internalAmount)
            r.updateTransaction(tx.copy(note="ویرایش شرح"),emptyList())
            assertEquals(640000L,r.report(0,300).expense);assertEquals(10000L,r.transaction(tx.id)!!.internalAmount)
        }
    }
    @Test fun rescanCorrectsOldSamanClassificationForBluWithoutDoubleBalance() {
        val parsed=SmsParser.parse("20000","بلو\nبرداشت رند\nمشتری عزیز،640,000 ریال از حساب شما پرید.\nو 10,000 ریال هم رند شد.\nموجودی:7,642,736 ریال",200)!!
        FinanceRepository(context).use { r ->
            val oldAccount=r.addAccount(Account(name="بانک سامان • بدون شناسه حساب",bankName="بانک سامان",smsIdentity="unidentified"))
            val oldTx=r.addTransaction(FinanceTransaction(amount=640000,type=TransactionType.EXPENSE,occurredAt=200,reason="قدیمی",accountId=oldAccount,categoryId=null,personId=null,source=TransactionSource.SMS,externalKey=parsed.externalKey))
            FinanceDb(context).use { helper -> helper.writableDatabase.execSQL("UPDATE accounts SET balance=7642736,balance_at=200 WHERE id=?",arrayOf<Any>(oldAccount)) }
            r.acceptSms(parsed,true)
            assertEquals(1,r.transactions().size);assertEquals(oldTx,r.transactions().single().transaction.id)
            assertEquals(7642736L,r.dashboard().totalBalance);assertEquals(640000L,r.report(0,300).expense)
            assertEquals(1,r.accounts().count{it.bankName.isNotBlank()});assertEquals("بلوبانک سامان",r.accounts().first{it.bankName.isNotBlank()}.bankName)
        }
    }
    private fun removeV3(db:SQLiteDatabase) {
        db.execSQL("DROP TABLE account_aliases")
        db.execSQL("ALTER TABLE transactions RENAME TO v3_transactions")
        db.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,amount INTEGER NOT NULL,type TEXT NOT NULL,occurred_at INTEGER NOT NULL,reason TEXT NOT NULL,account_id INTEGER,category_id INTEGER,person_id INTEGER,source TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',external_key TEXT)")
        db.execSQL("INSERT INTO transactions(id,amount,type,occurred_at,reason,account_id,category_id,person_id,source,note,external_key) SELECT id,amount,type,occurred_at,reason,account_id,category_id,person_id,source,note,external_key FROM v3_transactions")
        db.execSQL("DROP TABLE v3_transactions")
        db.execSQL("CREATE UNIQUE INDEX tx_external_key_idx ON transactions(external_key) WHERE external_key IS NOT NULL AND external_key<>''")
    }
    @Test fun migrationConsolidatesSenderBucketsUsingLatestSnapshotNotSum() {
        val helper=FinanceDb(context);val db=helper.writableDatabase
        db.execSQL("INSERT INTO accounts(name,type,bank_name,sms_identity,balance,balance_at) VALUES('شماره قدیم','بانک','پاسارگاد','sender:1000900',850,100)")
        val a=db.rawQuery("SELECT MAX(id) FROM accounts",null).use{it.moveToFirst();it.getLong(0)}
        db.execSQL("INSERT INTO accounts(name,type,bank_name,sms_identity,balance,balance_at) VALUES('شماره جدید','بانک','ویپاد','sender:b.pasargad',800,200)")
        val b=db.rawQuery("SELECT MAX(id) FROM accounts",null).use{it.moveToFirst();it.getLong(0)}
        db.execSQL("INSERT INTO transactions(amount,type,occurred_at,reason,account_id,source) VALUES(50,'EXPENSE',300,'بعد از مانده',?,'MANUAL')",arrayOf<Any>(a))
        db.execSQL("INSERT INTO installments(title,amount,due_at,account_id) VALUES('قسط مرتبط',20,1000,?)",arrayOf<Any>(b))
        removeV3(db);db.version=2;helper.close()
        FinanceRepository(context).use { r ->
            val bank=r.accounts().filter{it.bankName=="بانک پاسارگاد"};assertEquals(1,bank.size)
            assertEquals(750L,bank.single().balance);assertEquals("unidentified",bank.single().smsIdentity)
            assertEquals(bank.single().id,r.transactions().single().transaction.accountId)
            assertEquals(bank.single().id,r.installments().single().accountId)
        }
    }
    @Test fun migrationConvertsTomanExactlyOnce() {
        // Construct the actual v1 schema by creating current tables and rebuilding only accounts.
        val helper=FinanceDb(context);val db=helper.writableDatabase
        removeV3(db)
        db.execSQL("DROP TABLE deleted_sms")
        db.execSQL("ALTER TABLE accounts RENAME TO old_accounts")
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,type TEXT NOT NULL,bank_name TEXT NOT NULL DEFAULT '',card_last4 TEXT NOT NULL DEFAULT '',balance INTEGER NOT NULL DEFAULT 0,group_name TEXT NOT NULL DEFAULT 'شخصی')")
        db.execSQL("INSERT INTO accounts(name,type,balance) VALUES('قدیمی','بانک',123)")
        db.execSQL("DROP TABLE old_accounts")
        db.execSQL("INSERT INTO person_ledger(person_id,amount_signed,occurred_at,note) VALUES(1,42,1,'test')")
        db.version=1;helper.close()
        val r=FinanceRepository(context);assertEquals(1230L,r.accounts().single().balance)
        FinanceDb(context).readableDatabase.use { migrated -> assertEquals(3,migrated.version);migrated.rawQuery("SELECT amount_signed FROM person_ledger",null).use{it.moveToFirst();assertEquals(420L,it.getLong(0))} }
        assertEquals(1230L,r.accounts().single().balance)
    }
}
