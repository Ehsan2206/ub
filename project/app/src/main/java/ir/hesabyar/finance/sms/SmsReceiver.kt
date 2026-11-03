package ir.hesabyar.finance.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ir.hesabyar.finance.data.FinanceRepository

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages=Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if(messages.isEmpty()) return
        val pending=goAsync()
        Thread {
            try {
                val sender=messages.first().originatingAddress.orEmpty()
                val body=messages.joinToString(""){it.messageBody.orEmpty()}
                val parsed=SmsParser.parse(sender,body,messages.first().timestampMillis,SmsImporter.custom(context))
                if(parsed!=null && FinanceRepository(context).acceptSms(parsed)) context.sendBroadcast(Intent("ir.hesabyar.finance.DATA_CHANGED").setPackage(context.packageName))
            } catch(e:Exception) {
                android.util.Log.w("HesabYar","SMS processing failed", e)
            } finally { pending.finish() }
        }.start()
    }
}
