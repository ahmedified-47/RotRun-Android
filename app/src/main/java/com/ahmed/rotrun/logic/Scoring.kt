package com.ahmed.rotrun.logic

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class BrainHealthLevel(
    val label: String,
    val headline: String,
    val goalLabel: String,
    val goalMinutes: Int,
    val restoreMinutes: Int,
    val restoreCopy: String,
) {
    Easy(
        "Easy",
        "More forgiving. Recommended.",
        "5h daily goal",
        5 * 60,
        8,
        "Focus Mode restores 1 health every 8 min",
    ),
    Medium(
        "Medium",
        "Balanced. Brain decays at a steady pace.",
        "3h daily goal",
        3 * 60,
        6,
        "Focus Mode restores 1 health every 6 min",
    ),
    Hard(
        "Hard",
        "Strict. Brain reacts quickly.",
        "1h daily goal",
        60,
        4,
        "Focus Mode restores 1 health every 4 min",
    ),
    ;

    companion object {
        fun fromLabel(raw: String): BrainHealthLevel = entries.firstOrNull { it.label == raw } ?: Medium
    }
}

enum class BrainRank(val title: String, val badge: String, val flavor: String, val receipt: String) {
    Rotter("Rotter", "ROTTING", "Scroll debt is eating the signal.", "Brain fried. Scroll debt outstanding."),
    Sloth("Sloth", "IDLE", "Half-awake. Stay under your goal.", "Half-awake. Participation trophy energy."),
    Active("Active", "LOCKED IN", "Locked in. Keep rotting blocked.", "Somehow locked in. Keep it that way."),
    CosmicMind("Cosmic Mind", "COSMIC", "Clarity is maxed. Protect the streak.", "Cosmic. This receipt is a compliment."),
}

object Products {
    const val MONTHLY = "com.ahmed.rotrun.subscription.monthly"
    const val YEARLY = "com.ahmed.rotrun.subscription.yearly"
    const val YEARLY70 = "com.ahmed.rotrun.subscription.yearly70"
    const val MONTHLY_CENTS = 699
    const val YEARLY_CENTS = 7999
    const val YEARLY_INTRO_CENTS = 3999
    const val YEARLY70_INTRO_CENTS = 2399

    fun planName(id: String): String = when (id) {
        MONTHLY -> "Monthly"
        YEARLY -> "Yearly"
        YEARLY70 -> "Yearly offer"
        else -> "RotRun Premium"
    }
}

object Scoring {
    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun dayKey(date: LocalDate = LocalDate.now()): String = date.format(dayFormatter)

    fun parseDay(key: String): LocalDate? = runCatching { LocalDate.parse(key, dayFormatter) }.getOrNull()

    /** iOS weekday: Sunday = 1 … Saturday = 7. Thursday is 5. */
    fun thursdayStart(date: LocalDate = LocalDate.now()): LocalDate {
        val javaDow = date.dayOfWeek.value
        val iosWeekday = if (javaDow == 7) 1 else javaDow + 1
        val daysFromThursday = (iosWeekday + 7 - 5) % 7
        return date.minusDays(daysFromThursday.toLong())
    }

    fun thursdayWeek(date: LocalDate = LocalDate.now()): List<LocalDate> {
        val start = thursdayStart(date)
        return (0..6).map { start.plusDays(it.toLong()) }
    }

    fun drainHealth(todaySeconds: Long, level: BrainHealthLevel): Int {
        val goalSeconds = level.goalMinutes * 60.0
        val score = 100 - (todaySeconds.coerceAtLeast(0) / goalSeconds) * 50
        return score.roundToInt().coerceIn(0, 100)
    }

    fun displayedHealth(drain: Int, todayFocusMinutes: Int, restoreIntervalMinutes: Int): Int {
        val points = todayFocusMinutes.coerceAtLeast(0) / restoreIntervalMinutes.coerceAtLeast(1)
        return (drain + points).coerceIn(0, 100)
    }

    fun caption(percentage: Int): String = when (percentage) {
        in 85..100 -> "Looking sharp today"
        in 65..84 -> "Holding steady"
        in 40..64 -> "A little fried"
        else -> "Time to unplug"
    }

