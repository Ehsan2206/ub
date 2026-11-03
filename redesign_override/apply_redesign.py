from pathlib import Path
import shutil, re

root = Path("project")
over = Path("redesign_override")
for r in ["app/src/main/java/ir/hesabyar/finance/util/DateUtil.kt","app/src/main/java/ir/hesabyar/finance/ui/FinanceApp.kt","app/src/main/java/ir/hesabyar/finance/ui/theme/Theme.kt","app/src/main/res/drawable/ic_launcher_foreground.xml","app/src/main/res/values/colors.xml"]:
    s = over / r; d = root / r; d.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(s, d)

dbg = root / "app/src/debug/AndroidManifest.xml"
dbg.parent.mkdir(parents=True, exist_ok=True)
dbg.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><uses-permission android:name="android.permission.READ_SMS"/><uses-permission android:name="android.permission.RECEIVE_SMS"/></manifest>', encoding="utf-8")

sp = over / "app/src/main/java/ir/hesabyar/finance/sms/SmsParser.kt"
spt = sp.read_text(encoding="utf-8").replace("/ 10L", "* 1L").replace("/ 10", "* 1")
b_kt = """
val BANKS = "ملی:600060,100060,200060,300060,BMI,Melli,بام;ملت:200033,100033,300033,Mellat;صادرات:300060,100069,BSI,Saderat,سپینو;تجارت:200070,100070,Tejarat;سپه:300028,200028,Sepah,انصار,قوامین,کوثر,حکمت,مهر اقتصاد;کشاورزی:200090,Keshavarzi,BKI,AgriBank;مسکن:300014,200014,Maskan;پاسارگاد:200020,100020,Pasargad,BPI,WePod,ویپاد;سامان:20000,2000842,Saman,SB24;بلوبانک:200094,300094,Blu,BluBank,بلو بانک;پارسیان:200040,Parsian,PEB;اقتصاد نوین:200050,ENBank,Novin;کارآفرین:200080,Karafarin;سینا:200010,Sina;شهر:300086,Shahr;دی:200029,DayBank;آینده:200044,Ayandeh,Abank,آبانک;رفاه:300071,Refah;قرض‌الحسنه مهر ایران:300085,MehrIran,QMB,مهر ایران;قرض‌الحسنه رسالت:300018,Resalat,رسالت;گردشگری:200015,TourismBank,Tobank,توبانک;خاورمیانه:200058,MEBank,Bankino,بانکینو;سرمایه:200048,Sarmayeh;ایران زمین:300069,IranZamin;پست بانک:200088,PostBank;توسعه تعاون:300027,TTBank;توسعه صادرات:200025,EDBI;صنعت و معدن:200011,BIM;ملل:300073,Melal;نور:300079,Noor;صندوق قرض‌الحسنه:Qarz,Sandogh,قرض الحسنه,قرض‌الحسنه".split(";")
fun detectIranBank(sender: String, body: String): String {
    val s = sender.trim().removePrefix("+").removePrefix("00")
    for (r in BANKS) {
        val p = r.split(":"); val n = p[0]; val ks = p[1].split(",")
        if (ks.any { k -> body.contains(k, true) || body.contains(n, true) || s.equals(k, true) || (k.length >= 4 && s.contains(k, true)) }) return if (n.startsWith("بلو") || n.startsWith("صندوق") || n.startsWith("پست")) n else "بانک $n"
    }
    return "بانک (${sender.ifBlank { "نامشخص" }})"
}
fun extractSmsBalanceRial(body: String): Long? {
    val n = SmsParser.normalizeDigits(body)
    val m = Regex("(?:مانده|موجودی|قابل برداشت)\\\\s*[:=\\\\-]?\\\\s*([0-9][0-9,.\\\\s]{2,18})").find(n) ?: return null
    val v = m.groupValues[1].replace(",", "").replace(".", "").replace(" ", "").trim().toLongOrNull() ?: return null
    return if (n.contains("تومان") && !n.contains("ریال")) v * 10L else v
}
"""
if "detectIranBank" not in spt: sp.write_text(spt + "\n" + b_kt, encoding="utf-8")

srp = Path("feature_src/SmsReceiver.kt")
srp.write_text(srp.read_text(encoding="utf-8").replace("if (timestamp < installedAt) return@Thread", "").replace("repo.resolveSmsAccount(parsed.accountLast4, sender)", "repo.resolveOrUpdateSmsAccount(parsed.accountLast4, sender, body)"), encoding="utf-8")

modp = Path("feature_override/Models.kt")
if "isExcluded" not in modp.read_text(encoding="utf-8"): modp.write_text(modp.read_text(encoding="utf-8") + '\nval Account.isExcluded: Boolean get() = groupName == "EXCLUDED"\n', encoding="utf-8")

