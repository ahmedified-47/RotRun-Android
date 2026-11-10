package com.ahmed.rotrun.watch

/**
 * A block shield is for a blocked app the user actually opened.
 * RotRun, its overlay, System UI, and the launcher are not that app.
 */
enum class ShieldStep { Show, Hide, Keep, Leave }

object ForegroundGate {
    fun ignore(packageName: String, ownPackage: String): Boolean {
        if (packageName.isBlank() || packageName == ownPackage) return true
        return AskLaunchPolicy.transientWindow(packageName)
    }

    fun shouldShowShield(foreground: String?, ownPackage: String, safety: Set<String>, blocked: Boolean): Boolean {
        if (foreground.isNullOrBlank()) return false
        if (ignore(foreground, ownPackage)) return false
        if (foreground in safety) return false
        return blocked
    }

    /** Newest resume, including RotRun. Dropping RotRun is what made opening it look like the previous app. */
    fun latestResume(packagesNewestLast: List<String>): String? = packagesNewestLast.lastOrNull { it.isNotBlank() }

    /** Android and System UI. ColorOS chrome is separate: its window events are overlay noise. */
    fun systemChrome(packageName: String): Boolean {
        val name = packageName.lowercase()
        return name == "android" || name.startsWith("com.android.systemui")
    }

    /**
     * The user left the blocked app for RotRun, the launcher, or another safety app.
     * System UI is judged from a real resume or window-state change, not from overlay noise.
     */
    fun userWentElsewhere(packageName: String, ownPackage: String, safety: Set<String>): Boolean {
        if (packageName.isBlank() || packageName == "android") return false
        if (packageName.startsWith("com.android.systemui")) return false
        if (packageName == ownPackage) return true
        return packageName in safety
    }

    /**
     * The package a shield decision should use.
     * RotRun and the launcher stay as reported so they can take the shield down.
     * A blank reading, System UI, or ColorOS chrome is the overlay's own event, not the
     * app in front. [alternate] is that app (the focused application window). Using it
     * is what shows the cover the first time, when usage access has no row yet.
     */
    fun packageToCover(reported: String?, alternate: String?, ownPackage: String, safety: Set<String>): String? {
        val event = reported.orEmpty()
        if (event == ownPackage) return event
        if (event.isNotBlank() && userWentElsewhere(event, ownPackage, safety)) return event
        if (event.isNotBlank() && !ignore(event, ownPackage)) return event
        val other = alternate?.takeIf { candidate ->
            candidate.isNotBlank() &&
                candidate != ownPackage &&
                !ignore(candidate, ownPackage) &&
                candidate !in safety
        }
        if (other != null) return other
        return event.takeIf { it.isNotBlank() }
    }

    /**
     * Usage poll. A blank reading must not drop a shield: usage access is often off,
     * and that is what made every block disappear. It also must not be the only chance
     * to show one. RotRun and System UI hide the shield only when their resume is newer
     * than the shield. An older resume is the app the user just left. ColorOS usage
     * stays up so an overlay event cannot uncover Calendar.
     */
    fun onUsage(
        packageName: String?,
        resumedAtMillis: Long,
        shownFor: String?,
        shownAtMillis: Long,
        ownPackage: String,
        safety: Set<String>,
        blocked: Boolean,
    ): ShieldStep {
        if (packageName.isNullOrBlank()) return if (shownFor != null) ShieldStep.Keep else ShieldStep.Leave
        if (packageName == ownPackage) {
            if (shownFor != null && resumedAtMillis < shownAtMillis) return ShieldStep.Leave
            return ShieldStep.Hide
        }
        if (!userWentElsewhere(packageName, ownPackage, safety) && ignore(packageName, ownPackage)) {
            val leftForSystemUi = systemChrome(packageName) &&
                shownFor != null &&
                resumedAtMillis > 0L &&
                resumedAtMillis >= shownAtMillis
            if (leftForSystemUi) return ShieldStep.Hide
            return if (shownFor != null) ShieldStep.Keep else ShieldStep.Leave
        }
        return if (shouldShowShield(packageName, ownPackage, safety, blocked)) ShieldStep.Show else ShieldStep.Hide
    }

    /**
     * Accessibility window event. The overlay's own window is reported as RotRun or
     * System UI, including WINDOW_STATE_CHANGED. Hiding on that event removes the
     * cover and the next poll adds it again, which is the Ask my brain flicker.
     * A real leave is a usage resume of RotRun or System UI, or the launcher.
     */
    fun onWindow(
        eventPackage: String,
        windowStateChanged: Boolean,
        shownFor: String?,
        ownPackage: String,
        safety: Set<String>,
        eventBlocked: Boolean,
    ): ShieldStep {
        if (eventPackage.isBlank()) return if (shownFor != null) ShieldStep.Keep else ShieldStep.Leave
        if (shownFor != null && eventPackage == shownFor) return ShieldStep.Keep
        val elsewhere = userWentElsewhere(eventPackage, ownPackage, safety)
        val noise = eventPackage != ownPackage && !elsewhere && ignore(eventPackage, ownPackage)
        if (eventPackage == ownPackage || noise) {
            return if (shownFor != null) ShieldStep.Keep else ShieldStep.Leave
        }
        if (elsewhere) {
            if (windowStateChanged) return ShieldStep.Hide
            return if (shownFor != null) ShieldStep.Keep else ShieldStep.Leave
        }
        return if (shouldShowShield(eventPackage, ownPackage, safety, eventBlocked)) ShieldStep.Show else ShieldStep.Hide
    }
}
