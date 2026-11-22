package com.ahmed.rotrun.logic

import org.json.JSONObject

/**
 * A Play reinstall on the same phone restores SharedPreferences when backup is on.
 * `rotrun.xml` holds onboardingCompleted. An empty file is a real fresh install.
 * A restored file with that flag set is why signup was skipped.
 */
object InstallStart {
    fun showsOnboarding(savedStateJson: String?): Boolean {
        if (savedStateJson.isNullOrBlank()) return true
        val completed = runCatching { JSONObject(savedStateJson).optBoolean("onboardingCompleted") }.getOrDefault(false)
        return !completed
    }
}