repp = Path("feature_src/repo_01")
reps = repp.read_text(encoding="utf-8").replace("SELECT COALESCE(SUM(balance),0) FROM accounts", "SELECT COALESCE(SUM(balance),0) FROM accounts WHERE COALESCE(group_name,'') != 'EXCLUDED'").replace("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type=? AND occurred_at BETWEEN ? AND ?", "SELECT COALESCE(SUM(t.amount),0) FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id WHERE t.type=? AND t.occurred_at BETWEEN ? AND ? AND COALESCE(a.group_name,'') != 'EXCLUDED'")
r_add = """
fun setAccountExcluded(id: Long, ex: Boolean) { val cv = ContentValues().apply { put("group_name", if (ex) "EXCLUDED" else "عمومی") }; dbHelper.writableDatabase.update("accounts", cv, "id=?", arrayOf(id.toString())) }
fun resolveOrUpdateSmsAccount(last4: String, sender: String, body: String): Account {
    val bName = ir.hesabyar.finance.sms.detectIranBank(sender, body); val bal = ir.hesabyar.finance.sms.extractSmsBalanceRial(body)
    val m = accounts().firstOrNull { (last4.isNotBlank() && it.cardLast4 == last4) || it.bankName == bName || it.name.contains(bName) }
    if (m != null) {
        val dn = if (m.name.startsWith("حساب پیامکی")) (if (last4.isNotBlank()) "$bName ($last4)" else bName) else m.name
        val cv = ContentValues().apply { put("name", dn); put("bank_name", bName); if (last4.isNotBlank()) put("card_last4", last4); if (bal != null) put("balance", bal) }
        dbHelper.writableDatabase.update("accounts", cv, "id=?", arrayOf(m.id.toString()))
        return m.copy(name = dn, bankName = bName, balance = bal ?: m.balance)
    }
    val dn = if (last4.isNotBlank()) "$bName ($last4)" else bName
    val na = Account(name = dn, type = "بانکی", bankName = bName, cardLast4 = last4, balance = bal ?: 0L, groupName = "عمومی")
    return na.copy(id = addAccount(na))
}
fun scanInboxSms(ctx: Context, latestOnly: Boolean, fm: Long?, tm: Long?): String {
    val rows = mutableListOf<Triple<String, String, Long>>()
    val w = if (!latestOnly && fm != null && tm != null) "date BETWEEN $fm AND $tm" else null
    runCatching { ctx.contentResolver.query(android.provider.Telephony.Sms.Inbox.CONTENT_URI, arrayOf("address", "body", "date"), w, null, "date DESC")?.use { c -> var n = 0; while (c.moveToNext() && n < 2000) { rows += Triple(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2)); n++ } } }
    if (rows.isEmpty()) return "پیامکی یافت نشد"
    if (latestOnly) {
        val seen = mutableSetOf<String>()
        for ((s, b, d) in rows) { val p = ir.hesabyar.finance.sms.SmsParser.parse(s, b, d) ?: continue; val k = "${ir.hesabyar.finance.sms.detectIranBank(s, b)}|${p.accountLast4}"; if (k !in seen) { resolveOrUpdateSmsAccount(p.accountLast4, s, b); seen += k } }
        return "${seen.size} حساب بانکی شناسایی و موجودی به ریال ثبت شد"
    }
    var tc = 0; val bks = mutableSetOf<String>()
    for ((s, b, d) in rows.asReversed()) {
        val p = ir.hesabyar.finance.sms.SmsParser.parse(s, b, d) ?: continue; val acc = resolveOrUpdateSmsAccount(p.accountLast4, s, b); bks += acc.bankName
        if (p.amountToman > 0L && transactionByExternalKey(p.externalKey) == null) { if (addTransaction(FinanceTransaction(amount = p.amountToman, type = p.type, occurredAt = d, reason = p.reason, accountId = acc.id, categoryId = null, personId = null, source = TransactionSource.SMS, note = "پیامک ${acc.bankName}", externalKey = p.externalKey)) > 0L) tc++ }
    }
    return "${bks.size} حساب بانکی و $tc تراکنش به ریال ثبت شد"
}
}
"""
if "scanInboxSms" not in reps: repp.write_text(reps[:reps.rfind("}")] + "\n" + r_add, encoding="utf-8")

vmp = Path("feature_src/FinanceViewModel.kt"); vms = vmp.read_text(encoding="utf-8")
if "scanSms" not in vms: vmp.write_text(vms[:vms.rfind("}")] + '\nfun scanSms(latestOnly: Boolean, fm: Long?, tm: Long?) = runBg { val msg = repo.scanInboxSms(getApplication(), latestOnly, fm, tm); refreshDirect(); main.post { toast = msg } }\nfun toggleAccountExcluded(id: Long, ex: Boolean) = runBg("وضعیت استثنا حساب تغییر کرد") { repo.setAccountExcluded(id, ex); refreshDirect() }\n}\n', encoding="utf-8")

