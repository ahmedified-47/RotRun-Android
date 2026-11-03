package com.ahmed.rotrun.watch

import android.app.ActivityManager
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
 * Ask my brain brings RotRun forward with Talk open and leaves the blocked package
 * unblocked until that chat closes.
 *
 * On ColorOS a plain startActivity from an accessibility overlay is dropped, and
 * dismissing the overlay first removes the background-start exemption. This posts a
 * high-priority full-screen intent, moves the existing RotRun task forward, and
 * starts the launcher activity while the shield is still up. The shield comes down
 * after that attempt. A failed start does not release the hold and the watchers do
 * not draw the shield again.
 */
object AskBrainLaunch {
    const val EXTRA_OPEN_CHAT = "open_chat"
    const val EXTRA_HELD_PACKAGE = "held_package"

    /** New id so a muted copy of the old rotrun.ask channel is not reused. */
    private const val CHANNEL = "rotrun.ask.forward"
    private const val NOTIFICATION_ID = 44

    @Volatile
    var launching: Boolean = false
        private set

    fun open(context: Context, packageName: String, label: String) {
        launching = true
        try {
            if (packageName.isNotBlank()) ShieldSession.holdForChat(packageName)
            val brought = bringForward(context, packageName, label)
            val decision = AskLaunchPolicy.shieldAfterAttempt(startFailed = !brought)
            if (decision.dismiss) {
                BlockAccessibilityService.dismiss()
                BlockWatchService.dismiss()
            }
            if (decision.releaseHold) ShieldSession.release()
        } finally {
            launching = false
        }
        if (RotRunGraph.ready()) {
            RotRunGraph.repo.chargeAskBrain(System.currentTimeMillis())
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun bringForward(context: Context, packageName: String, label: String): Boolean {
        val launcher = launchIntent(context, packageName)
        val explicit = explicitIntent(context, packageName)
        val pending = pending(context, launcher)
        notify(context, label, pending)
        val sent = runCatching { pending.send() }.isSuccess
        val moved = bringExistingTaskForward(context)
        val started = start(context, launcher) || start(context, explicit)
        return sent || moved || started
    }

    private fun launchIntent(context: Context, packageName: String): Intent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return (launch ?: explicitIntent(context, packageName)).apply {
            addFlags(AskLaunchPolicy.BRING_FORWARD_FLAGS)
            putExtra(EXTRA_OPEN_CHAT, true)
            putExtra(EXTRA_HELD_PACKAGE, packageName)
        }
    }

    private fun explicitIntent(context: Context, packageName: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(AskLaunchPolicy.BRING_FORWARD_FLAGS)
            putExtra(EXTRA_OPEN_CHAT, true)
            putExtra(EXTRA_HELD_PACKAGE, packageName)
        }

    private fun start(context: Context, intent: Intent): Boolean {
        val options = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
                )
            }.toBundle()
        } else {
            null
        }
        return runCatching { context.startActivity(intent, options) }.isSuccess
    }

    private fun pending(context: Context, intent: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= 34) {
            val options = ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
                )
            }
            PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags, options.toBundle())
        } else {
            PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags)
        }
    }

    /** Own-task move. This is not ActivityManager.moveTaskToFront, which needs a privileged permission. */
    private fun bringExistingTaskForward(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val tasks = runCatching { manager.appTasks }.getOrNull().orEmpty()
        if (tasks.isEmpty()) return false
        val own = if (Build.VERSION.SDK_INT >= 29) {
            tasks.firstOrNull { task ->
                val info = runCatching { task.taskInfo }.getOrNull() ?: return@firstOrNull false
                info.baseActivity?.packageName == context.packageName ||
                    info.topActivity?.packageName == context.packageName
            }
        } else {
            null
        } ?: tasks.first()
        return runCatching {
            own.moveToFront()
            true
        }.getOrDefault(false)
    }

    private fun notify(context: Context, label: String, tap: PendingIntent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL,
                "Ask my brain",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
        val text = if (label.isBlank()) "Ask my brain" else "Ask my brain · $label"
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rot)
            .setContentTitle("Your Brain")
            .setContentText(text)
            .setContentIntent(tap)
            .setFullScreenIntent(tap, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
