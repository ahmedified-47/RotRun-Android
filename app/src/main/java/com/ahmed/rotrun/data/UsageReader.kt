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
        val todayApps = mutableMapOf<String, Long>()
        val todayByPackage = mutableMapOf<String, Long>()
        // A week-long INTERVAL_DAILY query stamps every row with the query start, so
        // today's time lands on an older day and Home shows 0m. Ask for each day.
        // Samples longer than that day are not clipped by the platform (Permission
        // controller reported 37h of "visible" time). Drop those and keep the rest.
        for (offset in 0..6) {
            val day = today.minusDays(offset.toLong())
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = if (offset == 0) now else day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val window = (end - start).coerceAtLeast(0L)
            val aggregated = manager.queryAndAggregateUsageStats(start, end).orEmpty()
            var dayTotal = 0L
            var drainTotal = 0L
            for ((pkg, stat) in aggregated) {
                if (pkg == own) continue
                val seconds = countedSampleMillis(stat.totalTimeInForeground, visibleMillis(stat), window) / 1000L
                if (seconds <= 0L) continue
                dayTotal += seconds
                if (offset == 0) todayByPackage[pkg] = seconds
                if (pkg !in excluded) {
                    drainTotal += seconds
                    if (offset == 0) todayApps[pkg] = seconds
                }
            }
            daily[day] = dayTotal
            drainDaily[day] = drainTotal
        }
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val live = foregroundSeconds(eventMarks(manager, todayStart, now), todayStart, now, own)
        for ((pkg, seconds) in live) {
            if (seconds <= 0L) continue
            val previous = todayByPackage[pkg] ?: 0L
            if (seconds <= previous) continue
            todayByPackage[pkg] = seconds
            daily[today] = (daily[today] ?: 0L) + (seconds - previous)
            if (pkg !in excluded) {
                drainDaily[today] = (drainDaily[today] ?: 0L) + (seconds - previous)
                todayApps[pkg] = seconds
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
        val offenders = todayApps.entries
            .filter { it.value > 0L }
            .sortedByDescending { it.value }
            .take(5)
            .map { (pkg, seconds) -> Offender(pkg, label(context, pkg), seconds) }
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

    /**
     * Package that last came to the foreground. The window is hours, not seconds:
     * a block service often starts after the blocked app is already open, and that
     * resume event is older than a few seconds.
     */
    fun foreground(context: Context): String? {
        if (!hasAccess(context)) return null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val resume = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_RESUMED else UsageEvents.Event.MOVE_TO_FOREGROUND
        val events = manager.queryEvents(end - 6 * 60 * 60_000L, end)
        val event = UsageEvents.Event()
        var latest: String? = null
        var latestTime = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == resume && event.timeStamp >= latestTime && event.packageName != context.packageName) {
                latestTime = event.timeStamp
                latest = event.packageName
            }
        }
        if (latest != null) return latest
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, end - 60 * 60_000L, end).orEmpty()
        return stats.filter { it.lastTimeUsed > 0L && it.packageName != context.packageName }
            .maxByOrNull { it.lastTimeUsed }?.packageName
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

    private fun visibleMillis(stat: UsageStats): Long =
        if (Build.VERSION.SDK_INT >= 29) stat.totalTimeVisible else 0L

    fun todaySeconds(context: Context, packageName: String): Long {
        if (!hasAccess(context)) return 0L
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val window = (end - start).coerceAtLeast(0L)
        val aggregated = manager.queryAndAggregateUsageStats(start, end)[packageName]?.let { stat ->
            countedSampleMillis(stat.totalTimeInForeground, visibleMillis(stat), window) / 1000L
        } ?: 0L
        val live = foregroundSeconds(eventMarks(manager, start, end), start, end, "")[packageName] ?: 0L
        return maxOf(aggregated, live)
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

internal data class UsageSlice(
    val packageName: String,
    val bucketStartMillis: Long,
    val lastUsedMillis: Long,
    val visibleMillis: Long,
)

/** Daily buckets are dated by their start. Last-used is only a fallback when the bucket start is missing. */
internal fun usageBucketDay(slice: UsageSlice, zone: ZoneId): LocalDate? {
    val millis = when {
        slice.bucketStartMillis > 0L -> slice.bucketStartMillis
        slice.lastUsedMillis > 0L -> slice.lastUsedMillis
        else -> return null
    }
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}

/** Same package and same bucket keep the larger sample so a repeated row is not added twice. */
internal fun dedupedUsageSlices(slices: List<UsageSlice>): List<UsageSlice> =
    slices.groupBy { slice ->
        slice.packageName to if (slice.bucketStartMillis > 0L) slice.bucketStartMillis else slice.lastUsedMillis
    }.values.map { group -> group.maxBy { it.visibleMillis } }

/**
 * Foreground time when it fits in the query window. Otherwise visible time, if that fits.
 * A sample longer than the window is the unclipped bucket, not time spent today.
 */
internal fun countedSampleMillis(foreground: Long, visible: Long, windowMillis: Long): Long {
    if (windowMillis <= 0L) return 0L
    val fg = foreground.coerceAtLeast(0L)
    val vis = visible.coerceAtLeast(0L)
    return when {
        fg > 0L && fg <= windowMillis -> fg
        vis > 0L && vis <= windowMillis -> vis
        else -> 0L
    }
}

internal data class ForegroundMark(val packageName: String, val timeMillis: Long, val resumed: Boolean)

/**
 * Foreground time inside the window. Only one package is in the foreground, so the
 * next app's resume closes the previous one. Extra pauses do not start another
 * span from the beginning of the day. A package cannot exceed the window.
 */
internal fun foregroundSeconds(marks: List<ForegroundMark>, windowStart: Long, windowEnd: Long, skipPackage: String): Map<String, Long> {
    if (windowEnd <= windowStart) return emptyMap()
    val cap = (windowEnd - windowStart) / 1000L
    val totals = HashMap<String, Long>()
    var current: String? = null
    var since = windowStart
    var seenOther = false
    var countedLeading = false
    fun add(packageName: String?, began: Long, ended: Long) {
        if (packageName.isNullOrBlank() || packageName == skipPackage) return
        val from = maxOf(began, windowStart)
        val to = minOf(ended, windowEnd)
        if (to > from) totals[packageName] = ((totals[packageName] ?: 0L) + (to - from) / 1000L).coerceAtMost(cap)
    }
    for (mark in marks.sortedBy { it.timeMillis }) {
        if (mark.packageName.isBlank()) continue
        if (mark.packageName == skipPackage) {
            if (mark.resumed && current != null) {
                add(current, since, mark.timeMillis)
                current = null
            }
            continue
        }
        if (mark.resumed) {
            seenOther = true
            if (current != mark.packageName) {
                add(current, since, mark.timeMillis)
                current = mark.packageName
                since = mark.timeMillis
            }
        } else if (current == mark.packageName) {
            add(current, since, mark.timeMillis)
            current = null
        } else if (current == null && !seenOther && !countedLeading) {
            countedLeading = true
            add(mark.packageName, windowStart, mark.timeMillis)
        }
    }
    add(current, since, windowEnd)
    return totals
}

private fun eventMarks(manager: UsageStatsManager, start: Long, end: Long): List<ForegroundMark> {
    val events = manager.queryEvents(start, end)
    val event = UsageEvents.Event()
    val marks = mutableListOf<ForegroundMark>()
    val resume = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_RESUMED else UsageEvents.Event.MOVE_TO_FOREGROUND
    val pause = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_PAUSED else UsageEvents.Event.MOVE_TO_BACKGROUND
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        val resumed = when (event.eventType) {
            resume -> true
            pause -> false
            else -> continue
        }
        val name = event.packageName ?: continue
        marks += ForegroundMark(name, event.timeStamp, resumed)
    }
    return marks
}

