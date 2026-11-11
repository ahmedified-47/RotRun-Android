package com.ahmed.rotrun.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.ahmed.rotrun.R
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.data.UsageReader
import com.ahmed.rotrun.widget.FocusWidgetProvider

object WatchController {
    fun sync(context: Context, state: SavedState) {
        val watching = state.focus != null || state.blockers.any { it.apps.isNotEmpty() } || state.appLists.any { it.apps.isNotEmpty() }
        val intent = Intent(context, BlockWatchService::class.java)
        if (watching) {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        } else {
            context.stopService(intent)
        }
    }
}

class BlockWatchService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastForeground: String? = null
    private var overlay: View? = null
    private var shownFor: String? = null
    private var overlayShownAt = 0L
    private var showingChat = false
    private var lastFocusWidget = 0L
    private val tick = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        live = this
        startInForeground("RotRun is watching the apps you chose.")
        handler.removeCallbacks(tick)
        handler.post {
            if (RotRunGraph.ready()) RotRunGraph.repo.finishLaunch()
            tick.run()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (live === this) live = null
        handler.removeCallbacks(tick)
        hideOverlay()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = Intent(applicationContext, BlockWatchService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) applicationContext.startForegroundService(restart)
            else applicationContext.startService(restart)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun startInForeground(text: String = "RotRun is watching the apps you chose.") {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun checkForeground() {
        if (!RotRunGraph.ready()) return
        val repo = RotRunGraph.repo
        repo.maybeExpireFocus()
        val focus = repo.state.value.focus
        val watching = if (focus == null) {
            "RotRun is watching the apps you chose."
        } else {
            val elapsed = System.currentTimeMillis() - focus.startedAt
            val left = focus.targetMinutes?.let { (it * 60_000L - elapsed).coerceAtLeast(0) }
            if (left == null) "Focus is running" else "Focus · ${left / 60_000}m left"
        }
        val now = System.currentTimeMillis()
        if (focus != null && now - lastFocusWidget > 15_000) {
            lastFocusWidget = now
            FocusWidgetProvider.updateAll(this)
        }
        startInForeground(watching)
        if (AskLaunchPolicy.leaveOverlayUp(AskBrainLaunch.launching)) return
        if (!AskLaunchPolicy.showShield(ShieldSession.chatPackage() != null)) {
            hideOverlay()
            return
        }
        if (BlockAccessibilityService.connected) {
            hideOverlay()
            return
        }
        val reading = UsageReader.foregroundResume(this)
        val safety = repo.safetyPackages()
        val choice = ForegroundGate.coverChoice(
            reported = reading?.packageName,
            windowsFrontToBack = emptyList(),
            usagePackage = reading?.packageName,
            usageResumedAt = reading?.atMillis ?: 0L,
            shownFor = shownFor,
            shownAt = overlayShownAt,
            ownPackage = packageName,
            safety = safety,
            blocked = { repo.isBlocked(it, safety) },
            windowStateChanged = false,
        )
        when (choice.step) {
            ShieldStep.Leave, ShieldStep.Keep -> return
            ShieldStep.Hide -> {
                hideOverlay()
                return
            }
            ShieldStep.Show -> Unit
        }
        val target = choice.packageName ?: return
        if (ShieldSession.openedInto(target)) {
            hideOverlay()
            if (!repo.isBlocked(target, repo.safetyPackages())) ShieldSession.release()
            return
        }
        val blocked = repo.isBlocked(target, repo.safetyPackages())
        if (target != lastForeground) {
            lastForeground = target
            Log.i(ShieldOverlay.TAG, "fg=$target blocked=$blocked overlay=${Settings.canDrawOverlays(this)}")
        }
        if (!blocked) {
            hideOverlay()
            return
        }
        val label = UsageReader.label(this, target)
        if (Settings.canDrawOverlays(this)) {
            val ask = repo.askIntervention.value
            val wantChat = ask?.packageName == target
            if (shownFor != target || overlay == null || showingChat != wantChat) {
                hideOverlay()
                overlay = ShieldOverlay.cover(
                    context = this,
                    type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    packageName = target,
                    label = label,
                    ask = if (wantChat) ask else null,
                    onAsk = { openAsk(target, label) },
                    onNah = {
                        repo.clearAskIntervention()
                        shownFor = null
                        checkForeground()
                    },
                    onUnlock = { minutes ->
                        if (!repo.grantUnlock(minutes)) {
                            repo.toast("Unlock waits until Focus ends")
                        } else {
                            ShieldSession.markOpened(target)
                            repo.clearAskIntervention()
                            hideOverlay()
                            packageManager.getLaunchIntentForPackage(target)?.let { launch ->
                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { startActivity(launch) }
                            }
                        }
                    },
                )
                showingChat = wantChat && overlay != null
                shownFor = if (overlay == null) null else target
                if (shownFor != null) overlayShownAt = System.currentTimeMillis()
            }
        }
        // No draw-over permission here. The accessibility service covers the blocked app.
        // An activity would be a second RotRun card in the switcher, beside that app.
    }

    private fun openAsk(packageName: String, label: String) {
        if (!RotRunGraph.ready()) return
        RotRunGraph.repo.requestAskIntervention(packageName, label)
        shownFor = null
        checkForeground()
    }

    private fun hideOverlay() {
        ShieldOverlay.hide(this, overlay)
        overlay = null
        shownFor = null
        overlayShownAt = 0L
        showingChat = false
    }

    private fun buildNotification(text: String, tap: PendingIntent? = null): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rot)
            .setContentTitle("RotRun")
            .setContentText(text)
            .setContentIntent(tap)
            .setOngoing(tap == null)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "RotRun blocking", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL = "rotrun.watch"
        private const val NOTIFICATION_ID = 42

        @Volatile
        private var live: BlockWatchService? = null

        fun dismiss() {
            live?.hideOverlay()
        }
    }
}
