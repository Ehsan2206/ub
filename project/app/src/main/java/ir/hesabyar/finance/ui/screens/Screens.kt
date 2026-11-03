package ir.hesabyar.finance.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import ir.hesabyar.finance.data.*
import ir.hesabyar.finance.util.DateUtil
import ir.hesabyar.finance.util.Money
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(padding:PaddingValues,summary:DashboardSummary,recent:List<TransactionRow>,people:List<PersonBalance>,smsAllowed:Boolean,onRequestSms:()->Unit,onOpenTransactions:()->Unit,onOpenPeople:()->Unit,onImportSms:()->Unit,onEditTransaction:(Long)->Unit) {
    Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("حساب‌یار",fontSize=30.sp,fontWeight=FontWeight.Black); Text(DateUtil.formatLongDate(DateUtil.today()),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Rounded.AccountBalanceWallet,null,Modifier.padding(14.dp),tint=MaterialTheme.colorScheme.primary) }
        }
        Surface(shape=RoundedCornerShape(28.dp),color=Color(0xFF172D42)) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("موجودی حساب‌های فعال",color=Color(0xFFB9CDDC),fontSize=13.sp)
                Text(Money.format(summary.totalBalance),color=Color.White,fontSize=28.sp,lineHeight=38.sp,fontWeight=FontWeight.Black)
                HorizontalDivider(color=Color.White.copy(alpha=.12f))
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { HeroMini("درآمد این ماه",summary.monthIncome,Icons.Rounded.SouthWest,Modifier.weight(1f),true);HeroMini("هزینه این ماه",summary.monthExpense,Icons.Rounded.NorthEast,Modifier.weight(1f),false) }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { StatCard("طلب از اشخاص",summary.receivable,Icons.Rounded.CallReceived,Modifier.weight(1f));StatCard("بدهی به اشخاص",summary.payable,Icons.Rounded.CallMade,Modifier.weight(1f)) }
        Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) { Icon(Icons.Rounded.Sms,null);Column(Modifier.weight(1f)){Text("پیامک‌های بانکی",fontWeight=FontWeight.Bold);Text(if(smsAllowed)"ثبت خودکار پیامک‌های جدید فعال است" else "ثبت خودکار دریافت‌ها و پرداخت‌ها",fontSize=12.sp)} }
                Text("ورود تاریخچه با بازه دلخواه یا دریافت آخرین مانده هر حساب",fontSize=12.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick=onImportSms){Text("ورود پیامک‌های قبلی")}; if(!smsAllowed) TextButton(onClick=onRequestSms){Text("فعال‌سازی")}}
            }
        }
        SectionHeader("تراکنش‌های اخیر","مشاهده همه",onOpenTransactions)
        if(recent.isEmpty()) EmptyCard("با دکمه + تراکنش ثبت کنید یا پیامک‌های بانکی را وارد کنید") else recent.forEach { row -> TransactionTile(row,onEdit={onEditTransaction(row.transaction.id)}) }
        OutlinedButton(onClick=onOpenPeople,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Icon(Icons.Rounded.Groups,null);Spacer(Modifier.width(8.dp));Text("حساب اشخاص • طلب، بدهی و ریز گردش")}
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun HeroMini(
    label: String,
    value: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    positive: Boolean
) {
    val accent = if (positive) Color(0xFF82E7B8) else Color(0xFFFFB8AE)
    Surface(
        modifier,
        color = Color.White.copy(alpha = .10f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = Color.White.copy(alpha = .76f), fontSize = 11.sp)
            }
            Text(
                Money.compact(value),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                Money.compact(value),
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                maxLines = 1
            )
        }
    }
}
@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TransactionTile(row: TransactionRow, onDelete: (() -> Unit)? = null, onEdit: (() -> Unit)? = null) {
    val tx = row.transaction
    Surface(modifier=Modifier.clickable(enabled=onEdit!=null){onEdit?.invoke()},shape = RoundedCornerShape(22.dp), tonalElevation = .5.dp) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = if (tx.type == TransactionType.INCOME) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer) {
                Icon(if (tx.type==TransactionType.INCOME) Icons.Rounded.SouthWest else Icons.Rounded.NorthEast, null, modifier = Modifier.padding(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(tx.reason.ifBlank { if(tx.type==TransactionType.INCOME) "درآمد" else "هزینه" }, fontWeight = FontWeight.Bold)
                Text(listOf(row.accountName, row.categoryName, row.personName).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { DateUtil.formatDate(tx.occurredAt) }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, modifier=Modifier.widthIn(max=170.dp)) {
                Text((if(tx.type==TransactionType.INCOME) "+" else "−") + Money.format(tx.amount), fontWeight = FontWeight.Black, color = if(tx.type==TransactionType.INCOME) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                Text(DateUtil.formatDate(tx.occurredAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    onEdit?.let { action -> TextButton(onClick=action,modifier=Modifier.semantics { contentDescription="ویرایش ${tx.reason}" },contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)){Text("ویرایش",fontSize=11.sp)} }
                    onDelete?.let { TextButton(onClick=it,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)){Text("حذف",color=MaterialTheme.colorScheme.error,fontSize=11.sp)} }
                }
            }
        }
    }
}

