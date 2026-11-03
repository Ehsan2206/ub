package ir.hesabyar.finance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ir.hesabyar.finance.data.*
import ir.hesabyar.finance.sms.BankRegistry
import ir.hesabyar.finance.util.DateUtil
import ir.hesabyar.finance.util.Money
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue

@Composable
fun SmsImportDialog(busy:Boolean,rules:Map<String,String>,onDismiss:()->Unit,onImport:(Long,Long,Boolean)->Unit,onSaveSender:(String,String)->Unit,onRemoveSender:(String)->Unit) {
    var latest by remember { mutableStateOf(true) }
    var from by remember { mutableStateOf(DateUtil.plusJalaliMonths(LocalDate.now(),-1)) }
    var to by remember { mutableStateOf(LocalDate.now()) }
    var sender by remember { mutableStateOf("") }; var bank by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var rulesOpen by remember { mutableStateOf(false) }
    Dialog(onDismissRequest={if(!busy)onDismiss()}) {
        Surface(shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth().heightIn(max=760.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically){Text("پیامک‌های بانکی",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton(onClick=onDismiss,enabled=!busy){Icon(Icons.Rounded.Close,"بستن")}}
                Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("پردازش فقط روی گوشی انجام می‌شود. پیش از انتخاب «شروع»، پیامک‌های قبلی خوانده نمی‌شوند.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterChip(selected=latest,onClick={latest=true},label={Text("فقط آخرین مانده هر حساب")},leadingIcon={Icon(Icons.Rounded.AccountBalanceWallet,null)})
                    FilterChip(selected=!latest,onClick={latest=false},label={Text("تراکنش‌های یک بازه زمانی")},leadingIcon={Icon(Icons.Rounded.DateRange,null)})
                    if(latest) Text("آخرین پیامک دارای مانده برای هر حساب پیدا می‌شود؛ فقط حساب و موجودی آن ثبت می‌شود و تراکنش‌های گذشته وارد نمی‌شوند.",fontSize=12.sp)
                    else {
                        DateButton("از",from,Modifier.fillMaxWidth()){from=it}
                        DateButton("تا",to,Modifier.fillMaxWidth()){to=it}
                        Text("هر دو روز در بازه محاسبه می‌شوند. پیامک تکراری دوباره ثبت نمی‌شود. مانده پیامک، موجودی اعلام‌شده بانک است.",fontSize=12.sp)
                        if(from>to) Text("تاریخ پایان باید بعد از شروع باشد",color=MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick={rulesOpen=!rulesOpen}){Text(if(rulesOpen)"بستن مدیریت سرشماره‌ها" else "بانک یا صندوق شناسایی نشد؟")}
                    if(rulesOpen) {
                        Text("سرشماره دقیق صندوق یا بانک را همراه نام آن تعریف کنید. برای بانک‌های فهرست از همان نام استفاده کنید.",fontSize=12.sp)
                        OutlinedTextField(sender,{sender=it},label={Text("سرشماره / نام فرستنده")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(bank,{bank=it},label={Text("نام بانک یا صندوق")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        var menu by remember{mutableStateOf(false)}
                        Box{TextButton(onClick={menu=true}){Text("انتخاب نام بانک از فهرست")};DropdownMenu(menu,{menu=false},modifier=Modifier.heightIn(max=280.dp)){BankRegistry.banks.forEach{b->DropdownMenuItem(text={Text(b.name)},onClick={bank=b.name;menu=false})}}}
                        OutlinedButton(onClick={onSaveSender(sender,bank);sender="";bank=""},enabled=sender.isNotBlank()&&bank.isNotBlank()&&!busy,modifier=Modifier.fillMaxWidth()){Text("ذخیره سرشماره")}
                        rules.forEach{(s,b)->Row(verticalAlignment=Alignment.CenterVertically){Text("$s • $b",Modifier.weight(1f),fontSize=12.sp);IconButton(onClick={onRemoveSender(s)},enabled=!busy){Icon(Icons.Rounded.Delete,"حذف سرشماره")}}}
                        Text("فهرست داخلی شامل نام‌های بانکی و سرشماره‌های شناخته‌شده است؛ برای صندوق‌های محلی سرشماره اختصاصی را اضافه کنید.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(onClick={confirm=true},enabled=!busy&&(latest||from<=to),modifier=Modifier.fillMaxWidth().height(50.dp)){Text(if(busy)"در حال بررسی پیامک‌ها…" else "شروع بررسی")}
            }
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("ورود پیامک‌ها")},text={Text(if(latest)"آخرین مانده هر حساب از پیامک‌های گوشی استخراج و جایگزین مانده قبلی می‌شود. تراکنش‌های بعد از آن مانده همچنان محاسبه می‌شوند." else "فقط پیامک‌های ${DateUtil.formatLocalDate(from)} تا ${DateUtil.formatLocalDate(to)} بررسی می‌شوند و حساب‌های شناسایی‌شده خودکار اضافه می‌شوند.")},confirmButton={Button(onClick={confirm=false;val zone=ZoneId.systemDefault();onImport(if(latest)0 else from.atStartOfDay(zone).toInstant().toEpochMilli(),if(latest)System.currentTimeMillis() else to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1,latest)}){Text("شروع")}},dismissButton={TextButton(onClick={confirm=false}){Text("انصراف")}})
}

@Composable
fun PersonLedgerDialog(p:PersonBalance,entries:List<LedgerEntry>,onDismiss:()->Unit,onAdd:(Long,Long,String)->Unit,onDelete:(Long)->Unit) {
    var adding by remember { mutableStateOf(false) };var deleting by remember{mutableStateOf<LedgerEntry?>(null)}
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth().heightIn(max=760.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically){Text(p.person.name,Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.Bold);IconButton(onClick=onDismiss){Icon(Icons.Rounded.Close,"بستن")}}
                Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(18.dp)){Text(if(p.net>0)"طلب من" else if(p.net<0)"بدهی من" else "حساب تسویه است",fontSize=12.sp);Text(Money.format(p.net.absoluteValue),fontSize=23.sp,fontWeight=FontWeight.Bold)}}
                Button(onClick={adding=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.Add,null);Text("ثبت گردش شخص")}
                Text("ریز گردش",fontWeight=FontWeight.Bold)
                Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(entries.isEmpty()) Text("هنوز گردشی ثبت نشده است",fontSize=12.sp)
                    entries.forEach { e ->
                        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(e.note,fontWeight=FontWeight.Bold)
                            Text(DateUtil.formatDate(e.occurredAt),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment=Alignment.CenterVertically){Text((if(e.signed>0)"افزایش طلب / کاهش بدهی: " else "افزایش بدهی / کاهش طلب: ")+Money.format(e.signed.absoluteValue),Modifier.weight(1f),fontSize=12.sp);IconButton(onClick={deleting=e}){Icon(Icons.Rounded.DeleteOutline,"حذف گردش",tint=MaterialTheme.colorScheme.error)}}
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
    if(adding) AddLedgerDialog({adding=false}) { signed,date,note -> onAdd(signed,date,note);adding=false }
    deleting?.let{e->AlertDialog(onDismissRequest={deleting=null},title={Text("حذف گردش؟")},text={Text(if(e.transactionId!=null)"این گردش به تراکنش مالی متصل است. تراکنش و همه سهم‌های مرتبط آن نیز حذف می‌شوند." else "اثر این گردش از مانده شخص حذف می‌شود.")},confirmButton={Button(onClick={onDelete(e.id);deleting=null}){Text("حذف")}},dismissButton={TextButton(onClick={deleting=null}){Text("انصراف")}})}
}

@Composable
private fun AddLedgerDialog(onDismiss:()->Unit,onSave:(Long,Long,String)->Unit) {
    val labels=listOf("طلب از شخص","بدهی به شخص","دریافت تسویه از شخص","پرداخت تسویه به شخص")
    var mode by remember{mutableIntStateOf(0)};var amount by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var date by remember{mutableStateOf(LocalDate.now())}
    AlertDialog(onDismissRequest=onDismiss,title={Text("ثبت گردش شخص")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        labels.forEachIndexed{i,label->FilterChip(mode==i,{mode=i},label={Text(label)})}
        OutlinedTextField(amount,{amount=it},label={Text("مبلغ (ریال)")},singleLine=true)
        OutlinedTextField(note,{note=it},label={Text("شرح گردش")})
        DateButton("تاریخ",date,Modifier.fillMaxWidth()){date=it}
        Text("این ثبت فقط طلب و بدهی شخص را تغییر می‌دهد. برای تغییر موجودی بانک، تراکنش مالی را نیز ثبت کنید.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }},confirmButton={Button(onClick={val sign=if(mode==0||mode==3)1 else -1;onSave(Money.parse(amount)*sign,date.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),note.trim().ifBlank{labels[mode]})},enabled=Money.parse(amount)>0){Text("ثبت")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}
