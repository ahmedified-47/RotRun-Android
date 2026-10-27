package com.ahmed.rotrun

import com.ahmed.rotrun.logic.BrainHealthLevel
import com.ahmed.rotrun.logic.BrainRank
import com.ahmed.rotrun.logic.Products
import com.ahmed.rotrun.logic.Scoring
import org.junit.Assert.assertEquals
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
        assertEquals(0, Scoring.dailyXp(260, 240, 0))
        assertEquals(BrainRank.Rotter, Scoring.rank(0))
        assertEquals(BrainRank.CosmicMind, Scoring.rank(3000))
    }

    @Test
    fun thursdayWeekStartsOnThursday() {
        assertEquals(LocalDate.of(2026, 10, 22), Scoring.thursdayStart(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun yearlySavePercentMatchesStoreKitPrices() {
        assertEquals(5, Scoring.savePercent(Products.YEARLY_CENTS))
    }

    @Test
    fun durationFormat() {
        assertEquals("5m", Scoring.formatDuration(5 * 60L))
        assertEquals("2h", Scoring.formatDuration(2 * 3600L))
        assertEquals("2h 1m", Scoring.formatDuration(2 * 3600L + 60L))
    }
}
