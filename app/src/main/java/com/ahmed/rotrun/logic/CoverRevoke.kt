package com.ahmed.rotrun.logic

/**
 * Losing Accessibility or overlay while a shield is up must not uncover the blocked app.
 * COVER keeps whichever grant can still draw and opens the permission gate.
 * GATE means neither grant can draw, so the only move is the in-app gate.
 */
object CoverRevoke {
    const val STAY = "stay"
    const val COVER = "cover"
    const val GATE = "gate"

    fun response(accessibilityOn: Boolean, overlayOn: Boolean): String = when {
        accessibilityOn && overlayOn -> STAY
        accessibilityOn || overlayOn -> COVER
        else -> GATE
    }
}
