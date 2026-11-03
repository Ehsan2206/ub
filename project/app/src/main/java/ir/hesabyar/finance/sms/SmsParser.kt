package ir.hesabyar.finance.sms

import ir.hesabyar.finance.data.TransactionType
import ir.hesabyar.finance.util.normalizeDigits
import java.security.MessageDigest

data class ParsedSms(
    val amountRial: Long?, val type: TransactionType?, val reason: String,
    val accountLast4: String, val externalKey: String, val bankName: String,
    val accountIdentity: String, val balanceRial: Long?, val timestamp: Long
)

object SmsParser {
    private const val NUMBER = "[0-9]+(?:[,٬٫.][0-9]{3})*"
    private const val UNIT = "ریال|تومان|تومن|rial|irr|toman"
    private val balancePattern=Regex("(?:مانده|موجودی|باقیمانده|باقی مانده|balance)(?:[ \\t]+(?:حساب|قابل برداشت|کل|سپرده))?[ \\t]*[:：=]?[ \\t]*(-?$NUMBER)[ \\t]*($UNIT)?",RegexOption.IGNORE_CASE)
    private val operation=Regex("(?:برداشت|خرید|پرداخت|بدهکار|کسر|واریز|واریزی|بستانکار|دریافت|انتقال به|انتقال از|deposit|withdrawal)",RegexOption.IGNORE_CASE)
    fun parse(sender: String, body: String, timestamp: Long, custom: Map<String,String> = emptyMap()): ParsedSms? {
        val text=body.normalizeDigits().replace('ي','ی').replace('ك','ک').replace('\u200c',' ')
        val bank=BankRegistry.detect(sender,text,custom) ?: return null
        if(Regex("رمز|otp|یکبار مصرف|یک بار مصرف|کد تایید|کد تأیید",RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        val balanceMatch=balancePattern.find(text)
        val balance=balanceMatch?.let { rial(it.groupValues[1],it.groupValues[2]) }
        val withoutBalance=balancePattern.replace(text, " ")
        val op=operation.find(withoutBalance)
        val typeByWord=op?.let { if(it.value.lowercase() in listOf("واریز","واریزی","بستانکار","دریافت","انتقال از","deposit")) TransactionType.INCOME else TransactionType.EXPENSE }
        val amountMatch=Regex("(?:مبلغ(?: تراکنش)?|برداشت|خرید|واریزی?|پرداخت|بستانکار|بدهکار|کسر|amount)\\s*[:：=]?\\s*($NUMBER)[ \\t]*($UNIT)?",RegexOption.IGNORE_CASE).find(withoutBalance)
            ?: Regex("($NUMBER)[ \\t]*($UNIT)",RegexOption.IGNORE_CASE).find(withoutBalance)
        val signedLine=Regex("(?m)^[ \t]*([+−-])($NUMBER)[ \t]*($UNIT)?[ \t]*$",RegexOption.IGNORE_CASE).find(withoutBalance)
        val type=typeByWord ?: signedLine?.let { if(it.groupValues[1]=="+") TransactionType.INCOME else TransactionType.EXPENSE }
        val amount=if(type==null) null else (amountMatch?.let { rial(it.groupValues[1],it.groupValues[2]) }
            ?: signedLine?.let { rial(it.groupValues[2],it.groupValues[3]) })?.takeIf { it>0 }
        if(balance==null && (amount==null || type==null)) return null
        // Prefer the account/deposit identifier. Retain the complete token to avoid merging equal suffixes.
        val token=Regex("(?:شماره[ \\t]*)?(?:حساب|سپرده|کارت)[ \\t]*[:：]?[ \\t]*([0-9*xX]+(?:[.\\-/][0-9*xX]+)*)").find(text)?.groupValues?.get(1)
        val sourceToken=token ?: Regex("(?:^|\\n)از[ \\t]*:[ \\t]*([0-9]+(?:[.\\-/][0-9]+)*)").find(text)?.groupValues?.get(1)
        val identity=sourceToken?.lowercase() ?: "sender:${BankRegistry.senderKey(sender)}"
        val last4=sourceToken?.filter { it.isDigit() }?.takeLast(4).orEmpty()
        val key=MessageDigest.getInstance("SHA-256").digest("$sender|$timestamp|$body".toByteArray()).joinToString(""){"%02x".format(it)}
        return ParsedSms(amount,type,if(type==TransactionType.INCOME)"واریز • $bank" else "برداشت • $bank",last4,key,bank,identity,balance,timestamp)
    }
    private fun rial(number: String, unit: String): Long? {
        val value=number.replace(Regex("[,٬٫.]"),"").toLongOrNull() ?: return null
        return runCatching { if(unit.lowercase() in listOf("تومان","تومن","toman")) Math.multiplyExact(value,10L) else value }.getOrNull()
    }
}
