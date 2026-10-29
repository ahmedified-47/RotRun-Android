package com.ahmed.rotrun.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.usage.UsageStats
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import com.ahmed.rotrun.logic.Scoring
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object UsageReader {
    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun read(context: Context, state: SavedState): UsageSnapshot {
        if (!hasAccess(context)) return UsageSnapshot()
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return UsageSnapshot()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val excluded = state.excluded.map { it.packageName }.toSet()
        val own = context.packageName
        val daily = mutableMapOf<LocalDate, Long>()
        val drainDaily = mutableMapOf<LocalDate, Long>()
        val todayApps = mutableListOf<Offender>()
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, weekStart, now).orEmpty()
        for (stat in stats) {
            val seconds = visibleMillis(stat) / 1000L
            if (stat.packageName == own || seconds <= 0L) continue
            val day = Instant.ofEpochMilli(stat.lastTimeUsed).atZone(zone).toLocalDate()
            if (day.isBefore(today.minusDays(6)) || day.isAfter(today)) continue
            daily[day] = (daily[day] ?: 0L) + seconds
            if (stat.packageName !in excluded) {
                drainDaily[day] = (drainDaily[day] ?: 0L) + seconds
            }
            if (day == today && stat.packageName !in excluded) {
                todayApps += Offender(stat.packageName, label(context, stat.packageName), seconds)
            }
        }
        val todaySeconds = daily[today] ?: 0L
        val drainSeconds = drainDaily[today] ?: 0L
        val avg = (0..6).sumOf { daily[today.minusDays(it.toLong())] ?: 0L } / 7L
        val hourlyToday = FloatArray(24)
        val hourlyAll = FloatArray(24)
        val hourlyDays = mutableSetOf<LocalDate>()
        readEvents(manager, weekStart, now, zone, own, hourlyToday, hourlyAll, hourlyDays, today)
        val pickups = countPickups(manager, today.atStartOfDay(zone).toInstant().toEpochMilli(), now, own)
        val hourCount = hourlyDays.size.coerceAtLeast(1)
        val offenders = todayApps.sortedByDescending { it.seconds }.take(5)
        val week = (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            DayUsage(Scoring.dayKey(day), day.dayOfWeek.name.take(1), daily[day] ?: 0L)
        }
        return UsageSnapshot(
            authorized = true,
            todaySeconds = todaySeconds,
            drainSeconds = drainSeconds,
            pickups = pickups,
            avgSeconds = avg,
            diffPercent = Scoring.diffPercent(todaySeconds, avg),
            belowAverage = todaySeconds <= avg,
            aheadOfPace = Scoring.aheadOfPace(todaySeconds, avg, now),
            offenders = offenders,
            hourlyToday = hourlyToday.map { it / 60f },
            hourlyAvg = hourlyAll.map { it / hourCount / 60f },
            week = week,
        )
    }

    fun foreground(context: Context): String? {
        if (!hasAccess(context)) return null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, end - 20_000, end).orEmpty()
        return stats.filter { it.lastTimeUsed >= end - 8_000 }.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    /**
     * Launchable apps via MAIN/LAUNCHER. Flags must be 0.
     * MATCH_ALL returns an empty list on Android 11+ unless the app holds QUERY_ALL_PACKAGES.
     */
    fun installedApps(context: Context): List<TrackedApp> {
        val hidden = safetyPackages(context)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return resolve(context, intent)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName in hidden) return@mapNotNull null
                val label = info.loadLabel(context.packageManager).toString().ifBlank { return@mapNotNull null }
                TrackedApp(packageName, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun safetyPackages(context: Context): Set<String> {
        val packages = mutableSetOf(
            context.packageName,
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.server.telecom",
        )
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        resolve(context, home).forEach { info -> info.activityInfo?.packageName?.let(packages::add) }
        val dial = Intent(Intent.ACTION_DIAL)
        resolve(context, dial).forEach { info -> info.activityInfo?.packageName?.let(packages::add) }
        return packages
    }

    private fun resolve(context: Context, intent: Intent): List<ResolveInfo> {
        val manager = context.packageManager
        return if (Build.VERSION.SDK_INT >= 33) {
            manager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.queryIntentActivities(intent, 0)
        }
    }

    private fun visibleMillis(stat: UsageStats): Long {
        val visible = if (Build.VERSION.SDK_INT >= 29) stat.totalTimeVisible else 0L
        return maxOf(stat.totalTimeInForeground, visible)
    }

    fun todaySeconds(context: Context, packageName: String): Long {
        if (!hasAccess(context)) return 0L
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis()).orEmpty()
        val stat = stats.firstOrNull { it.packageName == packageName } ?: return 0L
        return visibleMillis(stat) / 1000L
    }

    fun label(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName.substringAfterLast('.'))

    private fun readEvents(
        manager: UsageStatsManager,
        start: Long,
        end: Long,
        zone: ZoneId,
        own: String,
        hourlyToday: FloatArray,
        hourlyAll: FloatArray,
        days: MutableSet<LocalDate>,
        today: LocalDate,
    ) {
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val resume = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_RESUMED else UsageEvents.Event.MOVE_TO_FOREGROUND
        val pause = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_PAUSED else UsageEvents.Event.MOVE_TO_BACKGROUND
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == own) continue
            when (event.eventType) {
                resume -> open[event.packageName] = event.timeStamp
                pause -> {
                    val began = open.remove(event.packageName) ?: continue
                    addSpan(began, event.timeStamp, zone, today, hourlyToday, hourlyAll, days)
                }
            }
        }
    }

    private fun addSpan(
        start: Long,
        end: Long,
        zone: ZoneId,
        today: LocalDate,
        hourlyToday: FloatArray,
        hourlyAll: FloatArray,
        days: MutableSet<LocalDate>,
    ) {
        var cursor = start
        while (cursor < end) {
            val dateTime = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = dateTime.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
            val sliceEnd = minOf(end, nextHour)
            val seconds = ((sliceEnd - cursor) / 1000f).coerceAtLeast(0f)
            val hour = dateTime.hour
            if (hour in 0..23) {
                hourlyAll[hour] += seconds
                days += dateTime.toLocalDate()
                if (dateTime.toLocalDate() == today) hourlyToday[hour] += seconds
            }
            cursor = sliceEnd
        }
    }

    private fun countPickups(manager: UsageStatsManager, start: Long, end: Long, own: String): Int {
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        var keyguard = 0
        var interactive = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == own) continue
            when (event.eventType) {
                UsageEvents.Event.KEYGUARD_HIDDEN -> keyguard++
                UsageEvents.Event.SCREEN_INTERACTIVE -> interactive++
            }
        }
        return if (keyguard > 0) keyguard else interactive
    }
}
