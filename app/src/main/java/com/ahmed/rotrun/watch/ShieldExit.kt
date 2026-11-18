package com.ahmed.rotrun.watch

/**
 * Choosing not to open the blocked app has to leave that app.
 * The shield stays down until the foreground is no longer that package,
 * so the next poll cannot paint the same cover over it.
 */
object ShieldExit {
    fun suppress(declinedPackage: String?, foreground: String?): Boolean {
        if (declinedPackage.isNullOrBlank()) return false
        return foreground.isNullOrBlank() || foreground == declinedPackage
    }

    /**
     * Home, the launcher, or a different real app means the walk-away finished.
     * RotRun itself and overlay chrome do not: those show up while home is still on the way.
     */
    fun finishedLeaving(
        declinedPackage: String?,
        foreground: String?,
        ownPackage: String,
        safety: Set<String>,
    ): Boolean {
        if (declinedPackage.isNullOrBlank() || foreground.isNullOrBlank()) return false
        if (foreground == declinedPackage || foreground == ownPackage) return false
        if (ForegroundGate.userWentElsewhere(foreground, ownPackage, safety)) return true
        return ForegroundGate.realApp(foreground, ownPackage, safety)
    }
}
