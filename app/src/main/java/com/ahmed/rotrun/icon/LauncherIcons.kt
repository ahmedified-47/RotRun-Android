package com.ahmed.rotrun.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.ahmed.rotrun.R

/** Activity-alias launcher icons. Enable the chosen alias before disabling the others. */
object LauncherIcons {
    val order = listOf("Default", "Grassy", "Spark", "Zen", "Scholar", "Cosmic")

    fun preview(name: String): Int = when (name) {
        "Grassy" -> R.mipmap.ic_launcher_grassy
        "Spark" -> R.mipmap.ic_launcher_spark
        "Zen" -> R.mipmap.ic_launcher_zen
        "Scholar" -> R.mipmap.ic_launcher_scholar
        "Cosmic" -> R.mipmap.ic_launcher_cosmic
        else -> R.mipmap.ic_launcher_default
    }

    fun apply(context: Context, name: String) {
        val chosenName = if (name in order) name else "Default"
        val manager = context.packageManager
        runCatching {
            manager.setComponentEnabledSetting(
                component(context, chosenName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            order.filter { it != chosenName }.forEach { other ->
                manager.setComponentEnabledSetting(
                    component(context, other),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    private fun component(context: Context, name: String): ComponentName =
        ComponentName(context.packageName, "com.ahmed.rotrun.icon.$name")
}
