package com.ahmed.rotrun.watch

import android.content.Intent

/**
 * ColorOS often rejects startActivity from an accessibility overlay once that overlay is gone.
 * The launch keeps the overlay up until the attempt finishes, then drops it.
 * A failed start does not release the chat hold and does not draw the shield again.
 */
object AskLaunchPolicy {
    const val BRING_FORWARD_FLAGS: Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_NO_ANIMATION

    /** While this is true the overlay stays, so the start still has a visible window. */
    fun leaveOverlayUp(launching: Boolean): Boolean = launching

    /**
     * A shield that is already up for a blocked app must stay that one view.
     * ColorOS sends window events for itself, System UI, and RotRun while the overlay is showing.
     * Rebuilding on those events is the flicker, and it eats the Ask my brain tap.
     */
    fun keepShownShield(shownFor: String?, eventPackage: String, ownPackage: String): Boolean {
        if (shownFor.isNullOrBlank()) return false
        if (eventPackage.isBlank() || eventPackage == shownFor || eventPackage == ownPackage) return true
        return transientWindow(eventPackage)
    }

    /**
     * Overlay processes, not apps the user can put on a block list.
     * A ColorOS, Oppo, or HeyTap package is a real app unless it is one of these.
     * Treating the whole prefix as chrome is what let Gallery, Music, and Calendar
     * stay on the list and never raise a shield.
     */
    fun transientWindow(packageName: String): Boolean {
        val name = packageName.lowercase()
        if (name == "android" || name.startsWith("com.android.systemui")) return true
        if (name.endsWith(".systemui")) return true
        if (isHomeLauncher(name)) return true
        return name in vendorOverlay
    }

    fun isHomeLauncher(packageName: String): Boolean {
        val name = packageName.lowercase()
        return name.endsWith(".launcher") || name.endsWith(".launcher3") || name.contains(".launcher.")
    }

    private val vendorOverlay = setOf(
        "com.coloros.oppoguardelf",
        "com.oplus.systemui",
        "com.coloros.systemui",
        "com.coloros.smartsidebar",
        "com.oplus.smartsidebar",
        "com.coloros.floatassistant",
        "com.oplus.floatassistant",
        "com.coloros.screenshot",
        "com.oplus.screenshot",
        "com.coloros.exserviceui",
        "com.oplus.exserviceui",
        "com.coloros.securitykeyboard",
        "com.oplus.securitykeyboard",
        "com.coloros.gesture",
        "com.oplus.gesture",
        "com.coloros.recents",
        "com.oplus.recents",
        "com.coloros.assistantscreen",
        "com.oplus.assistantscreen",
        "com.heytap.accessory",
    )

    /** The first tap runs. Another tap while that launch is in flight does nothing. */
    fun acceptTap(inFlight: Boolean): Boolean = !inFlight

    /**
     * The shield is a window over the blocked app. A RotRun activity is a second
     * card in the switcher, beside that app, so it is never the cover.
     */
    fun raiseShieldActivity(alreadyShowing: Boolean, elapsedMs: Long): Boolean = false

    /** Overlay when accessibility or draw-over is allowed. Never an activity. */
    fun shieldCover(accessibilityConnected: Boolean, canDrawOverlays: Boolean): String =
        if (accessibilityConnected || canDrawOverlays) "overlay" else "none"

    /**
     * startActivity already opened RotRun. A full-screen intent on top of that
     * launches the activity again and recreates the Ask my brain screen.
     */
    fun fullScreenAfterStart(startSucceeded: Boolean): Boolean = !startSucceeded

    /**
     * A chat does not uncover the blocked app. The shield stays until an unlock.
     * Leaving via recents or home and coming back still draws it.
     */
    fun showShield(chatting: Boolean): Boolean = true

    /** A failed start never drops the hold. Dropping it is what paints the shield again. */
    fun releaseHold(startFailed: Boolean): Boolean = false

    fun shieldAfterAttempt(startFailed: Boolean): ShieldDecision = ShieldDecision(
        dismiss = false,
        redraw = showShield(chatting = true),
        releaseHold = releaseHold(startFailed),
    )
}

data class ShieldDecision(
    val dismiss: Boolean,
    val redraw: Boolean,
    val releaseHold: Boolean,
)
