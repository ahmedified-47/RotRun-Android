package com.ahmed.rotrun

import com.ahmed.rotrun.logic.InterventionXp
import com.ahmed.rotrun.logic.Scoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterventionXpTest {
    private val today = "2026-11-11"
    private val open = InterventionXp.DayLedger(xpDay = today, askBrainCount = 1, ransomDebit = 0, askOpen = true, lastAskToken = 42L)

    @Test
    fun showingThePopupCountsOnceAndAReshowDoesNot() {
        val first = InterventionXp.presentAsk(InterventionXp.DayLedger(xpDay = today), eventId = 42L, today = today)
        assertEquals(1, first.askBrainCount)
        assertTrue(first.askOpen)
        val again = InterventionXp.presentAsk(first, eventId = 99L, today = today)
        assertEquals(1, again.askBrainCount)
        val relaunch = InterventionXp.presentAsk(first.copy(askOpen = true), eventId = 42L, today = today)
        assertEquals(1, relaunch.askBrainCount)
    }

    @Test
    fun theSameTokenDoesNotCountAgainAfterRelaunch() {
        val shown = InterventionXp.presentAsk(InterventionXp.DayLedger(xpDay = today), eventId = 42L, today = today)
        val restored = shown.copy(askOpen = false)
        val again = InterventionXp.presentAsk(restored, eventId = 42L, today = today)
        assertEquals(1, again.askBrainCount)
    }

    @Test
    fun nahDoesNotChangeTheScore() {
        val left = InterventionXp.dismiss(open)
        assertEquals(1, left.askBrainCount)
        assertEquals(0, left.ransomDebit)
        assertFalse(left.askOpen)
        assertEquals(2380, Scoring.dailyXp(0, 240, left.askBrainCount, left.ransomDebit))
    }

    @Test
    fun timedUnlocksDoNotDeductAgain() {
        listOf(10, 20, 30).forEach { minutes ->
            val unlocked = InterventionXp.unlock(open, minutes)
            assertEquals(1, unlocked.askBrainCount)
            assertEquals(0, unlocked.ransomDebit)
            assertFalse(unlocked.askOpen)
        }
    }

    @Test
    fun ransomAddsOneHundredAndASecondPaymentAddsAnother() {
        val paid = InterventionXp.payRansom(open, today)
        assertEquals(1, paid.askBrainCount)
        assertEquals(100, paid.ransomDebit)
        assertTrue(paid.askOpen)
        val again = InterventionXp.payRansom(paid, today)
        assertEquals(200, again.ransomDebit)
        assertEquals(2180, Scoring.dailyXp(0, 240, again.askBrainCount, again.ransomDebit))
    }

    @Test
    fun localMidnightClearsTheCounters() {
        val rolled = InterventionXp.roll(open.copy(ransomDebit = 100), "2026-11-12")
        assertEquals(0, rolled.askBrainCount)
        assertEquals(0, rolled.ransomDebit)
        assertFalse(rolled.askOpen)
        val next = InterventionXp.presentAsk(rolled, eventId = 7L, today = "2026-11-12")
        assertEquals(1, next.askBrainCount)
    }
}
