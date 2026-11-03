package ir.hesabyar.finance.sms

import ir.hesabyar.finance.data.TransactionType
import ir.hesabyar.finance.util.normalizeDigits
import java.security.MessageDigest

data class ParsedSms(
    val amountRial: Long?, val type: TransactionType?, val reason: String,
    val accountLast4: String, val externalKey: String, val bankName: String,
    val accountIdentity: String, val balanceRial: Long?, val timestamp: Long,
    val accountIdentities:List<String> = emptyList(),
    val internalAmount:Long = 0,
    val details:String = ""
)

object SmsParser {
    private const val NUMBER = "[0-9]+(?:[,٬٫.][0-9]{3})*"
    private const val UNIT = "ریال|تومان|تومن|rial|irr|toman"
    private val balancePattern=Regex("(?:مانده|موجودی|باقیمانده|باقی مانده|balance)(?:[ \\t]+(?:حساب|قابل برداشت|کل|سپرده))?[ \\t]*[:：=]?[ \\t]*(-?$NUMBER)[ \\t]*($UNIT)?",RegexOption.IGNORE_CASE)
    private val operation=Regex("(?:برداشت|خرید|پرداخت|بدهکار|کسر|واریز|واریزی|بستانکار|دریافت|انتقال به|انتقال از|deposit|withdrawal)",RegexOption.IGNORE_CASE)
    fun parse(sender: String, body: String, timestamp: Long, custom: Map<String,String> = emptyMap()): ParsedSms? {
        val text=body.normalizeDigits().replace('ي','ی').replace('ك','ک').replace('\u200c',' ').replace(Regex("[\\u064B-\\u065F\\u0670]"),"")
        val bank=BankRegistry.detect(sender,text,custom) ?: return null
        if(Regex("رمز|otp|یکبار مصرف|یک بار مصرف|کد تایید|کد تأیید",RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        val balance=balancePattern.find(text)?.let { rial(it.groupValues[1],it.groupValues[2]) }
        val withoutBalance=balancePattern.replace(text, " ")
        val op=operation.find(withoutBalance)
        val typeByWord=op?.let { if(it.value.lowercase() in listOf("واریز","واریزی","بستانکار","دریافت","انتقال از","deposit")) TransactionType.INCOME else TransactionType.EXPENSE }
        val amountMatch=Regex("(?:مبلغ(?: تراکنش)?|برداشت|خرید|واریزی?|پرداخت|بستانکار|بدهکار|کسر|amount)\\s*[:：=]?\\s*($NUMBER)[ \\t]*($UNIT)?",RegexOption.IGNORE_CASE).find(withoutBalance)
            ?: Regex("($NUMBER)[ \\t]*($UNIT)",RegexOption.IGNORE_CASE).find(withoutBalance)
        val signedLine=Regex("(?m)^[ \\t]*([+−-])($NUMBER)[ \\t]*($UNIT)?[ \\t]*$",RegexOption.IGNORE_CASE).find(withoutBalance)
        val bluIncome=if(bank=="بلوبانک سامان") Regex("($NUMBER)[ \\t]*($UNIT)[^\\n]{0,60}به حساب شما نشست").find(withoutBalance) else null
        val bluExpense=if(bank=="بلوبانک سامان") Regex("($NUMBER)[ \\t]*($UNIT)[^\\n]{0,60}از حساب شما پرید").find(withoutBalance) else null
        val bluMoney=bluIncome ?: bluExpense
        val type=if(bluIncome!=null)TransactionType.INCOME else if(bluExpense!=null)TransactionType.EXPENSE else typeByWord ?: signedLine?.let { if(it.groupValues[1]=="+") TransactionType.INCOME else TransactionType.EXPENSE }
        val primary=if(type==null) null else (bluMoney?.let { rial(it.groupValues[1],it.groupValues[2]) }
            ?: amountMatch?.let { rial(it.groupValues[1],it.groupValues[2]) }
            ?: signedLine?.let { rial(it.groupValues[2],it.groupValues[3]) })?.takeIf { it>0 }
        val rounded=if(bluExpense!=null) Regex("($NUMBER)[ \\t]*($UNIT)[ \\t]*(?:هم[ \\t]*)?رند شد").find(withoutBalance)?.let { rial(it.groupValues[1],it.groupValues[2]) } ?: 0L else 0L
        val amount=primary?.let { runCatching{Math.addExact(it,rounded)}.getOrNull() ?: return null }
        if(balance==null && (amount==null || type==null)) return null
        val ids=AccountIdentity.tokens(text)
        val identity=ids.firstOrNull() ?: AccountIdentity.UNIDENTIFIED
        val last4=ids.firstOrNull()?.filter { it.isDigit() }?.takeLast(4).orEmpty()
        val key=MessageDigest.getInstance("SHA-256").digest("$sender|$timestamp|$body".toByteArray()).joinToString(""){"%02x".format(it)}
        val reason=when { rounded>0 -> "برداشت رند • $bank";type==TransactionType.INCOME -> "واریز • $bank";else -> "برداشت • $bank" }
        val details=if(rounded>0) "پرداخت: $primary ریال؛ انتقال رند به باکس پس‌انداز: $rounded ریال؛ جمع کسر از حساب: $amount ریال" else ""
        return ParsedSms(amount,type,reason,last4,key,bank,identity,balance,timestamp,ids,rounded,details)
    }
    private fun rial(number: String, unit: String): Long? {
        val value=number.replace(Regex("[,٬٫.]"),"").toLongOrNull() ?: return null
        return runCatching { if(unit.lowercase() in listOf("تومان","تومن","toman")) Math.multiplyExact(value,10L) else value }.getOrNull()
    }
}
