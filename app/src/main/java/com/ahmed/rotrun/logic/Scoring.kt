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
    val xpMultiplier: Double,
) {
    Easy(
        "Easy",
        "More forgiving. Recommended.",
        "5h daily goal",
        5 * 60,
        8,
        "Focus Mode restores 1 health every 8 min",
        1.0,
    ),
    Medium(
        "Medium",
        "Balanced. Brain decays at a steady pace.",
        "3h daily goal",
        3 * 60,
        6,
        "Focus Mode restores 1 health every 6 min",
        1.25,
    ),
    Hard(
        "Hard",
        "Strict. Brain reacts quickly.",
        "1h daily goal",
        1 * 60,
        4,
        "Focus Mode restores 1 health every 4 min",
        1.5,
    ),
    ;

    companion object {
        fun fromLabel(raw: String): BrainHealthLevel = entries.firstOrNull { it.label == raw } ?: Medium

        /** Goal hours from onboarding: 2 or under is Hard, 3–4 is Medium, otherwise Easy. */
        fun matching(hours: Int): BrainHealthLevel = when {
            hours <= 2 -> Hard
            hours <= 4 -> Medium
            else -> Easy
        }
    }
}

enum class BrainRank(val title: String, val badge: String, val flavor: String, val receipt: String) {
    Rotter("Rotter", "ROTTING", "Scroll debt is eating the signal.", "Brain fried. Scroll debt outstanding."),
    Sloth("Sloth", "IDLE", "Half-awake. Stay under your goal.", "Half-awake. Participation trophy energy."),
    Active("Active", "LOCKED IN", "Locked in. Keep rotting blocked.", "Somehow locked in. Keep it that way."),
    CosmicMind("Cosmic Mind", "COSMIC", "Clarity is maxed. Protect the streak.", "Cosmic. This receipt is a compliment."),
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

    /**
     * One Brain Health number for Home, Stats, and the widget.
     * [todaySeconds] is total screen time, the same input as iOS
     * `ScreenTimeFormatting.brainHealth(today:)`. The excluded-app drain is not used.
     * Missing usage access is unknown, not 0% and not 100%.
     * There is no minimum number of seconds before a real score is shown.
     */
    fun shownHealth(authorized: Boolean, todaySeconds: Long, focusMinutes: Int, level: BrainHealthLevel): Int? {
        if (!authorized) return null
        return displayedHealth(drainHealth(todaySeconds, level), focusMinutes, level.restoreMinutes)
    }

    /** Streak after an open. Same calendar day keeps it. The next day adds one. A gap starts over. */
    fun nextOpenStreak(lastOpenDay: String, streak: Int, today: String, yesterday: String): Int = when (lastOpenDay) {
        "", today -> maxOf(1, streak)
        yesterday -> streak + 1
        else -> 1
    }

    fun caption(percentage: Int): String = when (percentage) {
        in 85..100 -> "Looking sharp today"
        in 65..84 -> "Holding steady"
        in 40..64 -> "A little fried"
        else -> "Time to unplug"
    }

    /** Floor of seconds, the same minutes the Home card prints once it has a minute to show. */
    fun screenMinutes(seconds: Long): Int = (seconds.coerceAtLeast(0) / 60L).toInt()

    /**
     * Minutes the Home card is actually showing. Under a minute the card says
     * "Not in yet", and that is not scored as zero use.
     */
    fun scoredMinutes(seconds: Long, authorized: Boolean): Int? {
        if (!authorized || seconds < 60L) return null
        return screenMinutes(seconds)
    }

    /** Stored goal hours times 60. A missing goal is 4 hours. Daily XP uses [BrainHealthLevel.goalMinutes] instead. */
    fun goalMinutes(storedHours: Int): Int = (if (storedHours > 0) storedHours else 4) * 60

    /**
     * Under the goal: (goal − used) × 10 × [multiplier], nearest int.
     * Over the goal: (used − goal) × 15, with no multiplier.
     * Then −20 per Ask and −100 per ransom, floored at 0.
     */
    fun dailyXp(
        screenMinutes: Int,
        goalMinutes: Int,
        askBrainCount: Int,
        ransomDebit: Int = 0,
        multiplier: Double = 1.0,
    ): Int {
        val used = screenMinutes.coerceAtLeast(0)
        val goal = goalMinutes.coerceAtLeast(0)
        val screenXp = if (used <= goal) {
            ((goal - used) * 10.0 * multiplier).roundToInt()
        } else {
            -((used - goal) * 15)
        }
        val ask = InterventionXp.ASK_XP * askBrainCount.coerceAtLeast(0)
        return (screenXp - ask - ransomDebit.coerceAtLeast(0)).coerceAtLeast(0)
    }

