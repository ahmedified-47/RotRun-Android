package com.ahmed.rotrun.watch

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.ahmed.rotrun.InterventionActivity
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.data.UsageReader

/** Foreground block. Window-state changes draw the Rot shield over the blocked app. */
class BlockAccessibilityService : AccessibilityService() {
    private var overlay: View? = null
    private var shownFor: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        val current = UsageReader.foreground(this) ?: return
        consider(current)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> event.packageName?.toString()
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> rootInActiveWindow?.packageName?.toString()
            else -> return
        } ?: return
        consider(packageName)
    }

    private fun consider(packageName: String) {
        if (!RotRunGraph.ready()) return
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
        connected = false
        hide()
        super.onDestroy()
    }

    private fun openAsk(packageName: String, label: String) {
        hide()
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

    private fun hide() {
        ShieldOverlay.hide(this, overlay)
        overlay = null
        shownFor = null
        covering = false
    }

    companion object {
        @Volatile
        var connected: Boolean = false

        @Volatile
        var covering: Boolean = false
    }
}
