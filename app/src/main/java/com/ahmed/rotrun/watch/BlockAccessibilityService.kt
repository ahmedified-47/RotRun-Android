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
    private var showingChat = false
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
        val coverLoose = coverDespiteArming()
        if (!repo.armingReady() && !coverLoose) {
            if (overlay != null || shownFor != null) GatePrompt.open(this)
            hide()
            return
        }
        if (coverLoose) GatePrompt.open(this)
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
            blocked = { if (coverLoose) repo.rulesBlocked(it, safety) else repo.isBlocked(it, safety) },
            windowStateChanged = windowStateChanged,
        )
        when (choice.step) {
            ShieldStep.Show -> choice.packageName?.let(::consider)
            ShieldStep.Hide -> hide()
            ShieldStep.Keep, ShieldStep.Leave -> {
                if (coverLoose && ordered.firstOrNull() == packageName) hide()
            }
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
        if (RotRunGraph.ready() && !RotRunGraph.repo.armingReady() && !coverDespiteArming()) {
            if (overlay != null || shownFor != null) GatePrompt.open(this)
            hide()
            return
        }
        if (coverDespiteArming()) GatePrompt.open(this)
        if (AskLaunchPolicy.leaveOverlayUp(AskBrainLaunch.launching)) return
        if (!AskLaunchPolicy.showShield(ShieldSession.chatPackage() != null)) {
            hide()
            return
        }
        if (ShieldSession.openedInto(packageName)) {
            hide()
            if (!repoReadyBlocked(packageName)) ShieldSession.release()
            return
        }
        if (ForegroundGate.ignore(packageName, this.packageName)) {
            if (coverDespiteArming() && packageName == this.packageName) {
                hide()
                return
            }
            if (AskLaunchPolicy.keepShownShield(shownFor, packageName, this.packageName) && stillBlocked(shownFor)) {
                covering = overlay != null
                return
            }
            hide()
            return
        }
        if (AskLaunchPolicy.keepShownShield(shownFor, packageName, this.packageName) && stillBlocked(shownFor)) {
            covering = overlay != null
            return
        }
        val repo = RotRunGraph.repo
        val safety = repo.safetyPackages()
        val blockedNow = if (coverDespiteArming()) repo.rulesBlocked(packageName, safety) else repo.isBlocked(packageName, safety)
        if (!blockedNow) {
            hide()
            return
        }
        val label = UsageReader.label(this, packageName)
        val ask = RotRunGraph.repo.askIntervention.value
        val wantChat = ask?.packageName == packageName
        if (shownFor == packageName && overlay != null && showingChat == wantChat) {
            covering = true
            return
        }
        hide()
        overlay = ShieldOverlay.cover(
            context = this,
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            packageName = packageName,
            label = label,
            ask = if (wantChat) ask else null,
            onAsk = { openAsk(packageName, label) },
            onNah = { RotRunGraph.repo.clearAskIntervention(); shownFor = null; considerNow(packageName) },
            onUnlock = { minutes -> unlock(packageName, minutes) },
        )
        showingChat = wantChat && overlay != null
        shownFor = if (overlay == null) null else packageName
        if (shownFor != null) shownAt = System.currentTimeMillis()
        covering = overlay != null
    }

    override fun onInterrupt() {
        hide()
    }

    override fun onDestroy() {
        val last = shownFor
        val wasUp = overlay != null || last != null
        handler.removeCallbacks(poll)
        if (live === this) live = null
        connected = false
        hide()
        if (wasUp && last != null && RotRunGraph.ready()) {
            GatePrompt.open(this)
            if (android.provider.Settings.canDrawOverlays(this)) {
                CoverHold.hold(last)
                WatchController.sync(this, RotRunGraph.repo.state.value, armed = false)
            }
        }
        super.onDestroy()
    }

    private fun coverDespiteArming(): Boolean {
        if (!RotRunGraph.ready() || RotRunGraph.repo.armingReady()) {
            CoverLoose.clear()
            return false
        }
        val access = com.ahmed.rotrun.data.DeviceAccessReader.read(this)
        val decision = com.ahmed.rotrun.logic.CoverRevoke.response(access.accessibility && connected, access.overlay)
        val shieldUp = overlay != null || shownFor != null
        if (decision == com.ahmed.rotrun.logic.CoverRevoke.COVER && shieldUp) CoverLoose.active = true
        if (decision != com.ahmed.rotrun.logic.CoverRevoke.COVER) {
            CoverLoose.clear()
            return false
        }
        return shieldUp || CoverLoose.active
    }

    private fun stillBlocked(packageName: String?): Boolean {
        if (packageName.isNullOrBlank() || !RotRunGraph.ready()) return false
        val repo = RotRunGraph.repo
        val safety = repo.safetyPackages()
        return if (coverDespiteArming()) repo.rulesBlocked(packageName, safety) else repo.isBlocked(packageName, safety)
    }

    private fun repoReadyBlocked(packageName: String): Boolean {
        if (!RotRunGraph.ready()) return false
        val repo = RotRunGraph.repo
        return repo.isBlocked(packageName, repo.safetyPackages())
    }

    private fun openAsk(packageName: String, label: String) {
        if (!RotRunGraph.ready()) return
        RotRunGraph.repo.requestAskIntervention(packageName, label)
        shownFor = null
        considerNow(packageName)
    }

    private fun unlock(packageName: String, minutes: Int) {
        val repo = RotRunGraph.repo
        if (!repo.grantUnlock(minutes)) {
            repo.toast("Unlock waits until Focus ends")
            return
        }
        ShieldSession.markOpened(packageName)
        repo.clearAskIntervention()
        hide()
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(launch) }
        }
    }

    private fun hide() {
        ShieldOverlay.hide(this, overlay)
        overlay = null
        shownFor = null
        shownAt = 0L
        showingChat = false
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

        fun recheck() {
            val service = live ?: return
            service.handler.post {
                if (!RotRunGraph.ready()) return@post
                val reading = UsageReader.foregroundResume(service)
                service.route(reading?.packageName, reading?.packageName, reading?.atMillis ?: 0L, windowStateChanged = false)
            }
        }
    }
}
