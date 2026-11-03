package com.ahmed.rotrun.watch

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.data.UsageReader

/** Foreground block. Window-state changes draw the Rot shield over the blocked app. */
class BlockAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var shownFor: String? = null
    private val poll = object : Runnable {
        override fun run() {
            val current = UsageReader.foreground(this@BlockAccessibilityService)
            if (!current.isNullOrBlank()) consider(current)
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        live = this
        connected = true
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val fromEvent = event?.packageName?.toString()?.takeIf { it.isNotBlank() }
        val packageName = when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> fromEvent ?: rootInActiveWindow?.packageName?.toString()
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> fromEvent ?: rootInActiveWindow?.packageName?.toString()
            else -> return
        } ?: return
        consider(packageName)
    }

    private fun consider(packageName: String) {
        if (!RotRunGraph.ready()) return
        if (ShieldSession.blocks(packageName)) {
            hide()
            if (ShieldSession.openedInto(packageName) && !repoReadyBlocked(packageName)) ShieldSession.release()
            return
        }
        val repo = RotRunGraph.repo
        if (!repo.isBlocked(packageName, repo.safetyPackages())) {
            hide()
            return
        }
        if (shownFor == packageName && overlay != null) {
            covering = true
            return
        }
        val label = UsageReader.label(this, packageName)
        hide()
        overlay = ShieldOverlay.show(
            context = this,
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            packageName = packageName,
            label = label,
            onAsk = { openAsk(packageName, label) },
        )
        shownFor = if (overlay == null) null else packageName
        covering = overlay != null
    }

    override fun onInterrupt() {
        hide()
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        if (live === this) live = null
        connected = false
        hide()
        super.onDestroy()
    }

    private fun repoReadyBlocked(packageName: String): Boolean {
        if (!RotRunGraph.ready()) return false
        val repo = RotRunGraph.repo
        return repo.isBlocked(packageName, repo.safetyPackages())
    }

    private fun openAsk(packageName: String, label: String) {
        hide()
        AskBrainLaunch.open(this, packageName, label)
    }

    private fun hide() {
        ShieldOverlay.hide(this, overlay)
        overlay = null
        shownFor = null
        covering = false
    }

    companion object {
        @Volatile
        private var live: BlockAccessibilityService? = null

        @Volatile
        var connected: Boolean = false

        @Volatile
        var covering: Boolean = false

        fun dismiss() {
            live?.hide()
        }
    }
}
