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
import androidx.core.app.NotificationCompat
import com.ahmed.rotrun.InterventionActivity
import com.ahmed.rotrun.R
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.data.UsageReader
import com.ahmed.rotrun.logic.InterventionCopy

object WatchController {
    fun sync(context: Context, state: SavedState) {
        val watching = state.focus != null || brickHasApps(state)
        val intent = Intent(context, BlockWatchService::class.java)
        if (watching) {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        } else {
            context.stopService(intent)
        }
    }

    private fun brickHasApps(state: SavedState): Boolean {
        val brick = state.blockers.firstOrNull { it.id == "brick" }
        if (brick != null && brick.apps.isNotEmpty()) return true
        val list = state.appLists.firstOrNull { it.id == "list.brainmelt" }
        return list?.apps?.isNotEmpty() == true
    }
}

class BlockWatchService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val shownAt = HashMap<String, Long>()
    private val tick = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = buildNotification("RotRun is watching the apps you chose.")
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
        val packageName = UsageReader.foreground(this) ?: return
        if (!repo.isBlocked(packageName, repo.safetyPackages())) return
        val now = System.currentTimeMillis()
        if (now - (shownAt[packageName] ?: 0L) < 20_000) return
        shownAt[packageName] = now
        val label = UsageReader.label(this, packageName)
        val copy = InterventionCopy.forApp(label)
        val open = Intent(this, InterventionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(InterventionActivity.EXTRA_APP, label)
            putExtra(InterventionActivity.EXTRA_MODE, "shield")
        }
        val pending = PendingIntent.getActivity(
            this,
            packageName.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            ALERT_ID,
            buildNotification(copy.headline, pending),
        )
        runCatching { startActivity(open) }
    }

    private fun buildNotification(text: String, tap: PendingIntent? = null): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_brain)
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
