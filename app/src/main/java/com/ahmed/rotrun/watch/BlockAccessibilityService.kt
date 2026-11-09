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
    private var shownAt = 0L
    private val poll = object : Runnable {
        override fun run() {
            if (!AskLaunchPolicy.leaveOverlayUp(AskBrainLaunch.launching)) {
                if (!AskLaunchPolicy.showShield(ShieldSession.chatPackage() != null)) {
                    hide()
                } else if (RotRunGraph.ready()) {
                    val reading = UsageReader.foregroundResume(this@BlockAccessibilityService)
                    val repo = RotRunGraph.repo
                    val safety = repo.safetyPackages()
                    val blocked = reading != null && repo.isBlocked(reading.packageName, safety)
                    when (
                        ForegroundGate.onUsage(
                            packageName = reading?.packageName,
                            resumedAtMillis = reading?.atMillis ?: 0L,
                            shownFor = shownFor,
                            shownAtMillis = shownAt,
                            ownPackage = packageName,
                            safety = safety,
                            blocked = blocked,
                        )
                    ) {
                        ShieldStep.Show -> consider(reading!!.packageName)
                        ShieldStep.Hide -> hide()
                        ShieldStep.Keep, ShieldStep.Leave -> Unit
                    }
                }
            }
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
        val stateChanged = event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val windowsChanged = event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!stateChanged && !windowsChanged) return
        val packageName = event?.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: rootInActiveWindow?.packageName?.toString()
            ?: return
        if (AskLaunchPolicy.leaveOverlayUp(AskBrainLaunch.launching)) return
        if (!AskLaunchPolicy.showShield(ShieldSession.chatPackage() != null)) {
            hide()
            return
        }
        val safety = if (RotRunGraph.ready()) RotRunGraph.repo.safetyPackages() else emptySet()
        when (
            ForegroundGate.onWindow(
                eventPackage = packageName,
                windowStateChanged = stateChanged,
                shownFor = shownFor,
                ownPackage = this.packageName,
                safety = safety,
                eventBlocked = repoReadyBlocked(packageName),
            )
        ) {
            ShieldStep.Show -> consider(packageName)
            ShieldStep.Hide -> hide()
            ShieldStep.Keep, ShieldStep.Leave -> Unit
        }
    }

    private fun consider(packageName: String) {
        if (!RotRunGraph.ready()) return
        if (AskLaunchPolicy.leaveOverlayUp(AskBrainLaunch.launching)) return
        if (!AskLaunchPolicy.showShield(ShieldSession.chatPackage() != null)) {
            hide()
            return
        }
        if (ForegroundGate.ignore(packageName, this.packageName)) {
            if (AskLaunchPolicy.keepShownShield(shownFor, packageName, this.packageName)) {
                covering = overlay != null
                return
            }
            hide()
            return
        }
        if (ShieldSession.blocks(packageName)) {
            hide()
            if (ShieldSession.openedInto(packageName) && !repoReadyBlocked(packageName)) ShieldSession.release()
            return
        }
        if (AskLaunchPolicy.keepShownShield(shownFor, packageName, this.packageName)) {
            covering = overlay != null
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
        if (shownFor != null) shownAt = System.currentTimeMillis()
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
        AskBrainLaunch.open(this, packageName, label)
    }

    private fun hide() {
        ShieldOverlay.hide(this, overlay)
        overlay = null
        shownFor = null
        shownAt = 0L
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
