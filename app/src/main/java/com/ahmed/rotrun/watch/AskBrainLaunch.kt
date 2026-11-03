package com.ahmed.rotrun.watch

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ahmed.rotrun.MainActivity
import com.ahmed.rotrun.R
import com.ahmed.rotrun.data.RotRunGraph

/**
 * Ask my brain dismisses the shield and brings RotRun forward with Talk open.
 * The blocked package stays off the shield until that chat closes.
 * A failed startActivity does not drop the hold: the full-screen intent is the fallback,
 * and releasing here would paint the shield again before that intent fires.
 */
object AskBrainLaunch {
    const val EXTRA_OPEN_CHAT = "open_chat"
    const val EXTRA_HELD_PACKAGE = "held_package"
    private const val CHANNEL = "rotrun.ask"
    private const val NOTIFICATION_ID = 44

    fun open(context: Context, packageName: String, label: String) {
        if (packageName.isNotBlank()) ShieldSession.holdForChat(packageName)
        BlockAccessibilityService.dismiss()
        BlockWatchService.dismiss()
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
            putExtra(EXTRA_OPEN_CHAT, true)
            putExtra(EXTRA_HELD_PACKAGE, packageName)
        }
        val options = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
            }.toBundle()
        } else {
            null
        }
        runCatching { context.startActivity(open, options) }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notify(context, label, pending)
        if (RotRunGraph.ready()) {
            RotRunGraph.repo.chargeAskBrain(System.currentTimeMillis())
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun notify(context: Context, label: String, tap: PendingIntent) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Ask my brain", NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val text = if (label.isBlank()) "Ask my brain" else "Ask my brain · $label"
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rot)
            .setContentTitle("Your Brain")
            .setContentText(text)
            .setContentIntent(tap)
            .setFullScreenIntent(tap, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
}
