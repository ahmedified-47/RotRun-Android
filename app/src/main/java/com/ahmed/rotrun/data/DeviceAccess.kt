package com.ahmed.rotrun.data

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.ahmed.rotrun.watch.BlockAccessibilityService

data class DeviceAccess(
    val usage: Boolean = false,
    val accessibility: Boolean = false,
    val notifications: Boolean = false,
    val overlay: Boolean = false,
    val batteryUnrestricted: Boolean = false,
)

object DeviceAccessReader {
    fun read(context: Context): DeviceAccess {
        val notifications = if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED &&
                context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        } else {
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return DeviceAccess(
            usage = UsageReader.hasAccess(context),
            accessibility = accessibilityEnabled(context),
            notifications = notifications,
            overlay = Settings.canDrawOverlays(context),
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
        )
    }

    fun accessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!enabled) return false
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val expected = ComponentName(context, BlockAccessibilityService::class.java).flattenToString()
        return services.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun usageIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun accessibilityIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun overlayIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun open(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure {
            intent.data = null
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
