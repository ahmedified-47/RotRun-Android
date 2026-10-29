package com.ahmed.rotrun.watch

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ahmed.rotrun.R
import com.ahmed.rotrun.data.SavedState
import java.util.Calendar

/** Opt-in, default off, one note a day at 19:00. Never asks for notification permission. */
object UpgradeReminders {
    private const val id = 77
    private const val channel = "rotrun.upgrade"

    fun sync(context: Context, state: SavedState) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pending(context)
        context.getSharedPreferences("rotrun.flags", Context.MODE_PRIVATE).edit()
            .putBoolean("upgrade", state.upgradeReminders && !state.premiumActive())
            .apply()
        if (!state.upgradeReminders || state.premiumActive()) {
            alarm.cancel(pending)
            manager.cancel(id)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channel, "Rot", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            android.content.Intent(context, UpgradeReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

class UpgradeReminderReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent?) {
        if (!context.getSharedPreferences("rotrun.flags", Context.MODE_PRIVATE).getBoolean("upgrade", false)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel("rotrun.upgrade") == null) {
            manager.createNotificationChannel(NotificationChannel("rotrun.upgrade", "Rot", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(context, "rotrun.upgrade")
            .setSmallIcon(R.drawable.ic_stat_rot)
            .setContentTitle("Rot")
            .setContentText("It's Rot. Free chats run out after three a day. Premium is how I stay with you.")
            .build()
        manager.notify(77, notification)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        alarm.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            PendingIntent.getBroadcast(
                context,
                77,
                android.content.Intent(context, UpgradeReminderReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }
}
