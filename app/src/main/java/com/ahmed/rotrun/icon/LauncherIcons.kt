package com.ahmed.rotrun.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.ahmed.rotrun.R

/**
 * Activity-alias launcher icons. The chosen alias is enabled before the others are disabled,
 * and exactly one stays enabled. ColorOS and MIUI keep the previous bitmap until they rescan,
 * so a successful switch still tells the user the home screen can take a minute.
 */
object LauncherIcons {
    val order = listOf("Default", "Grassy", "Spark", "Zen", "Scholar", "Cosmic")

    const val refreshNote = "Your home screen may take a minute to update."

    /** Saved names that are not an alias fall back to the manifest default. */
    fun persistedChoice(stored: String?): String = if (stored != null && stored in order) stored else "Default"

    /**
     * Enable the saved alias first, then disable every other alias.
     * The first pair is the one that must stay enabled.
     */
    fun changes(name: String): List<Pair<String, Boolean>> {
        val chosen = persistedChoice(name)
        return listOf(chosen to true) + order.filter { it != chosen }.map { it to false }
    }

    fun isLauncherAlias(className: String): Boolean {
        val simple = className.substringAfterLast('.')
        return simple in order && className == "com.ahmed.rotrun.icon.$simple"
    }

    /** Flat preview. The alias mipmap is adaptive XML, which Compose cannot paint. */
    fun preview(name: String): Int = when (persistedChoice(name)) {
        "Grassy" -> R.drawable.ic_preview_grassy
        "Spark" -> R.drawable.ic_preview_spark
        "Zen" -> R.drawable.ic_preview_zen
        "Scholar" -> R.drawable.ic_preview_scholar
        "Cosmic" -> R.drawable.ic_preview_cosmic
        else -> R.drawable.ic_preview_default
    }

    /** Null when the aliases were updated. A string is the failure to show. */
    fun apply(context: Context, name: String): String? {
        val manager = context.packageManager
        return try {
            changes(name).forEach { (alias, enabled) ->
                manager.setComponentEnabledSetting(
                    component(context, alias),
                    if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            null
        } catch (error: Exception) {
            error.message?.takeIf { it.isNotBlank() } ?: "Couldn't change the app icon."
        }
    }

    private fun component(context: Context, name: String): ComponentName =
        ComponentName(context.packageName, "com.ahmed.rotrun.icon.$name")
}
