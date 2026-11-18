package com.ahmed.rotrun.logic

/**
 * Uninstall clears Accessibility, display-over-other-apps, and usage access.
 * Those grants are not in the backup. If signup is skipped, nobody asks for them
 * again and the shield has nothing to draw on.
 */
object AccessPrompt {
    fun shouldPrompt(usage: Boolean, accessibility: Boolean, overlay: Boolean): Boolean =
        !usage || !accessibility || !overlay

    fun shieldCanDraw(accessibility: Boolean, overlay: Boolean): Boolean = accessibility || overlay

    fun message(usage: Boolean, accessibility: Boolean, overlay: Boolean, batteryUnrestricted: Boolean): String {
        val lines = mutableListOf<String>()
        if (!accessibility && !overlay) {
            lines += "Apps are not blocked. Accessibility and display over other apps are both off, so the shield has nowhere to draw."
        } else {
            if (!accessibility) lines += "Accessibility is off, so the shield may not cover a blocked app."
            if (!overlay) lines += "Display over other apps is off. That is the other way to draw the shield."
        }
        if (!usage) lines += "Usage access is off, so RotRun can't tell which app is open."
        if (!batteryUnrestricted) lines += "Battery optimization is on. ColorOS can stop the shield after you leave RotRun."
        return lines.joinToString(" ")
    }

    /** First setting to open. Usage, then Accessibility, then the overlay. */
    fun firstMissing(usage: Boolean, accessibility: Boolean, overlay: Boolean): String? = when {
        !usage -> "usage"
        !accessibility -> "accessibility"
        !overlay -> "overlay"
        else -> null
    }
}
