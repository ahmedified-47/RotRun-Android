package com.ahmed.rotrun.watch

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ahmed.rotrun.R
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.logic.Scoring

/**
 * Over-goal notes from Rot. Max 2 a day, at least 4 hours apart, no repeated body.
 * Does not ask for notification permission.
 */
object RotNudges {
    private const val prefsName = "rotrun.flags"
    private const val channel = "rotrun.open"
    private const val alarmId = 81

    fun consider(context: Context, repo: RotRunRepository) {
        val state = repo.state.value
        if (state.focus != null) return
        val usage = repo.usage.value
        val limitMinutes = state.goalHours * 60
        if (limitMinutes < 1) return
        val hasUsage = usage.todaySeconds > 0 || usage.pickups > 0 || usage.offenders.isNotEmpty()
        if (!hasUsage) return
        val over = usage.offenders
            .filter { it.seconds >= 60 && it.seconds / 60 >= limitMinutes }
            .maxByOrNull { it.seconds }
            ?: return
        val text = "${over.label} is over your limit (${Scoring.formatDuration(over.seconds)}). Stop rotting your brain."
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val day = Scoring.dayKey()
        val count = if (prefs.getString("nudge.day", "") == day) prefs.getInt("nudge.count", 0) else 0
        if (count >= 2) return
        val last = prefs.getLong("nudge.last", 0L)
        if (last > 0 && System.currentTimeMillis() - last < 4 * 60 * 60 * 1000L) return
        if (prefs.getString("nudge.body", "") == text) return
        if (prefs.getBoolean("nudge.pending", false)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        repo.backend.noteFromRot(text)
        prefs.edit()
            .putString("nudge.day", day)
            .putInt("nudge.count", count + 1)
            .putLong("nudge.last", System.currentTimeMillis())
            .putString("nudge.body", text)
            .putString("nudge.pendingBody", text)
            .putBoolean("nudge.pending", true)
            .apply()
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 15 * 60 * 1000L, pending(context))
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId,
            Intent(context, RotNudgeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

class RotNudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("rotrun.flags", Context.MODE_PRIVATE)
        val body = prefs.getString("nudge.pendingBody", null)
        prefs.edit().putBoolean("nudge.pending", false).remove("nudge.pendingBody").apply()
        if (body.isNullOrBlank()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("rotrun.open", "Open Rot", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(context, "rotrun.open")
            .setSmallIcon(R.drawable.ic_stat_rot)
            .setContentTitle("Rot")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()
        manager.notify(81, notification)
    }
}
