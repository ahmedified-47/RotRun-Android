package com.ahmed.rotrun.watch

/**
 * A block shield is for a blocked app the user actually opened.
 * RotRun, its overlay, System UI, and the launcher are not that app.
 */
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
}
