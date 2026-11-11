package com.ahmed.rotrun.watch

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.data.UsageReader

/** Foreground block. Window-state changes draw the Rot shield over the blocked app. */
class BlockAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var shownFor: String? = null
    private var shownAt = 0L
    private var adding = false
    private val poll = object : Runnable {
        override fun run() {
            if (!adding && !AskLaunchPolicy.leaveOverlayUp(AskBrainLaunch.launching)) {
                if (!AskLaunchPolicy.showShield(ShieldSession.chatPackage() != null)) {
                    hide()
                } else if (RotRunGraph.ready()) {
                    val reading = UsageReader.foregroundResume(this@BlockAccessibilityService)
                    route(reading?.packageName, reading?.packageName, reading?.atMillis ?: 0L, windowStateChanged = false)
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
        if (adding) return
        val stateChanged = event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val windowsChanged = event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!stateChanged && !windowsChanged) return
        if (AskLaunchPolicy.leaveOverlayUp(AskBrainLaunch.launching)) return
        if (!AskLaunchPolicy.showShield(ShieldSession.chatPackage() != null)) {
            hide()
            return
        }
        val reported = event?.packageName?.toString()?.takeIf { it.isNotBlank() }
        val reading = if (RotRunGraph.ready()) UsageReader.foregroundResume(this) else null
        route(reported, reading?.packageName, reading?.atMillis ?: 0L, windowStateChanged = stateChanged)
    }

    /**
     * The event package is often the launcher, System UI, or ColorOS while Calendar
     * is still the app in front. The window list is what decides. A window does not
     * have to be focused: ColorOS often leaves that flag off.
     */
    private fun route(reported: String?, usagePackage: String?, usageAt: Long, windowStateChanged: Boolean) {
        if (!RotRunGraph.ready() || adding) return
        val repo = RotRunGraph.repo
        val safety = repo.safetyPackages()
        val ordered = ForegroundGate.orderedPackages(seenWindows(), packageName, safety)
        val choice = ForegroundGate.coverChoice(
            reported = reported,
            windowsFrontToBack = ordered,
            usagePackage = usagePackage,
            usageResumedAt = usageAt,
            shownFor = shownFor,
            shownAt = shownAt,
            ownPackage = packageName,
            safety = safety,
            blocked = { repo.isBlocked(it, safety) },
            windowStateChanged = windowStateChanged,
        )
        when (choice.step) {
            ShieldStep.Show -> choice.packageName?.let(::consider)
            ShieldStep.Hide -> hide()
            ShieldStep.Keep, ShieldStep.Leave -> Unit
        }
    }

    private fun seenWindows(): List<Pair<String, Boolean>> {
        val listed = runCatching { windows }.getOrNull()
        if (listed != null) {
            try {
                val apps = listed.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                val names = apps.mapNotNull { window ->
                    val root = runCatching { window.root }.getOrNull()
                    try {
                        val name = root?.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        name to (window.isFocused || window.isActive)
                    } finally {
                        runCatching { root?.recycle() }
                    }
                }
                if (names.isNotEmpty()) return names
            } finally {
                listed.forEach { runCatching { it.recycle() } }
            }
        }
        val root = rootInActiveWindow ?: return emptyList()
        return try {
            val name = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return emptyList()
            listOf(name to true)
        } finally {
            runCatching { root.recycle() }
        }
    }

    private fun consider(packageName: String) {
        if (adding || !RotRunGraph.ready()) return
        adding = true
        try {
            considerNow(packageName)
        } finally {
            adding = false
        }
    }

    private fun considerNow(packageName: String) {
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
