package com.ahmed.rotrun

import com.ahmed.rotrun.logic.BrainHealthLevel
import com.ahmed.rotrun.logic.BrainRank
import com.ahmed.rotrun.logic.Products
import com.ahmed.rotrun.logic.Scoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScoringTest {
    @Test
    fun drainHitsFiftyAtTheDailyGoal() {
        val goalSeconds = BrainHealthLevel.Medium.goalMinutes * 60L
        assertEquals(100, Scoring.drainHealth(0, BrainHealthLevel.Medium))
        assertEquals(50, Scoring.drainHealth(goalSeconds, BrainHealthLevel.Medium))
        assertEquals(0, Scoring.drainHealth(goalSeconds * 2, BrainHealthLevel.Medium))
    }

    @Test
    fun focusRestoresOnePointPerInterval() {
        assertEquals(58, Scoring.displayedHealth(50, 48, 6))
        assertEquals(100, Scoring.displayedHealth(99, 30, 4))
    }

    @Test
    fun captionsMatchBands() {
        assertEquals("Looking sharp today", Scoring.caption(89))
        assertEquals("Holding steady", Scoring.caption(70))
        assertEquals("A little fried", Scoring.caption(50))
        assertEquals("Time to unplug", Scoring.caption(20))
    }

    @Test
    fun dailyXpAndRank() {
        assertEquals(2400, Scoring.dailyXp(0, 240, 0))
        assertEquals(BrainRank.Active, Scoring.rank(2400))
        assertEquals(80, Scoring.dailyXp(230, 240, 1))
        assertEquals(2380, Scoring.dailyXp(0, 240, 1))
        assertEquals(2300, Scoring.dailyXp(0, 240, 0, 100))
        assertEquals(0, Scoring.dailyXp(260, 240, 0))
        assertEquals(0, Scoring.dailyXp(0, 240, 0, 9_999))
        assertEquals(BrainRank.Rotter, Scoring.rank(0))
        assertEquals(BrainRank.CosmicMind, Scoring.rank(3000))
        assertEquals(240, Scoring.goalMinutes(4))
        assertEquals(240, Scoring.goalMinutes(0))
        assertEquals(300, BrainHealthLevel.Easy.goalMinutes)
        assertEquals(180, BrainHealthLevel.Medium.goalMinutes)
        assertEquals(60, BrainHealthLevel.Hard.goalMinutes)
        assertEquals(1.0, BrainHealthLevel.Easy.xpMultiplier, 0.0)
        assertEquals(1.25, BrainHealthLevel.Medium.xpMultiplier, 0.0)
        assertEquals(1.5, BrainHealthLevel.Hard.xpMultiplier, 0.0)
        assertEquals(1, Scoring.screenMinutes(90))
        assertEquals(1, Scoring.screenMinutes(119))
        assertEquals(1, Scoring.scoredMinutes(90, authorized = true))
        assertEquals(null, Scoring.scoredMinutes(59, authorized = true))
        assertEquals(null, Scoring.scoredMinutes(3_600, authorized = false))
    }

    @Test
    fun eachGoalScoresUnderAndOver() {
        assertEquals(2000, Scoring.dailyXp(100, BrainHealthLevel.Easy.goalMinutes, 0, 0, BrainHealthLevel.Easy.xpMultiplier))
        assertEquals(0, Scoring.dailyXp(360, BrainHealthLevel.Easy.goalMinutes, 0, 0, BrainHealthLevel.Easy.xpMultiplier))
        assertEquals(1500, Scoring.dailyXp(60, BrainHealthLevel.Medium.goalMinutes, 0, 0, BrainHealthLevel.Medium.xpMultiplier))
        assertEquals(13, Scoring.dailyXp(179, BrainHealthLevel.Medium.goalMinutes, 0, 0, BrainHealthLevel.Medium.xpMultiplier))
        assertEquals(0, Scoring.dailyXp(200, BrainHealthLevel.Medium.goalMinutes, 0, 0, BrainHealthLevel.Medium.xpMultiplier))
        assertEquals(600, Scoring.dailyXp(20, BrainHealthLevel.Hard.goalMinutes, 0, 0, BrainHealthLevel.Hard.xpMultiplier))
        assertEquals(0, Scoring.dailyXp(80, BrainHealthLevel.Hard.goalMinutes, 0, 0, BrainHealthLevel.Hard.xpMultiplier))
        assertEquals(1880, Scoring.dailyXp(100, BrainHealthLevel.Easy.goalMinutes, 1, 100, BrainHealthLevel.Easy.xpMultiplier))
    }

    @Test
    fun goalStreakBonusCapsAtFiveDays() {
        val goal = BrainHealthLevel.Medium.goalMinutes
        val rate = BrainHealthLevel.Medium.xpMultiplier
        assertEquals(1500, Scoring.dailyXp(60, goal, 0, 0, rate, 0))
        assertEquals(1650, Scoring.dailyXp(60, goal, 0, 0, rate, 1))
        assertEquals(1950, Scoring.dailyXp(60, goal, 0, 0, rate, 3))
        assertEquals(2250, Scoring.dailyXp(60, goal, 0, 0, rate, 5))
        assertEquals(2250, Scoring.dailyXp(60, goal, 0, 0, rate, 7))
        assertEquals(14, Scoring.dailyXp(179, goal, 0, 0, rate, 1))
        val over = Scoring.dailyXp(200, goal, 0, 0, rate, 7)
        assertEquals(0, over)
        assertEquals(Scoring.dailyXp(200, goal, 0, 0, rate, 0), over)
        assertNull(Scoring.streakLine(0))
        assertEquals("1-day streak +10%", Scoring.streakLine(1))
        assertEquals("3-day streak +30%", Scoring.streakLine(3))
        assertEquals("7-day streak +50%", Scoring.streakLine(7))
        assertEquals("easy", BrainHealthLevel.Easy.wireDifficulty)
        assertEquals("medium", BrainHealthLevel.Medium.wireDifficulty)
        assertEquals("hard", BrainHealthLevel.Hard.wireDifficulty)
    }

    @Test
    fun goalStreakStopsOnAGapOrANull() {
        val today = LocalDate.of(2026, 11, 15)
        fun day(offset: Long, met: Boolean?) = Scoring.PastGoal(today.minusDays(offset), met)
        assertEquals(0, Scoring.goalStreakDays(today, emptyList()))
        assertEquals(1, Scoring.goalStreakDays(today, listOf(day(1, true))))
        assertEquals(3, Scoring.goalStreakDays(today, (1L..3L).map { day(it, true) }))
        assertEquals(5, Scoring.goalStreakDays(today, (1L..5L).map { day(it, true) }))
        assertEquals(7, Scoring.goalStreakDays(today, (1L..7L).map { day(it, true) }))
        assertEquals(2, Scoring.goalStreakDays(today, listOf(day(1, true), day(2, true), day(4, true))))
        assertEquals(1, Scoring.goalStreakDays(today, listOf(day(1, true), day(2, null), day(3, true))))
        assertEquals(0, Scoring.goalStreakDays(today, listOf(day(1, false), day(2, true))))
        assertEquals(1, Scoring.goalStreakDays(today, listOf(day(0, true), day(1, true))))
        assertEquals(4, Scoring.streakAfterFetch(null, 4, today))
        assertEquals(0, Scoring.streakAfterFetch(emptyList(), 4, today))
        assertEquals(
            "/rest/v1/daily_xp?select=day,met_goal&user_id=eq.user-1&day=lt.2026-11-15&order=day.desc&limit=7",
            Scoring.goalStreakPath("user-1", "2026-11-15"),
        )
    }

    @Test
    fun standInMinutesAreNotMeasured() {
        assertNull(Scoring.measuredMinutes(Scoring.PLACEHOLDER_MINUTES))
        assertNull(Scoring.measuredMinutes(Scoring.COVERAGE_CAP_MINUTES))
        assertNull(Scoring.measuredMinutes(1441))
        assertNull(Scoring.measuredMinutes(null))
        assertEquals(100, Scoring.measuredMinutes(100))
        val today = "2026-11-15"
        assertNull(Scoring.nextDailyXp(today, Scoring.PLACEHOLDER_MINUTES, 0, 0, BrainHealthLevel.Easy, "user", null))
        assertNull(Scoring.nextDailyXp(today, Scoring.COVERAGE_CAP_MINUTES, 0, 0, BrainHealthLevel.Easy, "user", null))
        val stored = Scoring.DailyXpPost(today, 1500, 60, 0, 0, BrainHealthLevel.Medium.label, "user")
        assertNull(Scoring.nextDailyXp(today, Scoring.PLACEHOLDER_MINUTES, 0, 0, BrainHealthLevel.Medium, "user", stored))
    }

    @Test
    fun staleRefreshDoesNotLowerTheSameDayScore() {
        val today = "2026-11-14"
        val stored = Scoring.DailyXpPost(today, 1500, 60, 0, 0, BrainHealthLevel.Medium.label, "user")
        assertNull(Scoring.nextDailyXp(today, null, 0, 0, BrainHealthLevel.Medium, "user", stored))
        val shrunk = Scoring.nextDailyXp(today, 10, 0, 0, BrainHealthLevel.Medium, "user", stored)
        assertNull(shrunk)
        val moreUse = Scoring.nextDailyXp(today, 120, 0, 0, BrainHealthLevel.Medium, "user", stored)
        assertEquals(750, moreUse?.xp)
        val asked = Scoring.nextDailyXp(today, null, 1, 0, BrainHealthLevel.Medium, "user", stored)
        assertEquals(1480, asked?.xp)
        assertEquals(60, asked?.minutes)
        val paid = Scoring.nextDailyXp(today, null, 0, 100, BrainHealthLevel.Medium, "user", stored)
        assertEquals(1400, paid?.xp)
        val tomorrow = Scoring.nextDailyXp("2026-11-15", 170, 0, 0, BrainHealthLevel.Medium, "user", stored)
        assertEquals(125, tomorrow?.xp)
        assertNull(Scoring.nextDailyXp("2026-11-15", null, 0, 0, BrainHealthLevel.Medium, "user", stored))
        assertEquals(stored, Scoring.DailyXpPost.decode(stored.encode()))
    }

    @Test
    fun bailoutIsHalfTheZeroMinuteCapWithoutStreak() {
        assertEquals(3000, Scoring.underGoalCap(BrainHealthLevel.Easy.goalMinutes, BrainHealthLevel.Easy.xpMultiplier))
        assertEquals(2250, Scoring.underGoalCap(BrainHealthLevel.Medium.goalMinutes, BrainHealthLevel.Medium.xpMultiplier))
        assertEquals(900, Scoring.underGoalCap(BrainHealthLevel.Hard.goalMinutes, BrainHealthLevel.Hard.xpMultiplier))
        assertEquals(1500, Scoring.bailoutReward(BrainHealthLevel.Easy))
        assertEquals(1125, Scoring.bailoutReward(BrainHealthLevel.Medium))
        assertEquals(450, Scoring.bailoutReward(BrainHealthLevel.Hard))
        assertEquals(4380, Scoring.dailyXp(0, 300, 1, 100, 1.0, 0, 1500))
        assertEquals(2505, Scoring.dailyXp(60, 180, 1, 100, 1.25, 0, 1125))
        assertEquals(0, Scoring.dailyXp(200, 60, 0, 0, 1.5, 0, 450))
        assertEquals(6000, Scoring.dailyXp(0, 300, 0, 0, 1.0, 5, 1500))
        val today = "2026-11-15"
        val base = Scoring.nextDailyXp(today, 0, 0, 0, BrainHealthLevel.Easy, "user", null)
        assertEquals(3000, base?.xp)
        val paid = Scoring.nextDailyXp(today, 0, 0, 0, BrainHealthLevel.Easy, "user", base, bailout = 1500)
        assertEquals(4500, paid?.xp)
        assertNull(Scoring.nextDailyXp(today, 0, 0, 0, BrainHealthLevel.Easy, "user", paid, bailout = 0))
    }

    @Test
    fun thursdayWeekStartsOnThursday() {
        assertEquals(LocalDate.of(2026, 10, 22), Scoring.thursdayStart(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun savePercentUsesTwelveMonthsOfTheMonthlyPrice() {
        assertEquals(50, Scoring.savePercent(1_000_000L, 6_000_000L))
        assertEquals(0, Scoring.savePercent(1_000_000L, 12_000_000L))
        assertEquals(70, Scoring.discountPercent(10_000_000L, 3_000_000L))
    }

    @Test
    fun brainHealthMatchesIosTotalScreenTime() {
        val today = (1.5 * 3600).toLong()
        assertEquals(75, Scoring.drainHealth(today, BrainHealthLevel.Medium))
        assertEquals(83, Scoring.shownHealth(true, today, 48, BrainHealthLevel.Medium))
        assertEquals(100, Scoring.shownHealth(true, 30, 0, BrainHealthLevel.Easy))
        assertEquals(0, Scoring.drainHealth(BrainHealthLevel.Hard.goalMinutes * 60L * 2, BrainHealthLevel.Hard))
    }

    @Test
    fun oneAndAHalfHoursAtMediumIsSeventyFive() {
        assertEquals(75, Scoring.drainHealth((1.5 * 3600).toLong(), BrainHealthLevel.Medium))
        assertEquals("Holding steady", Scoring.caption(75))
    }

    @Test
    fun healthIsUnknownUntilUsageIsConnected() {
        assertEquals(null, Scoring.shownHealth(false, 0, 0, BrainHealthLevel.Medium))
        assertEquals(null, Scoring.shownHealth(false, 3 * 3600, 40, BrainHealthLevel.Medium))
    }

    @Test
    fun healthShowsBelowOneMinuteOfUsage() {
        assertEquals(100, Scoring.shownHealth(true, 30, 0, BrainHealthLevel.Medium))
        assertEquals(75, Scoring.shownHealth(true, (1.5 * 3600).toLong(), 0, BrainHealthLevel.Medium))
    }

    @Test
    fun streakAdvancesOnlyOnTheNextDay() {
        assertEquals(5, Scoring.nextOpenStreak("2026-10-31", 4, "2026-11-01", "2026-10-31"))
        assertEquals(4, Scoring.nextOpenStreak("2026-11-01", 4, "2026-11-01", "2026-10-31"))
        assertEquals(1, Scoring.nextOpenStreak("2026-10-30", 9, "2026-11-01", "2026-10-31"))
        assertEquals(1, Scoring.nextOpenStreak("", 0, "2026-11-01", "2026-10-31"))
    }

    @Test
    fun durationFormat() {
        assertEquals("5m", Scoring.formatDuration(5 * 60L))
        assertEquals("2h", Scoring.formatDuration(2 * 3600L))
        assertEquals("2h 1m", Scoring.formatDuration(2 * 3600L + 60L))
        assertEquals("1m", Scoring.formatDuration(90))
        assertEquals("1m", Scoring.formatDuration(119))
    }
}
