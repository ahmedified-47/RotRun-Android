package com.ahmed.rotrun.logic

/** Payload for screen_time_readings / screen_time_days. Skips anything under 60 seconds. */
object ScreenTimeSync {
    private val bannedNames = setOf("unknown", "app", "website")

    data class UsageView(
        val authorized: Boolean,
        val todaySeconds: Long,
        val pickups: Int,
        val avgSeconds: Long,
        val ahead: Boolean,
        val offenders: List<Pair<String, Long>>,
        val hourlyToday: List<Float>,
        val hourlyAvg: List<Float>,
        val week: List<Pair<String, Long>>,
    )

    data class OffenderRow(val name: String, val minutes: Int)
    data class HourRow(val hour: Int, val todayMinutes: Int, val averageMinutes: Int)
    data class DayRow(val day: String, val minutes: Int)

    data class Reading(
        val todayMinutes: Int,
        val pickups: Int,
        val topApp: String?,
        val topAppMinutes: Int?,
        val offenders: List<OffenderRow>,
        val paceAverageMinutes: Int?,
        val ahead: Boolean,
        val hourly: List<HourRow>,
        val days: List<DayRow>,
    )

    fun reading(snapshot: UsageView): Reading? {
        if (!snapshot.authorized || snapshot.todaySeconds < 60L) return null
        val offenders = snapshot.offenders.mapNotNull { (label, seconds) ->
            if (seconds < 60L) return@mapNotNull null
            val name = label.trim().take(80)
            if (name.isBlank() || name.lowercase() in bannedNames) return@mapNotNull null
            val minutes = (seconds / 60L).toInt()
            if (minutes <= 0) return@mapNotNull null
            OffenderRow(name, minutes)
        }
        val top = offenders.firstOrNull()
        val hourly = snapshot.hourlyToday.mapIndexedNotNull { hour, today ->
            if (hour !in 0..23) return@mapIndexedNotNull null
            val average = snapshot.hourlyAvg.getOrElse(hour) { 0f }
            val todayMinutes = today.toInt().coerceAtLeast(0)
            val averageMinutes = average.toInt().coerceAtLeast(0)
            if (todayMinutes <= 0 && averageMinutes <= 0) null else HourRow(hour, todayMinutes, averageMinutes)
        }
        val days = snapshot.week.mapNotNull { (day, seconds) ->
            val minutes = (seconds / 60L).toInt()
            if (day.isBlank() || minutes <= 0) null else DayRow(day, minutes)
        }
        return Reading(
            todayMinutes = (snapshot.todaySeconds / 60L).toInt().coerceAtLeast(1),
            pickups = snapshot.pickups.coerceAtLeast(0),
            topApp = top?.name,
            topAppMinutes = top?.minutes,
            offenders = offenders,
            paceAverageMinutes = (snapshot.avgSeconds / 60L).toInt().takeIf { it > 0 },
            ahead = snapshot.ahead,
            hourly = hourly,
            days = days,
        )
    }
}
