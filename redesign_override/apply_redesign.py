from pathlib import Path
import shutil

root = Path('project')
over = Path('redesign_override')

def copy(rel):
    src = over / rel
    dst = root / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)

for rel in [
    'app/src/main/java/ir/hesabyar/finance/util/DateUtil.kt',
    'app/src/main/java/ir/hesabyar/finance/ui/FinanceApp.kt',
    'app/src/main/java/ir/hesabyar/finance/ui/theme/Theme.kt',
    'app/src/main/res/drawable/ic_launcher_foreground.xml',
    'app/src/main/res/values/colors.xml',
]:
    copy(rel)

# Jalali month calculations in repository.
p = root / 'app/src/main/java/ir/hesabyar/finance/data/FinanceRepository.kt'
s = p.read_text()
if 'import ir.hesabyar.finance.util.DateUtil' not in s:
    s = s.replace('package ir.hesabyar.finance.data\n', 'package ir.hesabyar.finance.data\n\nimport ir.hesabyar.finance.util.DateUtil\n')
s = s.replace(
    '        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()\n'
    '        val monthEnd = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1',
    '        val (monthStart, monthEnd) = DateUtil.jalaliMonthRange(today)'
)
p.write_text(s)

# ViewModel: Jalali installments/months + remove every historical SMS scan entry point.
p = root / 'app/src/main/java/ir/hesabyar/finance/ui/FinanceViewModel.kt'
s = p.read_text()
if 'import ir.hesabyar.finance.util.DateUtil' not in s:
    s = s.replace('import ir.hesabyar.finance.sms.SmsImporter\n', 'import ir.hesabyar.finance.sms.SmsImporter\nimport ir.hesabyar.finance.util.DateUtil\n')
s = s.replace(
    '            val due = baseDate.plusMonths(index.toLong() * item.repeatEvery.coerceAtLeast(1)).atStartOfDay(zone).toInstant().toEpochMilli()',
    '            val dueDate = DateUtil.plusJalaliMonths(baseDate, index.toLong() * item.repeatEvery.coerceAtLeast(1))\n'
    '            val due = dueDate.atStartOfDay(zone).toInstant().toEpochMilli()'
)
old_month = '''    private fun monthRange(): Pair<Long,Long> {
        val zone=ZoneId.systemDefault(); val now=LocalDate.now(zone); val start=now.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli(); val end=now.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()-1
        return start to end
    }'''
s = s.replace(old_month, '    private fun monthRange(): Pair<Long,Long> = DateUtil.jalaliMonthRange()')
s = s.replace('import ir.hesabyar.finance.sms.SmsImporter\n', '')
start = s.find('    fun syncSms() = runBg {')
if start >= 0:
    end = s.find('\n    fun importBank(', start)
    if end < 0:
        raise SystemExit('Could not remove historical SMS sync function')
    s = s[:start] + s[end + 1:]
p.write_text(s)

# Manifest: RECEIVE_SMS only. Never request READ_SMS / Inbox access.
p = root / 'app/src/main/AndroidManifest.xml'
s = p.read_text()
s = s.replace('    <uses-permission android:name="android.permission.READ_SMS" />\n', '')
p.write_text(s)

# Remove the old Inbox importer completely so no code path can scan pre-install SMS.
legacy_importer = root / 'app/src/main/java/ir/hesabyar/finance/sms/SmsImporter.kt'
if legacy_importer.exists():
    legacy_importer.unlink()

# Receiver safety net: ignore any SMS whose timestamp is older than first install time.
p = root / 'app/src/main/java/ir/hesabyar/finance/sms/SmsReceiver.kt'
s = p.read_text()
needle = '            try {\n                val parsed = SmsParser.parse(sender, body, timestamp) ?: return@Thread'
replacement = '''            try {
                val installedAt = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
                }.getOrDefault(System.currentTimeMillis())
                if (timestamp < installedAt) return@Thread
                val parsed = SmsParser.parse(sender, body, timestamp) ?: return@Thread'''
if needle not in s:
    raise SystemExit('SmsReceiver patch marker not found')
s = s.replace(needle, replacement)
p.write_text(s)

# UI permission/disclosure: RECEIVE_SMS only, no historical sync action.
p = root / 'app/src/main/java/ir/hesabyar/finance/ui/FinanceApp.kt'
s = p.read_text()
s = s.replace(
    '    fun hasSmsPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&\n'
    '        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED',
    '    fun hasSmsPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED'
)
s = s.replace(
    '        smsAllowed = result[Manifest.permission.READ_SMS] == true && result[Manifest.permission.RECEIVE_SMS] == true\n'
    '        if (smsAllowed) vm.syncSms() else localMessage = "بدون مجوز پیامک، ثبت دستی همچنان فعال است."',
    '        smsAllowed = result[Manifest.permission.RECEIVE_SMS] == true\n'
    '        localMessage = if (smsAllowed) "ثبت خودکار پیامک‌های جدید فعال شد" else "بدون مجوز پیامک، ثبت دستی همچنان فعال است."'
)
s = s.replace('                    onSyncSms = vm::syncSms,\n', '')
s = s.replace(
    'حساب‌یار فقط برای تشخیص واریز و برداشت، پیامک‌های مالی را روی همین دستگاه پردازش می‌کند. متن پیامک‌ها به سرور ارسال نمی‌شود و پیام‌های غیرمالی نادیده گرفته می‌شوند. این دسترسی اختیاری است و ثبت دستی بدون آن هم کار می‌کند.',
    'حساب‌یار فقط پیامک‌های بانکی جدیدی را که بعد از فعال‌سازی این قابلیت دریافت می‌شوند روی همین دستگاه بررسی می‌کند. برنامه به پیامک‌های قدیمی Inbox دسترسی نمی‌گیرد و هیچ پیامکی به سرور ارسال نمی‌شود. این دسترسی اختیاری است و ثبت دستی بدون آن هم کار می‌کند.'
)
s = s.replace(
    'smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))',
    'smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))'
)
p.write_text(s)