@Composable
fun TransactionsScreen(padding: PaddingValues, transactions: List<TransactionRow>, accounts: List<Account>, people: List<Person>, onDelete:(Long)->Unit,onEdit:(Long)->Unit) {
    var deleting by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<TransactionType?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var personId by remember { mutableStateOf<Long?>(null) }
    val filtered = remember(transactions, query, type, accountId, personId) {
        transactions.filter { r ->
            (query.isBlank() || r.transaction.reason.contains(query, true) || r.accountName.contains(query,true) || r.personName.contains(query,true)) &&
                (type==null || r.transaction.type==type) && (accountId==null || r.transaction.accountId==accountId) && (personId==null || r.transaction.personId==personId || r.personName.contains(people.firstOrNull{it.id==personId}?.name.orEmpty(), true))
        }
    }
    Column(Modifier.padding(padding).fillMaxSize().padding(horizontal=16.dp, vertical=10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenTitle("تراکنش‌ها", "جستجو و فیلتر همه دریافت‌ها و پرداخت‌ها", Icons.Rounded.ReceiptLong)
        OutlinedTextField(query, {query=it}, modifier=Modifier.fillMaxWidth(), placeholder={Text("جستجو در علت، حساب یا شخص")}, leadingIcon={Icon(Icons.Rounded.Search,null)}, singleLine=true, shape=RoundedCornerShape(20.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(type==null,{type=null},{Text("همه")})
            FilterChip(type==TransactionType.EXPENSE,{type=TransactionType.EXPENSE},{Text("مخارج")})
            FilterChip(type==TransactionType.INCOME,{type=TransactionType.INCOME},{Text("درآمد")})
            SimpleSelectorChip("حساب", accounts.map { it.id to it.name }, accountId) { accountId=it }
            SimpleSelectorChip("شخص", people.map { it.id to it.name }, personId) { personId=it }
        }
        Text("${filtered.size} مورد", fontSize=12.sp, color=MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp), contentPadding=PaddingValues(bottom=100.dp)) {
            items(filtered, key={it.transaction.id}) { row -> TransactionTile(row,onDelete={deleting=row.transaction.id},onEdit={onEdit(row.transaction.id)}) }
            if (filtered.isEmpty()) item { EmptyCard("نتیجه‌ای با این فیلتر پیدا نشد") }
        }
    }
    deleting?.let { id -> AlertDialog(onDismissRequest={deleting=null},title={Text("حذف تراکنش؟")},text={Text("این تراکنش و سهم‌های مرتبط اشخاص حذف می‌شوند. پیامک حذف‌شده در ورود دوباره تاریخچه برنمی‌گردد. مانده اعلام‌شده بانک تغییر نمی‌کند؛ گردش‌های بعد از آخرین مانده اصلاح می‌شوند.")},confirmButton={Button(onClick={onDelete(id);deleting=null}){Text("حذف")}},dismissButton={TextButton(onClick={deleting=null}){Text("انصراف")}}) }
}

@Composable
private fun SimpleSelectorChip(label: String, options: List<Pair<Long,String>>, selectedId: Long?, onSelect: (Long?)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick={open=true}, label={Text(options.firstOrNull{it.first==selectedId}?.second ?: label)}, leadingIcon={Icon(Icons.Rounded.FilterAlt,null, modifier=Modifier.size(18.dp))})
        DropdownMenu(expanded=open,onDismissRequest={open=false}) {
            DropdownMenuItem(text={Text("همه")},onClick={onSelect(null);open=false})
            options.forEach { (id,name)-> DropdownMenuItem(text={Text(name)},onClick={onSelect(id);open=false}) }
        }
    }
}

