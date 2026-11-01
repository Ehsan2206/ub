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
p.write_text(s)

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

p = root / 'settings.gradle.kts'
s = p.read_text().replace('rootProject.name = "HesabYar2027"', 'rootProject.name = "HesabYar"')
p.write_text(s)
p = root / 'app/build.gradle.kts'
s = p.read_text().replace('versionCode = 1', 'versionCode = 2').replace('versionName = "1.0.0"', 'versionName = "1.1.0"')
p.write_text(s)

required = ['DateUtil.jalaliMonthRange','JalaliDatePickerDialog','مدیریت مالی شخصی','تقویم شمسی']
all_text = '\n'.join(x.read_text(errors='ignore') for x in (root/'app/src/main').rglob('*') if x.is_file())
for token in required:
    if token not in all_text:
        raise SystemExit(f'Missing expected redesign token: {token}')
if '۲۰۲۷' in all_text or '2027' in all_text:
    raise SystemExit('2027 branding still present in app source')
print('Redesign/Jalali overrides applied successfully')
