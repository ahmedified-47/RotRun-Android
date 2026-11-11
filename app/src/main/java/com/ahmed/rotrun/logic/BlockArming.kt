package com.ahmed.rotrun.logic

/**
 * Nothing blocks until every required grant is on.
 * Accessibility has to be enabled in Settings and actually bound.
 * Notifications are required only when the watch notification needs the runtime grant.
 */
object BlockArming {
    data class Step(val id: String, val title: String, val body: String)

    fun steps(
        usage: Boolean,
        accessibilityEnabled: Boolean,
        accessibilityBound: Boolean,
        overlay: Boolean,
        notificationsGranted: Boolean,
        needsNotifications: Boolean,
        batteryUnrestricted: Boolean,
        exactAlarms: Boolean,
    ): List<Step> = buildList {
        if (!usage) {
            add(Step("usage", "Usage access", "RotRun needs this to see which app is open, including after you leave and come back."))
        }
        if (!accessibilityEnabled || !accessibilityBound) {
            add(
                Step(
                    "accessibility",
                    "Accessibility",
                    "Turn RotRun on, and wait until it connects. On ColorOS, also allow Auto-start, or the shield stops when you leave the app.",
                ),
            )
        }
        if (!overlay) {
            add(Step("overlay", "Display over other apps", "This is the other way to draw the shield over a blocked app."))
        }
        if (needsNotifications && !notificationsGranted) {
            add(Step("notifications", "Notifications", "The watching notification is what keeps blocks alive after you leave RotRun."))
        }
        if (!batteryUnrestricted) {
            add(
                Step(
                    "battery",
                    "Battery",
                    "Turn off battery optimization. On ColorOS, also allow Auto-start so a block is still there after RotRun is closed.",
                ),
            )
        }
        if (!exactAlarms) {
            add(Step("alarms", "Alarms", "Allow exact alarms so a 10, 20, or 30 minute unlock blocks again when the time is up, even if RotRun was closed."))
        }
    }

    fun canArm(steps: List<Step>): Boolean = steps.isEmpty()

    /** Saved blockers stay saved. Enforcement waits until every grant is on. */
    fun enforced(armed: Boolean, blocked: Boolean): Boolean = armed && blocked

    fun shouldWatch(armed: Boolean, focusRunning: Boolean, blockerHasApps: Boolean, listHasApps: Boolean): Boolean =
        enforced(armed, focusRunning || blockerHasApps || listHasApps)

    fun pausedLine(grantTitle: String): String = "Blocking paused: grant $grantTitle"
}

/**
 * The first notification ask uses the system dialog.
 * After a denial, a false rationale means Android will not show that dialog again, so the next step is the app notification settings page.
 */
object NotificationPrompt {
    const val REQUEST = "request"
    const val SETTINGS = "settings"

    fun next(granted: Boolean, shouldShowRationale: Boolean, alreadyAsked: Boolean): String? {
        if (granted) return null
        if (!alreadyAsked || shouldShowRationale) return REQUEST
        return SETTINGS
    }

    fun afterDenial(shouldShowRationale: Boolean): String =
        if (shouldShowRationale) REQUEST else SETTINGS
}
