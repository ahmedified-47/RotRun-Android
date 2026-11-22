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
import com.ahmed.rotrun.logic.BrainCallPolicy
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
    const val EXTRA_HELD_LABEL = "held_label"

    /** New id so a muted copy of the old rotrun.ask channel is not reused. */
    private const val CHANNEL = "rotrun.ask.forward"
    private const val NOTIFICATION_ID = 44

    @Volatile
    var launching: Boolean = false
        private set

    fun open(context: Context, packageName: String, label: String, mode: String? = null) {
        if (!AskLaunchPolicy.acceptTap(launching)) return
        if (packageName.isNotBlank() && ShieldSession.chatPackage() == packageName) return
        launching = true
        try {
            if (packageName.isNotBlank()) ShieldSession.holdForChat(packageName, label)
            if (RotRunGraph.ready()) RotRunGraph.repo.requestAskIntervention(packageName, label, mode)
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
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun bringForward(context: Context, packageName: String, label: String): Boolean {
        val explicit = explicitIntent(context, packageName, label)
        val tap = pending(context, explicit)
        notify(context, label, tap, fullScreen = false)
        val started = start(context, explicit)
        if (started) return true
        if (AskLaunchPolicy.fullScreenAfterStart(startSucceeded = false)) {
            notify(context, label, tap, fullScreen = true)
        }
        return bringExistingTaskForward(context)
    }

    private fun launchIntent(context: Context, packageName: String, label: String): Intent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return (launch ?: explicitIntent(context, packageName, label)).apply {
            addFlags(AskLaunchPolicy.BRING_FORWARD_FLAGS)
            putExtra(EXTRA_OPEN_CHAT, true)
            putExtra(EXTRA_HELD_PACKAGE, packageName)
            putExtra(EXTRA_HELD_LABEL, label)
        }
    }

    private fun explicitIntent(context: Context, packageName: String, label: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(AskLaunchPolicy.BRING_FORWARD_FLAGS)
            putExtra(EXTRA_OPEN_CHAT, true)
            putExtra(EXTRA_HELD_PACKAGE, packageName)
            putExtra(EXTRA_HELD_LABEL, label)
        }

    /**
     * ALLOW_ALWAYS is API 36. On 34 and 35 that constant is not a mode the system
     * understands, so the start falls through and Ask my brain never comes forward.
     * Those versions use ALLOWED, which is the background-start mode they actually have.
     */
    @Suppress("DEPRECATION")
    private fun backgroundStartOptions(): ActivityOptions? {
        if (Build.VERSION.SDK_INT < 34) return null
        val mode = if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        return ActivityOptions.makeBasic().apply {
            setPendingIntentBackgroundActivityStartMode(mode)
        }
    }

    private fun start(context: Context, intent: Intent): Boolean {
        val options = backgroundStartOptions()?.toBundle()
        return runCatching { context.startActivity(intent, options) }.isSuccess
    }

    private fun pending(context: Context, intent: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val options = backgroundStartOptions()?.toBundle()
        return if (options != null) {
            PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags, options)
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

    private fun notify(context: Context, label: String, tap: PendingIntent, fullScreen: Boolean) {
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
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rot)
            .setContentTitle("RotRun")
            .setContentText(text)
            .setContentIntent(tap)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
        if (fullScreen && BrainCallPolicy.postFullScreenIntent(Build.VERSION.SDK_INT, fullScreenAllowed(context), alreadyPosted = false)) {
            notification.setFullScreenIntent(tap, true)
        }
        manager.notify(NOTIFICATION_ID, notification.build())
    }

    private fun fullScreenAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
}