    /**
     * Last Daily XP written for one user on one local day.
     * A later refresh uses it so missing or smaller minutes cannot replace a higher score.
     */
    data class DailyXpPost(
        val day: String,
        val xp: Int,
        val minutes: Int,
        val askCount: Int,
        val ransomDebit: Int,
        val level: String,
        val userId: String,
    ) {
        fun encode(): String = listOf(day, xp, minutes, askCount, ransomDebit, level, userId).joinToString("|")

        companion object {
            fun decode(raw: String?): DailyXpPost? {
                if (raw.isNullOrBlank()) return null
                val parts = raw.split('|')
                if (parts.size != 7 || parts[0].length != 10 || parts[5].isBlank()) return null
                return DailyXpPost(
                    day = parts[0],
                    xp = parts[1].toIntOrNull() ?: return null,
                    minutes = parts[2].toIntOrNull() ?: return null,
                    askCount = parts[3].toIntOrNull() ?: return null,
                    ransomDebit = parts[4].toIntOrNull() ?: return null,
                    level = parts[5],
                    userId = parts[6],
                )
            }
        }
    }

    /**
     * Score to upsert, or null when this refresh must not write.
     * Missing minutes, or a smaller minute total than the one already stored today, are not a real drop.
     * More minutes, a new Ask, a new ransom, or a new difficulty may lower today's score.
     * A new local day does not keep yesterday's score.
     */
    fun nextDailyXp(
        today: String,
        minutesLoaded: Int?,
        askCount: Int,
        ransomDebit: Int,
        level: BrainHealthLevel,
        userId: String,
        previous: DailyXpPost?,
    ): DailyXpPost? {
        val prior = previous?.takeIf { it.userId == userId && it.day == today }
        val loaded = minutesLoaded?.coerceAtLeast(0)
        val minutes = when {
            loaded == null -> prior?.minutes
            prior != null && loaded < prior.minutes -> prior.minutes
            else -> loaded
        } ?: return null
        val candidate = dailyXp(minutes, level.goalMinutes, askCount, ransomDebit, level.xpMultiplier)
        val post = DailyXpPost(today, candidate, minutes, askCount, ransomDebit, level.label, userId)
        if (prior == null) return if (loaded == null) null else post
        val usageGrew = loaded != null && loaded > prior.minutes
        val ledgerChanged = askCount != prior.askCount || ransomDebit != prior.ransomDebit
        val goalChanged = level.label != prior.level
        if (candidate < prior.xp && !usageGrew && !ledgerChanged && !goalChanged) return null
        if (candidate == prior.xp && !usageGrew && !ledgerChanged && !goalChanged) return null
        return post
    }

    fun rank(score: Int): BrainRank = when (score.coerceAtLeast(0)) {
        in 0..499 -> BrainRank.Rotter
        in 500..1499 -> BrainRank.Sloth
        in 1500..2999 -> BrainRank.Active
        else -> BrainRank.CosmicMind
    }

    fun formatDuration(seconds: Long): String {
        val minutesTotal = screenMinutes(seconds)
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

    /** Percent saved by a yearly price versus 12 times the monthly price. Zero when it is not cheaper. */
    fun savePercent(monthlyMicros: Long, yearlyMicros: Long): Int {
        val annual = monthlyMicros * 12
        if (annual <= 0L || yearlyMicros <= 0L || yearlyMicros >= annual) return 0
        return ((annual - yearlyMicros) * 100.0 / annual).roundToInt()
    }

    /** Whole percent off a regular price. Zero when there is no real saving. */
    fun discountPercent(regularMicros: Long, offerMicros: Long): Int {
        if (regularMicros <= 0L || offerMicros <= 0L || offerMicros >= regularMicros) return 0
        return ((regularMicros - offerMicros) * 100.0 / regularMicros).roundToInt()
    }

    fun visibleDuration(seconds: Long): String = if (seconds < 60L) "Not in yet" else formatDuration(seconds)

    fun visibleCount(count: Int): String = if (count <= 0) "Not in yet" else count.toString()

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