fap = Path("feature_src/FinanceApp.kt"); fas = fap.read_text(encoding="utf-8")
for imp in ["import androidx.compose.foundation.clickable","import ir.hesabyar.finance.data.*","import ir.hesabyar.finance.util.DateUtil","import ir.hesabyar.finance.util.JalaliDatePickerDialog"]:
    if imp not in fas: fas = fas.replace("import android.Manifest", "import android.Manifest\n" + imp, 1)
fas = fas.replace("Manifest.permission.RECEIVE_SMS", "Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS").replace("bottomBar =", "topBar = { HesabYarTopBar(vm) }, bottomBar =", 1)
fas = re.sub(r'PEOPLE\("[^"]+"\)', 'PEOPLE("حساب اشخاص")', fas)
top_kt = """
@Composable
fun HesabYarTopBar(vm: FinanceViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var sDlg by remember { mutableStateOf(false) }; var aDlg by remember { mutableStateOf(false) }
    val lch = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (it[Manifest.permission.READ_SMS] == true) sDlg = true }
    Surface(color = androidx.compose.ui.graphics.Color(0xFF0F766E)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("حساب‌یار پیشرفته (ریال)", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) sDlg = true else lch.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }) { Text("اسکن پیامک", fontSize = 11.sp) }
                FilledTonalButton(onClick = { aDlg = true }) { Text("استثنا حساب", fontSize = 11.sp) }
            }
        }
    }
    if (sDlg) {
        var latest by remember { mutableStateOf(true) }; val now = remember { System.currentTimeMillis() }
        var fm by remember { mutableStateOf(now - 30L * 86400000L) }; var tm by remember { mutableStateOf(now) }
        var pf by remember { mutableStateOf(false) }; var pt by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { sDlg = false }, title = { Text("شناسایی پیامک‌های بانکی و ثبت حساب‌ها") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable { latest = true }) { RadioButton(latest, { latest = true }); Text("فقط آخرین پیامک هر بانک (ثبت حساب و موجودی)") }
                Row(Modifier.fillMaxWidth().clickable { latest = false }) { RadioButton(!latest, { latest = false }); Text("انتخاب بازه زمانی دلخواه (ثبت حساب و تراکنش‌ها)") }
                if (!latest) {
                    OutlinedButton(onClick = { pf = true }, modifier = Modifier.fillMaxWidth()) { Text("از تاریخ: " + DateUtil.formatDate(fm)) }
                    OutlinedButton(onClick = { pt = true }, modifier = Modifier.fillMaxWidth()) { Text("تا تاریخ: " + DateUtil.formatDate(tm)) }
                }
            }
        }, confirmButton = { Button({ sDlg = false; vm.scanSms(latest, fm, tm + 86399000L) }) { Text("شروع شناسایی") } }, dismissButton = { TextButton({ sDlg = false }) { Text("انصراف") } })
        if (pf) JalaliDatePickerDialog(fm, { pf = false }, { fm = it; pf = false })
        if (pt) JalaliDatePickerDialog(tm, { pt = false }, { tm = it; pt = false })
    }
    if (aDlg) {
        AlertDialog(onDismissRequest = { aDlg = false }, title = { Text("استثنا کردن حساب از محاسبات کل") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (vm.accounts.isEmpty()) Text("حسابی ثبت نشده است.")
                vm.accounts.forEach { acc -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("${acc.name} (${acc.bankName})", fontSize = 12.sp, modifier = Modifier.weight(1f)); Switch(acc.isExcluded, { vm.toggleAccountExcluded(acc.id, it) }) } }
            }
        }, confirmButton = { Button({ aDlg = false }) { Text("بستن") } })
    }
}
"""
if "HesabYarTopBar" not in fas: fap.write_text(fas + "\n" + top_kt, encoding="utf-8")

for ktf in list(Path("feature_src").glob("*")) + list((root / "app/src/main/java/ir/hesabyar/finance").rglob("*.kt")):
    if ktf.is_file() and ktf.name != "SmsParser.kt":
        t = ktf.read_text(encoding="utf-8").replace("تومان", "ریال").replace("تومن", "ریال").replace('"دنگ"', '"حساب اشخاص"').replace("تب دنگ", "حساب اشخاص").replace("بخش دنگ", "حساب اشخاص")
        if ktf.name == "BankFileParser.kt": t = t.replace("if (valuesAreRial) raw / 10 else raw", "raw")
        ktf.write_text(t, encoding="utf-8")
