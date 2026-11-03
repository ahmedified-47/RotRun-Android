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
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT

    /** While this is true the overlay stays, so the start still has a visible window. */
    fun leaveOverlayUp(launching: Boolean): Boolean = launching

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
