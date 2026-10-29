package com.ahmed.rotrun.logic

import java.time.ZonedDateTime

/** On-device block decisions. Android enforces Schedule and Daily Limit; iOS 105 stores them only. */
object BlockRules {
    const val FREE_APP_LIMIT = 1
    const val FREE_LIMIT_MESSAGE = "Free includes 1 blocked app. Premium blocks unlimited apps."

    data class Window(
        val packages: Set<String>,
        val mode: String,
        val days: Set<Int>,
        val startHour: Int,
        val endHour: Int,
        val limitMinutes: Int,
    )

    data class FocusGate(val allApps: Boolean, val brainrot: Set<String>)

    /** Sunday = 0 … Saturday = 6, matching the iOS day chips. */
    fun dayIndex(now: ZonedDateTime): Int = now.dayOfWeek.value % 7

    fun covers(mode: String, days: Set<Int>, startHour: Int, endHour: Int, now: ZonedDateTime): Boolean {
        if (dayIndex(now) !in days) return false
        if (mode != "schedule") return true
        val hour = now.hour
        return when {
            startHour == endHour -> true
            startHour < endHour -> hour in startHour until endHour
            else -> hour >= startHour || hour < endHour
        }
    }

    fun isBlocked(
        packageName: String,
        safety: Set<String>,
        excluded: Set<String>,
        focus: FocusGate?,
        unlockUntil: Long,
        nowMillis: Long,
        now: ZonedDateTime,
        usageSeconds: Long,
        windows: List<Window>,
    ): Boolean {
        if (packageName in safety || packageName in excluded) return false
        if (focus != null) return if (focus.allApps) true else packageName in focus.brainrot
        if (nowMillis < unlockUntil) return false
        return windows.any { window ->
            if (packageName !in window.packages) return@any false
            when (window.mode) {
                "schedule" -> covers("schedule", window.days, window.startHour, window.endHour, now)
                "dailyLimit" -> covers("allDay", window.days, window.startHour, window.endHour, now) &&
                    usageSeconds >= window.limitMinutes * 60L
                else -> covers("allDay", window.days, window.startHour, window.endHour, now)
            }
        }
    }

    /** Free tier keeps the first distinct package. Premium, or a paywall that is off, keeps all of them. */
    fun freeAllowed(ordered: List<String>, premium: Boolean, paywall: Boolean): Set<String> {
        val unique = ordered.filter { it.isNotBlank() }.distinct()
        if (!paywall || premium) return unique.toSet()
        return unique.take(FREE_APP_LIMIT).toSet()
    }

    fun overFreeCap(
        existing: Set<String>,
        proposed: Set<String>,
        excluded: Set<String>,
        premium: Boolean,
        paywall: Boolean,
    ): Boolean {
        if (!paywall || premium) return false
        return ((existing + proposed) - excluded).size > FREE_APP_LIMIT
    }
}
