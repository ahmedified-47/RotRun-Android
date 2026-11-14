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
