package ir.hesabyar.finance.sms

import android.content.Context
import android.provider.Telephony
import ir.hesabyar.finance.data.FinanceRepository

object SmsImporter {
    data class Result(val scanned:Int,val changed:Int,val unrecognized:Int)
    fun custom(context:Context):Map<String,String> = context.getSharedPreferences("sms_banks",Context.MODE_PRIVATE).all.mapValues{it.value.toString()}
    fun saveSender(context:Context,sender:String,bank:String) {
        require(sender.isNotBlank() && bank.isNotBlank())
        context.getSharedPreferences("sms_banks",Context.MODE_PRIVATE).edit().putString(BankRegistry.senderKey(sender),bank.trim()).apply()
    }
    fun removeSender(context:Context,sender:String) { context.getSharedPreferences("sms_banks",Context.MODE_PRIVATE).edit().remove(sender).apply() }
    fun import(context:Context,repo:FinanceRepository,from:Long,to:Long,latestOnly:Boolean):Result {
        require(from<=to) { "تاریخ شروع باید قبل از پایان باشد" }
        var scanned=0;var changed=0;var unknown=0
        val seen=mutableSetOf<String>();val map=custom(context)
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,arrayOf("address","body","date"),"date>=? AND date<=?",arrayOf(from.toString(),to.toString()),"date DESC, _id DESC")?.use { c ->
            while(c.moveToNext()) {
                scanned++
                val p=SmsParser.parse(c.getString(0).orEmpty(),c.getString(1).orEmpty(),c.getLong(2),map)
                if(p==null) { if(Regex("برداشت|واریز|موجودی|مانده").containsMatchIn(c.getString(1).orEmpty())) unknown++;continue }
                if(latestOnly && (p.balanceRial==null || !seen.add(p.bankName+"|"+p.accountIdentity))) continue
                if(repo.acceptSms(p,latestOnly)) changed++
            }
        } ?: error("خواندن پیامک‌ها ممکن نشد")
        return Result(scanned,changed,unknown)
    }
}