/**
 * Home figures to draw after a refresh.
 * A missing, failed, or all-zero read keeps the last measured numbers so the screen
 * does not flash empty. A positive new figure replaces that field.
 */
internal fun mergeHomeUsage(current: UsageSnapshot, next: UsageSnapshot): UsageSnapshot {
    if (!next.authorized) return if (current.authorized) current else next
    if (!current.authorized) return next
    val today = next.todaySeconds.takeIf { it > 0L } ?: current.todaySeconds
    val drain = next.drainSeconds.takeIf { it > 0L } ?: current.drainSeconds
    val pickups = next.pickups.takeIf { it > 0 } ?: current.pickups
    val avg = next.avgSeconds.takeIf { it > 0L } ?: current.avgSeconds
    val offenders = next.offenders.filter { it.seconds > 0L }.ifEmpty { current.offenders }
    val week = when {
        next.week.any { it.seconds > 0L } -> next.week
        current.week.any { it.seconds > 0L } -> current.week
        else -> next.week
    }
    val hourlyToday = if (next.hourlyToday.any { it > 0f }) next.hourlyToday else current.hourlyToday
    val hourlyAvg = if (next.hourlyAvg.any { it > 0f }) next.hourlyAvg else current.hourlyAvg
    return next.copy(
        authorized = true,
        todaySeconds = today,
        drainSeconds = drain,
        pickups = pickups,
        avgSeconds = avg,
        diffPercent = Scoring.diffPercent(today, avg),
        belowAverage = today <= avg,
        aheadOfPace = Scoring.aheadOfPace(today, avg),
        offenders = offenders,
        hourlyToday = hourlyToday,
        hourlyAvg = hourlyAvg,
        week = week,
    )
}

/** Today's rows only, summed when the platform splits the day into more than one record. */
internal fun visibleSecondsOnDay(slices: List<UsageSlice>, packageName: String, day: LocalDate, zone: ZoneId): Long =
    dedupedUsageSlices(slices.filter { it.packageName == packageName })
        .filter { usageBucketDay(it, zone) == day }
        .sumOf { it.visibleMillis } / 1000L
