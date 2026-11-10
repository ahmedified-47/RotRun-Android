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

    fun transientWindow(packageName: String): Boolean {
        val name = packageName.lowercase()
        return name == "android" ||
            name.startsWith("com.android.systemui") ||
            name.startsWith("com.coloros.") ||
            name.startsWith("com.oplus.") ||
            name.startsWith("com.oppo.") ||
            name.startsWith("com.heytap.")
    }

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

    /** Talk is open. Watchers must not paint the shield over the blocked app. */
    fun showShield(chatting: Boolean): Boolean = !chatting

    /** A failed start never drops the hold. Dropping it is what paints the shield again. */
    fun releaseHold(startFailed: Boolean): Boolean = false

    fun shieldAfterAttempt(startFailed: Boolean): ShieldDecision = ShieldDecision(
        dismiss = true,
        redraw = showShield(chatting = true),
        releaseHold = releaseHold(startFailed),
    )
}

data class ShieldDecision(
    val dismiss: Boolean,
    val redraw: Boolean,
    val releaseHold: Boolean,
)
