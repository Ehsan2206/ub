package ir.hesabyar.finance.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ir.hesabyar.finance.data.FinanceRepository
import ir.hesabyar.finance.data.FinanceTransaction
import ir.hesabyar.finance.data.TransactionSource

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        Thread {
            try {
                val installedAt = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
                }.getOrDefault(System.currentTimeMillis())
                if (timestamp < installedAt) return@Thread

                val parsed = SmsParser.parse(sender, body, timestamp) ?: return@Thread
                val repo = FinanceRepository(context)
                if (repo.transactionByExternalKey(parsed.externalKey) != null) return@Thread
                val account = repo.resolveSmsAccount(parsed.accountLast4, sender)
                val id = repo.addTransaction(
                    FinanceTransaction(
                        amount = parsed.amountToman, type = parsed.type, occurredAt = timestamp, reason = parsed.reason,
                        accountId = account.id, categoryId = null, personId = null, source = TransactionSource.SMS,
                        note = "از پیامک $sender", externalKey = parsed.externalKey
                    )
                )
                if (id > 0) context.sendBroadcast(Intent("ir.hesabyar.finance.DATA_CHANGED").setPackage(context.packageName))
            } finally { pending.finish() }
        }.start()
    }
}