@Composable
fun PeopleScreen(padding:PaddingValues,people:List<PersonBalance>,ledgers:Map<Long,List<LedgerEntry>>,onAddPerson:(Person)->Unit,onAddLedger:(Long,Long,Long,String)->Unit,onDeleteLedger:(Long)->Unit) {
    var add by remember { mutableStateOf(false) };var selectedId by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.padding(padding).fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.weight(1f)){ScreenTitle("حساب اشخاص","طلب، بدهی و ریز گردش هر نفر",Icons.Rounded.Groups)};FilledTonalIconButton(onClick={add=true}){Icon(Icons.Rounded.PersonAdd,"افزودن شخص")} }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=90.dp)) {
            items(people,key={it.person.id}) { p ->
                Surface(Modifier.fillMaxWidth().clickable{selectedId=p.person.id},shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.primaryContainer){Text(p.person.name.take(1),Modifier.padding(14.dp),fontWeight=FontWeight.Bold)}
                        Column(Modifier.weight(1f)){Text(p.person.name,fontWeight=FontWeight.Bold);Text("مشاهده گردش و ثبت تراکنش",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        Column(horizontalAlignment=Alignment.End){Text(if(p.net>0)"طلب من" else if(p.net<0)"بدهی من" else "تسویه",fontSize=11.sp);Text(Money.format(p.net.absoluteValue),fontWeight=FontWeight.Bold,color=if(p.net<0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)}
                    }
                }
            }
            if(people.isEmpty()) item{EmptyCard("یک شخص اضافه کنید تا طلب، بدهی و گردش حساب او را ثبت کنید")}
        }
    }
    if(add) AddPersonDialog({add=false}){onAddPerson(it);add=false}
    people.firstOrNull{it.person.id==selectedId}?.let { p -> PersonLedgerDialog(p,ledgers[p.person.id].orEmpty(),{selectedId=null},{signed,date,note->onAddLedger(p.person.id,signed,date,note)},onDeleteLedger) }
}

@Composable
private fun AddPersonDialog(onDismiss:()->Unit,onSave:(Person)->Unit) {
    var name by remember{mutableStateOf("")}; var phone by remember{mutableStateOf("")}; var group by remember{mutableStateOf("دوستان")}; var note by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("شخص جدید")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("نام")});OutlinedTextField(phone,{phone=it},label={Text("شماره تماس")});OutlinedTextField(group,{group=it},label={Text("دسته‌بندی")});OutlinedTextField(note,{note=it},label={Text("یادداشت")})}},confirmButton={Button(onClick={onSave(Person(name=name.trim(),phone=phone.trim(),groupName=group.trim().ifBlank{"سایر"},note=note.trim()))},enabled=name.isNotBlank()){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("لغو")}})
}

@Composable
private fun PersonBalanceDialog(p:PersonBalance,onDismiss:()->Unit,onSettle:(Long,Boolean)->Unit){
    var amount by remember{mutableStateOf(p.net.absoluteValue.toString())}; var theyPaid by remember{mutableStateOf(p.net>=0)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(p.person.name)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(if(p.net>0)"این شخص ${Money.format(p.net)} به شما بدهکار است." else if(p.net<0)"شما ${Money.format(-p.net)} به این شخص بدهکارید." else "حساب شما با این شخص تسویه است.");OutlinedTextField(amount,{amount=it},label={Text("مبلغ تسویه (ریال)")},singleLine=true);Row(verticalAlignment=Alignment.CenterVertically){Text(if(theyPaid)"او به من پرداخت کرد" else "من به او پرداخت کردم",modifier=Modifier.weight(1f));Switch(theyPaid,{theyPaid=it})}}},confirmButton={Button(onClick={onSettle(Money.parse(amount),theyPaid)},enabled=Money.parse(amount)>0){Text("ثبت تسویه")}},dismissButton={TextButton(onClick=onDismiss){Text("بستن")}})
}

private enum class ReportRange(val label:String){TODAY("روزانه"),WEEK("هفتگی"),MONTH("ماهانه"),YEAR("سالانه"),CUSTOM("دلخواه")}

