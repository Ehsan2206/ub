# HesabYar uses only Android/Jetpack APIs and local SQLite.
# Keep manifest-instantiated receivers.
-keep class ir.hesabyar.finance.sms.SmsReceiver { *; }
-keep class ir.hesabyar.finance.notify.** { *; }
