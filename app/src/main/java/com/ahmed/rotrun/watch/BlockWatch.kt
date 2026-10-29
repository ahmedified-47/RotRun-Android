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
import com.ahmed.rotrun.InterventionActivity
import com.ahmed.rotrun.R
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.data.UsageReader
import com.ahmed.rotrun.logic.InterventionCopy
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
    private val shownAt = HashMap<String, Long>()
    private var lastForeground: String? = null
    private var overlay: View? = null
    private var shownFor: String? = null
    private var lastFocusWidget = 0L
    private val tick = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground("RotRun is watching the apps you chose.")
        handler.removeCallbacks(tick)
        handler.post {
            if (RotRunGraph.ready()) RotRunGraph.repo.finishLaunch()
            tick.run()
        }
        return START_STICKY
    }

    override fun onDestroy() {
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
        if (BlockAccessibilityService.connected && BlockAccessibilityService.covering) {
            hideOverlay()
            return
        }
        val packageName = UsageReader.foreground(this) ?: run {
            hideOverlay()
            return
        }
        val blocked = repo.isBlocked(packageName, repo.safetyPackages())
        if (packageName != lastForeground) {
            lastForeground = packageName
            Log.i(ShieldOverlay.TAG, "fg=$packageName blocked=$blocked overlay=${Settings.canDrawOverlays(this)}")
        }
        if (!blocked) {
            hideOverlay()
            return
        }
        val label = UsageReader.label(this, packageName)
        if (Settings.canDrawOverlays(this)) {
            if (shownFor != packageName || overlay == null) {
                hideOverlay()
                overlay = ShieldOverlay.show(
                    context = this,
                    type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    packageName = packageName,
                    label = label,
                    onAsk = { openAsk(packageName, label) },
                )
                shownFor = if (overlay == null) null else packageName
            }
            return
        }
        if (now - (shownAt[packageName] ?: 0L) < 20_000) return
        shownAt[packageName] = now
        val copy = InterventionCopy.forPackage(packageName, label)
        val open = Intent(this, InterventionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(InterventionActivity.EXTRA_APP, label)
            putExtra(InterventionActivity.EXTRA_PACKAGE, packageName)
            putExtra(InterventionActivity.EXTRA_MODE, "shield")
        }
        val pending = PendingIntent.getActivity(
            this,
            packageName.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        getSystemService(NotificationManager::class.java).notify(ALERT_ID, buildNotification(copy.headline, pending))
    }

    private fun openAsk(packageName: String, label: String) {
        hideOverlay()
        if (!RotRunGraph.ready()) return
        val mode = RotRunGraph.repo.pickStyle() ?: "standard"
        val open = Intent(this, InterventionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(InterventionActivity.EXTRA_APP, label)
            putExtra(InterventionActivity.EXTRA_PACKAGE, packageName)
            putExtra(InterventionActivity.EXTRA_MODE, mode)
        }
        runCatching { startActivity(open) }
    }

    private fun hideOverlay() {
        ShieldOverlay.hide(this, overlay)
        overlay = null
        shownFor = null
    }

    private fun buildNotification(text: String, tap: PendingIntent? = null): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rot)
            .setContentTitle("Your Brain")
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
        private const val ALERT_ID = 43
    }
}