@Composable
fun ReportsScreen(padding:PaddingValues,accounts:List<Account>,people:List<Person>,report:ReportSummary,onLoad:(Long,Long,Long?,Long?)->Unit){
    val zone=ZoneId.systemDefault()
    var range by remember{mutableStateOf(ReportRange.MONTH)}
    var accountId by remember{mutableStateOf<Long?>(null)}
    var personId by remember{mutableStateOf<Long?>(null)}
    var customFrom by remember{mutableStateOf(DateUtil.plusJalaliMonths(LocalDate.now(zone), -1))}
    var customTo by remember{mutableStateOf(LocalDate.now(zone))}
    fun customBounds(): Pair<Long,Long> {
        val from = customFrom.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = customTo.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1
        return from to to
    }
    fun bounds():Pair<Long,Long> = when(range){
        ReportRange.TODAY -> DateUtil.dayRange(LocalDate.now(zone))
        ReportRange.WEEK -> DateUtil.jalaliWeekRange(LocalDate.now(zone))
        ReportRange.MONTH -> DateUtil.jalaliMonthRange(LocalDate.now(zone))
        ReportRange.YEAR -> DateUtil.jalaliYearRange(LocalDate.now(zone))
        ReportRange.CUSTOM -> customBounds()
    }
    LaunchedEffect(range,accountId,personId,customFrom,customTo){ val b=bounds(); onLoad(b.first,b.second,accountId,personId) }
    Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        ScreenTitle("گزارش‌های مالی", "تحلیل درآمد و هزینه بر اساس تقویم شمسی", Icons.Rounded.BarChart)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){ReportRange.entries.forEach{r->FilterChip(range==r,{range=r},{Text(r.label)})}}
        if(range==ReportRange.CUSTOM){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DateButton("از",customFrom,Modifier.weight(1f)){customFrom=it};DateButton("تا",customTo,Modifier.weight(1f)){customTo=it}}}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){SimpleSelectorChip("همه حساب‌ها",accounts.map{it.id to it.name},accountId){accountId=it};SimpleSelectorChip("همه اشخاص",people.map{it.id to it.name},personId){personId=it}}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){ReportValue("درآمد",report.income,MaterialTheme.colorScheme.secondary,Modifier.weight(1f));ReportValue("هزینه",report.expense,MaterialTheme.colorScheme.error,Modifier.weight(1f))}
        ReportValue("خالص بازه",report.net,if(report.net>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,Modifier.fillMaxWidth())
        Text("هزینه بر اساس دسته‌بندی",fontSize=18.sp,fontWeight=FontWeight.Black)
        if(report.byCategory.isEmpty()) EmptyCard("در این بازه هزینه‌ای ثبت نشده") else {
            val max=report.byCategory.maxOf{it.second}.coerceAtLeast(1)
            report.byCategory.forEach{(name,value)->Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Row{Text(name,Modifier.weight(1f),fontWeight=FontWeight.Medium);Text(Money.format(value),fontSize=12.sp)};Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surfaceVariant)){Box(Modifier.fillMaxWidth((value.toFloat()/max.toFloat()).coerceIn(.03f,1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))}}}
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun ReportValue(label:String,value:Long,color:Color,modifier:Modifier){Surface(modifier,shape=RoundedCornerShape(24.dp),tonalElevation=1.dp){Column(Modifier.padding(16.dp)){Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(Money.format(value),fontWeight=FontWeight.Black,fontSize=18.sp,color=color)}}}

@Composable
internal fun DateButton(label:String,date:LocalDate,modifier:Modifier,onDate:(LocalDate)->Unit){
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick={open=true},modifier=modifier,shape=RoundedCornerShape(16.dp)){
        Icon(Icons.Rounded.CalendarMonth,null); Spacer(Modifier.width(6.dp)); Text("$label ${DateUtil.formatLocalDate(date)}")
    }
    if(open) JalaliDatePickerDialog(date,{open=false},{selected->onDate(selected);open=false})
}

@Composable
private fun JalaliDatePickerDialog(initial:LocalDate,onDismiss:()->Unit,onConfirm:(LocalDate)->Unit){
    val j = remember(initial) { DateUtil.toJalali(initial) }
    var year by remember { mutableIntStateOf(j.year) }
    var month by remember { mutableIntStateOf(j.month) }
    var day by remember { mutableIntStateOf(j.day) }
    val maxDay = DateUtil.daysInJalaliMonth(year,month)
    LaunchedEffect(year,month){ if(day>maxDay) day=maxDay }
    val preview = remember(year,month,day) { runCatching { DateUtil.fromJalali(year,month,day) }.getOrNull() }
    AlertDialog(
        onDismissRequest=onDismiss,
        icon={Icon(Icons.Rounded.CalendarMonth,null)},
        title={Text("انتخاب تاریخ شمسی")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.fillMaxWidth()){
                Text(preview?.let{DateUtil.formatLongDate(it)}?:"تاریخ نامعتبر",Modifier.padding(14.dp),fontWeight=FontWeight.Bold)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                JalaliNumberMenu("سال",year,(year-5..year+5).toList(),{year=it},Modifier.weight(1.2f))
                JalaliNumberMenu("ماه",month,(1..12).toList(),{month=it},Modifier.weight(1f))
                JalaliNumberMenu("روز",day,(1..maxDay).toList(),{day=it},Modifier.weight(1f))
            }
        }},
        confirmButton={Button(onClick={preview?.let(onConfirm)},enabled=preview!=null){Text("انتخاب")}},
        dismissButton={TextButton(onClick=onDismiss){Text("لغو")}}
    )
}

