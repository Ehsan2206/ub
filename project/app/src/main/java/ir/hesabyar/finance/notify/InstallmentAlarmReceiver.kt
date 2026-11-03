package ir.hesabyar.finance.notify

import android.Manifest
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ir.hesabyar.finance.MainActivity
import ir.hesabyar.finance.R
import ir.hesabyar.finance.util.Money

class InstallmentAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("installments", "یادآوری اقساط", NotificationManager.IMPORTANCE_DEFAULT))
        val title = intent.getStringExtra("title") ?: "قسط"
        val amount = intent.getLongExtra("amount", 0)
        val open = PendingIntent.getActivity(context, 90, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, "installments")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("یادآوری: $title")
            .setContentText("مبلغ ${Money.format(amount)} به سررسید نزدیک شده است.")
            .setAutoCancel(true).setContentIntent(open).build()
        manager.notify(intent.getLongExtra("id", 1).toInt(), n)
    }
}
