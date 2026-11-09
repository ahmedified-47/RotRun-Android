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

    /**
     * The user left the blocked app for RotRun, the launcher, or another safety app.
     * System UI is not that. It is noise from the overlay.
     */
    fun userWentElsewhere(packageName: String, ownPackage: String, safety: Set<String>): Boolean {
        if (packageName.isBlank() || packageName == "android") return false
        if (packageName.startsWith("com.android.systemui")) return false
        if (packageName == ownPackage) return true
        return packageName in safety
    }

    /**
     * Usage poll. A blank reading must not drop a shield: usage access is often off,
     * and that is what made every block disappear. RotRun hides the shield only when
     * its resume is newer than the shield. An older RotRun resume is the app the user
     * just left, and system UI never counts as leaving.
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
        if (packageName.isNullOrBlank()) return ShieldStep.Leave
        if (packageName == ownPackage) {
            if (shownFor != null && resumedAtMillis < shownAtMillis) return ShieldStep.Leave
            return ShieldStep.Hide
        }
        if (!userWentElsewhere(packageName, ownPackage, safety) && ignore(packageName, ownPackage)) {
            return if (shownFor != null) ShieldStep.Keep else ShieldStep.Leave
        }
        return if (shouldShowShield(packageName, ownPackage, safety, blocked)) ShieldStep.Show else ShieldStep.Hide
    }

    /**
     * Accessibility window event. WINDOWS_CHANGED for RotRun or system chrome is the
     * shield itself. Hiding on that event is what removed the block a moment after it
     * appeared. WINDOW_STATE_CHANGED for RotRun means the user opened RotRun.
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
        val elsewhere = userWentElsewhere(eventPackage, ownPackage, safety)
        val noise = eventPackage != ownPackage && !elsewhere && ignore(eventPackage, ownPackage)
        if (eventPackage == ownPackage || noise || elsewhere) {
            if (windowStateChanged && (eventPackage == ownPackage || elsewhere)) return ShieldStep.Hide
            return if (shownFor != null) ShieldStep.Keep else ShieldStep.Leave
        }
        return if (shouldShowShield(eventPackage, ownPackage, safety, eventBlocked)) ShieldStep.Show else ShieldStep.Hide
    }
}
