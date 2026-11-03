package ir.hesabyar.finance.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.hesabyar.finance.data.FinanceRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        FinanceRepository(context).installments(openOnly = true).forEach { InstallmentScheduler.schedule(context, it) }
    }
}
