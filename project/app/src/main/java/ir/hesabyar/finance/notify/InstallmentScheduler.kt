package ir.hesabyar.finance.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ir.hesabyar.finance.data.Installment

object InstallmentScheduler {
    fun cancel(context: Context, id: Long) {
        val intent = Intent(context, InstallmentAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(context, id.toInt(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) { context.getSystemService(AlarmManager::class.java).cancel(pi); pi.cancel() }
    }

    fun schedule(context: Context, item: Installment) {
        if (item.id <= 0) return
        val alarm = context.getSystemService(AlarmManager::class.java)
        val trigger = (item.dueAt - item.reminderDaysBefore * 86_400_000L).coerceAtLeast(System.currentTimeMillis() + 5_000L)
        val intent = Intent(context, InstallmentAlarmReceiver::class.java).apply {
            putExtra("id", item.id); putExtra("title", item.title); putExtra("amount", item.amount); putExtra("dueAt", item.dueAt)
        }
        val pi = PendingIntent.getBroadcast(context, item.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
    }
}