# Main screens + Jalali date picker.
p = root / 'app/src/main/java/ir/hesabyar/finance/ui/screens/Screens.kt'
s = p.read_text()
s = s.replace('import android.app.DatePickerDialog\n', '')
s = s.replace('import java.time.DayOfWeek\n', '')
s = s.replace('import java.time.Instant\n', '')

def replace_between(text, start_marker, end_marker, replacement):
    a = text.index(start_marker)
    b = text.index(end_marker, a)
    return text[:a] + replacement + text[b:]

home = (over / 'sections/home.txt').read_text()
home = home.replace('    onRequestSms: () -> Unit,\n    onSyncSms: () -> Unit,\n', '    onRequestSms: () -> Unit,\n')
old_sms_enabled = '''        } else {
            OutlinedButton(
                onClick = onSyncSms,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Sync, null)
                Spacer(Modifier.width(8.dp))
                Text("همگام‌سازی پیامک‌های بانکی")
            }
        }
'''
new_sms_enabled = '''        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column(Modifier.weight(1f)) {
                        Text("ثبت پیامک‌های جدید فعال است", fontWeight = FontWeight.Bold)
                        Text(
                            "فقط پیامک‌هایی که از این به بعد می‌رسند بررسی می‌شوند؛ پیامک‌های قدیمی خوانده نمی‌شوند.",
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
'''
if old_sms_enabled not in home:
    raise SystemExit('Home SMS sync block not found')
home = home.replace(old_sms_enabled, new_sms_enabled)
reports = (over / 'sections/reports.txt').read_text()
datepicker = (over / 'sections/datepicker.txt').read_text()
title = (over / 'sections/screentitle.txt').read_text()
s = replace_between(s, '@Composable\nfun HomeScreen(', '@Composable\nprivate fun SectionHeader', home)
s = replace_between(s, '@Composable\nfun ReportsScreen', '@Composable\nprivate fun ReportValue', reports)
s = replace_between(s, '@Composable\nprivate fun DateButton', '@Composable\nfun MoreScreen', datepicker)
s = s.replace('        Text("تراکنش‌ها", fontSize=28.sp, fontWeight=FontWeight.Black)', '        ScreenTitle("تراکنش‌ها", "جستجو و فیلتر همه دریافت‌ها و پرداخت‌ها", Icons.Rounded.ReceiptLong)')
s = s.replace('            Column(Modifier.weight(1f)) { Text("دنگ‌ها و اشخاص",fontSize=28.sp,fontWeight=FontWeight.Black); Text("طلب و بدهی هر نفر در یک نگاه",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp) }', '            Box(Modifier.weight(1f)) { ScreenTitle("دنگ‌ها و اشخاص", "طلب و بدهی هر نفر در یک نگاه", Icons.Rounded.Groups) }')
s = s.replace('        Text("مدیریت",fontSize=28.sp,fontWeight=FontWeight.Black)', '        ScreenTitle("بیشتر", "حساب‌ها، اقساط، دسته‌بندی و ابزارها", Icons.Rounded.Tune)')
s = s.replace('var date by remember{mutableStateOf(LocalDate.now(zone).plusDays(30))}', 'var date by remember{mutableStateOf(DateUtil.plusJalaliMonths(LocalDate.now(zone),1))}')
marker = '@Composable\nprivate fun FeatureSection'
if 'private fun ScreenTitle(' not in s:
    s = s.replace(marker, title + marker)
p.write_text(s)

# Project branding/version.
p = root / 'settings.gradle.kts'
s = p.read_text().replace('rootProject.name = "HesabYar2027"', 'rootProject.name = "HesabYar"')
p.write_text(s)
p = root / 'app/build.gradle.kts'
s = p.read_text().replace('versionCode = 1', 'versionCode = 3').replace('versionName = "1.0.0"', 'versionName = "1.2.0"')
p.write_text(s)

# Strong build-time guards.
required = ['DateUtil.jalaliMonthRange','JalaliDatePickerDialog','مدیریت مالی شخصی','تقویم شمسی','ثبت پیامک‌های جدید فعال است']
all_text = '\n'.join(x.read_text(errors='ignore') for x in (root/'app/src/main').rglob('*') if x.is_file())
for token in required:
    if token not in all_text:
        raise SystemExit(f'Missing expected redesign token: {token}')
for forbidden in ['۲۰۲۷', '2027', 'android.permission.READ_SMS', 'importExisting(', 'همگام‌سازی پیامک‌های بانکی', 'onSyncSms', 'syncSms()']:
    if forbidden in all_text:
        raise SystemExit(f'Forbidden legacy/history token remains: {forbidden}')
print('Redesign/Jalali/new-SMS-only overrides applied successfully')