    fun dailyXp(screenMinutes: Int, goalMinutes: Int, askBrainCount: Int, ransomDebit: Int = 0): Int {
        val delta = goalMinutes.coerceAtLeast(0) - screenMinutes.coerceAtLeast(0)
        val screenXp = if (delta >= 0) delta * 10 else delta * 15
        return (screenXp - 20 * askBrainCount.coerceAtLeast(0) - ransomDebit.coerceAtLeast(0)).coerceAtLeast(0)
    }

    fun rank(score: Int): BrainRank = when (score.coerceAtLeast(0)) {
        in 0..499 -> BrainRank.Rotter
        in 500..1499 -> BrainRank.Sloth
        in 1500..2999 -> BrainRank.Active
        else -> BrainRank.CosmicMind
    }

    fun formatDuration(seconds: Long): String {
        val minutesTotal = (seconds.coerceAtLeast(0) / 60.0).roundToInt()
        val hours = minutesTotal / 60
        val minutes = minutesTotal % 60
        return when {
            hours == 0 -> "${minutes}m"
            minutes == 0 -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    fun formatClock(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
        } else {
            "%d:%02d".format(Locale.US, minutes, seconds)
        }
    }

    fun usd(cents: Int): String = "$" + String.format(Locale.US, "%.2f", cents / 100.0)

    fun savePercent(priceCents: Int): Int {
        val base = Products.MONTHLY_CENTS * 12
        if (priceCents >= base) return 0
        return ((base - priceCents) * 100.0 / base).roundToInt()
    }

    fun comparisonLine(todaySeconds: Long, avgSeconds: Long, diffPercent: Int, below: Boolean): String {
        if (todaySeconds <= 0 && avgSeconds <= 0) return "Waiting for this week’s Screen Time."
        val direction = if (below) "less" else "more"
        return "Today you’ve spent $diffPercent% $direction screen time than your 7-day average."
    }

    fun paceLine(ahead: Boolean): String = if (ahead) {
        "You’re using your phone more than you usually do by this point."
    } else {
        "You’re using your phone less than you usually do by this point."
    }

    fun weeklyLine(avgSeconds: Long): String =
        "You spent an average of ${formatDuration(avgSeconds)} a day over the last 7 days."

    fun diffPercent(today: Long, average: Long): Int {
        if (average <= 0L) return 0
        return ((abs(today - average).toDouble() / average.toDouble()) * 100).roundToInt()
    }

    fun aheadOfPace(todaySeconds: Long, avgSeconds: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (avgSeconds <= 0L) return false
        val zone = ZoneId.systemDefault()
        val minutes = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime().toSecondOfDay() / 60
        val expected = avgSeconds * (minutes.coerceAtLeast(1) / (24.0 * 60.0))
        return todaySeconds > expected * 1.02
    }

    fun hourLabel(hour: Int, compact: Boolean = false): String {
        val h = ((hour % 24) + 24) % 24
        val suffix = if (h < 12) "am" else "pm"
        val hr = when (val twelve = h % 12) {
            0 -> 12
            else -> twelve
        }
        return if (compact) "$hr$suffix" else "$hr $suffix"
    }

    fun daysLabel(days: List<Int>): String {
        val set = days.toSet()
        return when (set) {
            (0..6).toSet() -> "All"
            setOf(1, 2, 3, 4, 5) -> "Weekdays"
            setOf(0, 6) -> "Weekends"
            else -> "${set.size} days"
        }
    }

    fun estimateCopy(hours: Int): String = when {
        hours <= 2 -> "That's a healthy starting point."
        hours <= 4 -> "There's room to reclaim some of your day."
        hours <= 8 -> "This is a significant percentage of your life."
        else -> "That's most of your waking life. RotRun can help."
    }

    fun goalCopy(estimated: Int, goal: Int): String {
        val saved = estimated - goal
        return if (saved > 0) "Great! You will save $saved hours per day!"
        else "A focused target helps RotRun score your Brain Health."
    }
}