@Composable
private fun JalaliNumberMenu(label:String,value:Int,options:List<Int>,onSelect:(Int)->Unit,modifier:Modifier=Modifier){
    var open by remember { mutableStateOf(false) }
    Box(modifier){
        OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){
            Column(horizontalAlignment=Alignment.CenterHorizontally){Text(label,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value.toString(),fontWeight=FontWeight.Bold)}
        }
        DropdownMenu(expanded=open,onDismissRequest={open=false}){
            options.forEach{v->DropdownMenuItem(text={Text(v.toString())},onClick={onSelect(v);open=false})}
        }
    }
}

@Composable
fun MoreScreen(
    padding:PaddingValues,accounts:List<Account>,people:List<Person>,categories:List<Category>,installments:List<Installment>,bankEntries:List<BankEntry>,
    onAddAccount:(Account)->Unit,onAddCategory:(Category)->Unit,onAddInstallment:(Installment,Int)->Unit,onPaid:(Long)->Unit,onImport:(Uri,Boolean,Long?)->Unit,onRequestNotification:()->Unit,onExclude:(Long,Boolean)->Unit,onSms:()->Unit
){
    var addAccount by remember{mutableStateOf(false)}; var addCategory by remember{mutableStateOf(false)}; var addInstallment by remember{mutableStateOf(false)}; var valuesAreRial by remember{mutableStateOf(true)}; var importAccountId by remember{mutableStateOf<Long?>(accounts.firstOrNull()?.id)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{onImport(it,valuesAreRial,importAccountId)}}
    Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        ScreenTitle("حساب‌ها و ابزارها", "بانک‌ها، پیامک‌ها و تنظیمات مالی", Icons.Rounded.AccountBalance)
        OutlinedButton(onClick=onSms,modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.Sms,null);Spacer(Modifier.width(8.dp));Text("ورود پیامک و مدیریت سرشماره‌ها")}
        FeatureSection("حساب‌ها",Icons.Rounded.AccountBalanceWallet,action="افزودن",onAction={addAccount=true}){
            Text("حساب مستثنا در موجودی کل، گزارش‌ها و ثبت خودکار پیامک محاسبه نمی‌شود.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            accounts.forEach { a ->
                Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(a.name,fontWeight=FontWeight.Bold);Text(a.bankName.ifBlank{a.type},fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text(Money.format(a.balance),fontWeight=FontWeight.Bold)}
                    Row(verticalAlignment=Alignment.CenterVertically){Text(if(a.excluded)"مستثنا از محاسبات" else "فعال در محاسبات",Modifier.weight(1f),fontSize=12.sp);Switch(checked=!a.excluded,onCheckedChange={onExclude(a.id,!it)})}
                    HorizontalDivider()
                }
            }
        }
        FeatureSection("دسته‌بندی‌های مالی",Icons.Rounded.Category,action="افزودن",onAction={addCategory=true}){
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){categories.forEach{c->AssistChip(onClick={},label={Text(c.name)},leadingIcon={Text(if(c.type==TransactionType.INCOME)"+" else "−")})}}
        }
        FeatureSection("اقساط و یادآوری",Icons.Rounded.EventRepeat,action="قسط جدید",onAction={addInstallment=true}){
            OutlinedButton(onClick=onRequestNotification,modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.NotificationsActive,null);Spacer(Modifier.width(8.dp));Text("بررسی مجوز اعلان")}
            installments.filter{it.status==InstallmentStatus.OPEN}.take(5).forEach{i->Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(i.title,fontWeight=FontWeight.Bold);Text("سررسید ${DateUtil.formatDate(i.dueAt)}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Column(horizontalAlignment=Alignment.End){Text(Money.format(i.amount),fontWeight=FontWeight.Bold);TextButton(onClick={onPaid(i.id)}){Text("پرداخت شد")}}}}
            if(installments.none{it.status==InstallmentStatus.OPEN}) Text("قسط پرداخت‌نشده‌ای ثبت نشده",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FeatureSection("ورود فایل بانک و مغایرت‌گیری",Icons.Rounded.FactCheck){
            SimpleSelectorField("حساب مورد تطبیق",accounts.map{it.id to it.name},importAccountId){importAccountId=it}
            Row(verticalAlignment=Alignment.CenterVertically){Text(if(valuesAreRial)"واحد فایل: ریال" else "واحد فایل: تومان؛ تبدیل به ریال",Modifier.weight(1f));Switch(valuesAreRial,{valuesAreRial=it})}
            Button(onClick={picker.launch(arrayOf("text/*","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.UploadFile,null);Spacer(Modifier.width(8.dp));Text("انتخاب CSV / XLSX")}
            if(bankEntries.isNotEmpty()){
                val matched=bankEntries.count{it.matchedTransactionId!=null};Text("${bankEntries.size} ردیف اخیر • $matched تطبیق • ${bankEntries.size-matched} مغایرت",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                bankEntries.take(8).forEach{e->Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(e.matchedTransactionId!=null)Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,null,tint=if(e.matchedTransactionId!=null)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(e.description,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp);Text(DateUtil.formatDate(e.occurredAt),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text(Money.format(e.amount),fontSize=11.sp,fontWeight=FontWeight.Bold)} }
            }
        }
        FeatureSection("حریم خصوصی",Icons.Rounded.Security){Text("داده‌های مالی، پیامک‌های شناسایی‌شده و فایل‌های بانکی در پایگاه‌داده محلی گوشی نگهداری می‌شوند. این نسخه هیچ سرویس ابری یا تبلیغاتی ندارد.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Spacer(Modifier.height(90.dp))
    }
    if(addAccount)AddAccountDialog({addAccount=false}){onAddAccount(it);addAccount=false}
    if(addCategory)AddCategoryDialog({addCategory=false}){onAddCategory(it);addCategory=false}
    if(addInstallment)AddInstallmentDialog(accounts,people,{addInstallment=false}){item,count->onAddInstallment(item,count);addInstallment=false}
}

@Composable
private fun ScreenTitle(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(10.dp))}
        Column(Modifier.weight(1f)){Text(title,fontSize=24.sp,fontWeight=FontWeight.Black);Text(subtitle,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable
internal fun FeatureSection(title:String,icon:androidx.compose.ui.graphics.vector.ImageVector,action:String?=null,onAction:()->Unit={},content:@Composable ColumnScope.()->Unit){Surface(shape=RoundedCornerShape(26.dp),tonalElevation=1.dp){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(9.dp));Text(title,fontWeight=FontWeight.Black,fontSize=17.sp,modifier=Modifier.weight(1f));action?.let{TextButton(onClick=onAction){Text(it)}}};content()}}}

@Composable
private fun AddCategoryDialog(onDismiss:()->Unit,onSave:(Category)->Unit){var name by remember{mutableStateOf("")};var type by remember{mutableStateOf(TransactionType.EXPENSE)};AlertDialog(onDismissRequest=onDismiss,title={Text("دسته‌بندی مالی جدید")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(type==TransactionType.EXPENSE,{type=TransactionType.EXPENSE},{Text("هزینه")});FilterChip(type==TransactionType.INCOME,{type=TransactionType.INCOME},{Text("درآمد")})};OutlinedTextField(name,{name=it},label={Text("نام دسته‌بندی")})}},confirmButton={Button(onClick={onSave(Category(name=name.trim(),type=type,icon=if(type==TransactionType.INCOME)"+" else "−"))},enabled=name.isNotBlank()){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("لغو")}})}

@Composable
private fun AddAccountDialog(onDismiss:()->Unit,onSave:(Account)->Unit){var name by remember{mutableStateOf("")};var type by remember{mutableStateOf("بانک")};var bank by remember{mutableStateOf("")};var last4 by remember{mutableStateOf("")};var balance by remember{mutableStateOf("")};var group by remember{mutableStateOf("شخصی")};AlertDialog(onDismissRequest=onDismiss,title={Text("حساب جدید")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(name,{name=it},label={Text("نام حساب")});OutlinedTextField(type,{type=it},label={Text("نوع: بانک، نقدی، کیف پول...")});OutlinedTextField(bank,{bank=it},label={Text("نام بانک")});OutlinedTextField(last4,{last4=it.takeLast(4)},label={Text("۴ رقم آخر کارت/حساب")});OutlinedTextField(balance,{balance=it},label={Text("موجودی اولیه (ریال)")});OutlinedTextField(group,{group=it},label={Text("دسته‌بندی حساب")})}},confirmButton={Button(onClick={onSave(Account(name=name.trim(),type=type.trim(),bankName=bank.trim(),cardLast4=last4.trim(),balance=Money.parse(balance),groupName=group.trim().ifBlank{"شخصی"}))},enabled=name.isNotBlank()){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("لغو")}})}

@Composable
private fun AddInstallmentDialog(accounts:List<Account>,people:List<Person>,onDismiss:()->Unit,onSave:(Installment,Int)->Unit){
    val zone=ZoneId.systemDefault();var title by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var date by remember{mutableStateOf(DateUtil.plusJalaliMonths(LocalDate.now(zone),1))};var reminder by remember{mutableStateOf("1")};var count by remember{mutableStateOf("1")};var everyMonths by remember{mutableStateOf("1")};var accountId by remember{mutableStateOf<Long?>(accounts.firstOrNull()?.id)};var personId by remember{mutableStateOf<Long?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("برنامه اقساط")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(title,{title=it},label={Text("عنوان قسط / قرارداد")});OutlinedTextField(amount,{amount=it},label={Text("مبلغ هر قسط (ریال)")});DateButton("اولین سررسید",date,Modifier.fillMaxWidth()){date=it}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(count,{count=it},label={Text("تعداد اقساط")},modifier=Modifier.weight(1f));OutlinedTextField(everyMonths,{everyMonths=it},label={Text("فاصله (ماه)")},modifier=Modifier.weight(1f))}
        SimpleSelectorField("حساب پرداخت",accounts.map{it.id to it.name},accountId){accountId=it};SimpleSelectorField("طرف حساب (اختیاری)",people.map{it.id to it.name},personId){personId=it};OutlinedTextField(reminder,{reminder=it},label={Text("چند روز قبل یادآوری شود؟")})
    }},confirmButton={Button(onClick={onSave(Installment(title=title.trim(),amount=Money.parse(amount),dueAt=date.atStartOfDay(zone).toInstant().toEpochMilli(),accountId=accountId,personId=personId,repeatUnit="ماه",repeatEvery=everyMonths.toIntOrNull()?.coerceIn(1,24)?:1,reminderDaysBefore=reminder.toIntOrNull()?.coerceIn(0,30)?:1),count.toIntOrNull()?.coerceIn(1,120)?:1)},enabled=title.isNotBlank()&&Money.parse(amount)>0){Text("ساخت برنامه")}},dismissButton={TextButton(onClick=onDismiss){Text("لغو")}})
}

@Composable
internal fun SimpleSelectorField(label:String,options:List<Pair<Long,String>>,selectedId:Long?,onSelect:(Long?)->Unit){var open by remember{mutableStateOf(false)};Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){Text(options.firstOrNull{it.first==selectedId}?.second?:label,modifier=Modifier.weight(1f));Icon(Icons.Rounded.ArrowDropDown,null)};DropdownMenu(open,{open=false}){DropdownMenuItem({Text("هیچ‌کدام")},{onSelect(null);open=false});options.forEach{(id,name)->DropdownMenuItem({Text(name)},{onSelect(id);open=false})}}}}

@Composable
fun AddTransactionDialog(
    accounts:List<Account>, people:List<Person>, categories:List<Category>,
    onDismiss:()->Unit, onSave:(FinanceTransaction,List<SplitDraft>)->Unit,
    initial:FinanceTransaction?=null, initialSplits:List<SplitDraft> = emptyList(), busy:Boolean=false
) {
    val zone=ZoneId.systemDefault()
    val originalTime=remember(initial?.id) { initial?.let { java.time.Instant.ofEpochMilli(it.occurredAt).atZone(zone) } }
    var amount by remember(initial?.id){mutableStateOf(initial?.amount?.toString().orEmpty())}
    var type by remember(initial?.id){mutableStateOf(initial?.type ?: TransactionType.EXPENSE)}
    var txDate by remember(initial?.id){mutableStateOf(originalTime?.toLocalDate() ?: LocalDate.now(zone))}
    var reason by remember(initial?.id){mutableStateOf(initial?.reason.orEmpty())}
    var accountId by remember(initial?.id){mutableStateOf(if(initial!=null) initial.accountId else accounts.firstOrNull()?.id)}
    var categoryId by remember(initial?.id){mutableStateOf(initial?.categoryId)}
    var personId by remember(initial?.id){mutableStateOf(initial?.personId)}
    var split by remember(initial?.id){mutableStateOf(initialSplits.isNotEmpty())}
    var note by remember(initial?.id){mutableStateOf(initial?.note.orEmpty())}
    val splitAmounts=remember(initial?.id){mutableStateMapOf<Long,String>().apply { initialSplits.forEach { put(it.personId,it.amount.toString()) } }}
    val splitRelations=remember(initial?.id){mutableStateMapOf<Long,SplitRelation>().apply { initialSplits.forEach { put(it.personId,it.relation) } }}
    val allowedCats=categories.filter{it.type==type}
    LaunchedEffect(type) {
        // Preserve the original category on opening, including an explicit empty category.
        if(categoryId!=null && allowedCats.none{it.id==categoryId}) categoryId=null
        if(initial==null && categoryId==null) categoryId=allowedCats.firstOrNull()?.id
    }
    val valid=Money.parse(amount)>0 && (!split || (splitAmounts.isNotEmpty() && splitAmounts.values.all{Money.parse(it)>0}))
    Dialog(onDismissRequest={if(!busy)onDismiss()}) {
        Surface(shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth().heightIn(max=760.dp)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(if(initial==null)"ثبت تراکنش" else "ویرایش تراکنش",fontSize=22.sp,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f))
                    IconButton(onClick=onDismiss,enabled=!busy){Icon(Icons.Rounded.Close,if(initial==null)"بستن ثبت تراکنش" else "بستن ویرایش تراکنش")}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(type==TransactionType.EXPENSE,{type=TransactionType.EXPENSE},{Text("هزینه")})
                        FilterChip(type==TransactionType.INCOME,{type=TransactionType.INCOME},{Text("درآمد")})
                    }
                    OutlinedTextField(amount,{amount=it},label={Text("مبلغ (ریال)")},modifier=Modifier.fillMaxWidth(),singleLine=true,textStyle=LocalTextStyle.current.copy(fontSize=22.sp,fontWeight=FontWeight.Bold))
                    OutlinedTextField(reason,{reason=it},label={Text("علت / شرح")},modifier=Modifier.fillMaxWidth())
                    DateButton("تاریخ",txDate,Modifier.fillMaxWidth()){txDate=it}
                    SimpleSelectorField("حساب (اختیاری)",accounts.map{it.id to it.name},accountId){accountId=it}
                    SimpleSelectorField("دسته‌بندی",allowedCats.map{it.id to it.name},categoryId){categoryId=it}
                    if(!split) SimpleSelectorField("طرف حساب (اختیاری)",people.map{it.id to it.name},personId){personId=it}
                    Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)){Text("ثبت سهم اشخاص",fontWeight=FontWeight.Bold);Text("اثر سهم‌ها در طلب و بدهی ثبت می‌شود",fontSize=11.sp)}
                            Switch(split,{split=it;if(it)personId=null})
                        }
                    }
                    if(split) {
                        if(people.isEmpty()) Text("ابتدا شخص را در تب اشخاص تعریف کنید",fontSize=12.sp)
                        people.forEach { p ->
                            val selected=splitAmounts.containsKey(p.id)
                            Surface(shape=RoundedCornerShape(18.dp),color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Checkbox(selected,{checked->if(checked){splitAmounts[p.id]="";splitRelations[p.id]=SplitRelation.OWES_ME}else{splitAmounts.remove(p.id);splitRelations.remove(p.id)}})
                                        Text(p.name,fontWeight=FontWeight.Bold)
                                    }
                                    if(selected) {
                                        OutlinedTextField(splitAmounts[p.id].orEmpty(),{splitAmounts[p.id]=it},label={Text("سهم ${p.name} (ریال)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                            FilterChip(splitRelations[p.id]!=SplitRelation.I_OWE,{splitRelations[p.id]=SplitRelation.OWES_ME},{Text("به من بدهکار")})
                                            FilterChip(splitRelations[p.id]==SplitRelation.I_OWE,{splitRelations[p.id]=SplitRelation.I_OWE},{Text("من بدهکارم")})
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(note,{note=it},label={Text("یادداشت (اختیاری)")},modifier=Modifier.fillMaxWidth())
                    if(initial!=null) Text("مانده اعلام‌شده بانک با ویرایش گردش‌های قبل از آخرین مانده تغییر نمی‌کند. سهم اشخاص و گردش‌های بعد از آن اصلاح می‌شوند.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick={
                    // Keep the exact original instant when the date is unchanged, especially for SMS snapshots.
                    val occurredAt=if(originalTime?.toLocalDate()==txDate) initial!!.occurredAt else txDate.atTime(originalTime?.toLocalTime() ?: java.time.LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
                    val tx=FinanceTransaction(id=initial?.id ?: 0,amount=Money.parse(amount),type=type,occurredAt=occurredAt,reason=reason.trim().ifBlank{if(type==TransactionType.EXPENSE)"هزینه" else "درآمد"},accountId=accountId,categoryId=categoryId,personId=if(split)null else personId,source=initial?.source ?: TransactionSource.MANUAL,note=note.trim(),externalKey=initial?.externalKey.orEmpty())
                    val drafts=if(!split)emptyList() else splitAmounts.map{(pid,a)->SplitDraft(pid,Money.parse(a),splitRelations[pid] ?: SplitRelation.OWES_ME)}
                    onSave(tx,drafts)
                },enabled=valid&&!busy,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(18.dp)) {
                    Text(if(busy)"در حال ذخیره…" else if(initial==null)"ثبت نهایی" else "ذخیره تغییرات",fontWeight=FontWeight.Bold)
                }
            }
        }
    }
}
