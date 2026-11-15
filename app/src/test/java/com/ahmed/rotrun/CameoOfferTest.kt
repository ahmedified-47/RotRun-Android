package com.ahmed.rotrun

import com.ahmed.rotrun.logic.CameoOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameoOfferTest {
    @Test
    fun cameoIsAbsentAboveZeroHealth() {
        assertFalse(CameoOffer.lastResort(80, false, false, false, false, hasFriend = true))
        assertEquals("standard", CameoOffer.mode(80, false, false, false, false, hasFriend = true))
        assertEquals("standard", CameoOffer.mode(null, false, false, false, false, hasFriend = true))
        repeat(24) {
            val picked = CameoOffer.mode(40, true, true, true, true, hasFriend = true)
            assertTrue(picked in setOf("standard", "breathing", "mirror", "quick"))
        }
    }

    @Test
    fun zeroHealthStillRunsAnEnabledStandardStyle() {
        assertFalse(CameoOffer.standardStylesUnusable(standard = true, breathing = false, mirror = false, quick = false))
        assertEquals("standard", CameoOffer.mode(0, true, false, false, false, hasFriend = true))
        assertEquals("breathing", CameoOffer.mode(0, false, true, false, false, hasFriend = true))
        assertEquals("mirror", CameoOffer.mode(0, false, false, true, false, hasFriend = true))
        assertEquals("quick", CameoOffer.mode(0, false, false, false, true, hasFriend = true))
        assertFalse(CameoOffer.standardInterventionFailed(0, continuedFromStandard = false, true, false, false, false, hasFriend = true))
    }

    @Test
    fun continuingAStandardStyleAtZeroHealthShowsCameoBeforeOpen() {
        listOf("standard", "breathing", "mirror", "quick").forEach { style ->
            assertTrue(CameoOffer.isStandardStyle(style))
        }
        assertFalse(CameoOffer.isStandardStyle("cameo"))
        assertTrue(CameoOffer.interceptStandardContinue(health = 0, hasFriend = true))
        assertFalse(CameoOffer.interceptStandardContinue(health = 1, hasFriend = true))
        assertFalse(CameoOffer.interceptStandardContinue(health = null, hasFriend = true))
        assertFalse(CameoOffer.interceptStandardContinue(health = 0, hasFriend = false))
        assertTrue(CameoOffer.standardInterventionFailed(0, continuedFromStandard = true, true, true, true, true, hasFriend = true))
        assertFalse(CameoOffer.standardInterventionFailed(8, continuedFromStandard = true, true, false, false, false, hasFriend = true))
        assertFalse(CameoOffer.standardInterventionFailed(0, continuedFromStandard = true, true, false, false, false, hasFriend = false))
        assertEquals("standard", CameoOffer.mode(0, true, false, false, false, hasFriend = true))
        assertEquals(1500, payout(health = 0, hasFriend = true, standard = true, continued = true))
        assertEquals(0, payout(health = 0, hasFriend = true, standard = true, continued = true, choice = CameoOffer.Confirm.Open))
        assertEquals(0, payout(health = 3, hasFriend = true, standard = true, continued = true))
        assertEquals(0, payout(health = 0, hasFriend = false, standard = true, continued = true))
    }

    @Test
    fun zeroHealthWithNoStandardStyleAndAFriendOffersCameo() {
        assertTrue(CameoOffer.standardStylesUnusable(false, false, false, false))
        assertTrue(CameoOffer.lastResort(0, false, false, false, false, hasFriend = true))
        assertEquals("cameo", CameoOffer.mode(0, false, false, false, false, hasFriend = true))
        assertFalse(CameoOffer.pool(true, true, true, true).contains("cameo"))
        assertEquals(listOf("standard", "breathing", "mirror", "quick"), CameoOffer.pool(true, true, true, true))
    }

    @Test
    fun zeroHealthWithoutAFriendStaysOnTheNormalFlow() {
        assertFalse(CameoOffer.lastResort(0, false, false, false, false, hasFriend = false))
        assertEquals("standard", CameoOffer.mode(0, false, false, false, false, hasFriend = false))
    }

    @Test
    fun confirmAsksBeforeOpeningAndOnlyAWalkAwayPays() {
        assertEquals(
            "You're at 0 XP. Do you want to continue and open the app?",
            CameoOffer.confirmQuestion,
        )
        assertFalse(CameoOffer.opensApp(CameoOffer.Confirm.WalkAway))
        assertTrue(CameoOffer.opensApp(CameoOffer.Confirm.Open))
        val open = CameoOffer.confirmPayout(
            CameoOffer.Confirm.Open,
            health = 0,
            standard = false,
            breathing = false,
            mirror = false,
            quick = false,
            hasFriend = true,
            reward = 1500,
            alreadyPaid = false,
        )
        val walk = CameoOffer.confirmPayout(
            CameoOffer.Confirm.WalkAway,
            health = 0,
            standard = false,
            breathing = false,
            mirror = false,
            quick = false,
            hasFriend = true,
            reward = 1500,
            alreadyPaid = false,
        )
        assertEquals(0, open)
        assertEquals(1500, walk)
    }

    @Test
    fun friendCameoSpeaksWithTheFirstNameAndTheRealGap() {
        assertEquals(6, CameoOffer.motivationCount())
        val spoken = CameoOffer.friendLine("Sam", friendXp = 900, yourXp = 0, seed = 0)
        assertEquals("Sam", spoken.speaker)
        assertEquals("Come on, put it down. I'm 900 XP ahead of you today.", spoken.sentence)
        assertEquals("Sara", CameoOffer.firstName("Sara J."))
        val again = CameoOffer.friendLine("Sam", 900, 0, 0)
        assertEquals(spoken.sentence, again.sentence)
        val lines = (0 until 6).map { seed ->
            CameoOffer.friendLine("Sam Jones", friendXp = 1200, yourXp = 300, seed = seed)
        }
        assertEquals(6, lines.map { it.sentence }.distinct().size)
        lines.forEach { line ->
            assertEquals("Sam", line.speaker)
            assertTrue(line.sentence.contains("I'm 900 XP ahead of you today"))
            assertFalse(line.sentence.contains("workout"))
            assertFalse(line.sentence.contains("shame"))
        }
        assertEquals("We're level on XP today", CameoOffer.gapPhrase(40, 40))
        assertEquals("You're 20 XP ahead of me today", CameoOffer.gapPhrase(10, 30))
        assertEquals(CameoOffer.confirmQuestion, "You're at 0 XP. Do you want to continue and open the app?")
    }

    @Test
    fun noBailoutAboveZeroHealthOrWithoutAFriend() {
        assertEquals(0, payout(health = 1, hasFriend = true, standard = false))
        assertEquals(0, payout(health = 12, hasFriend = true, standard = false))
        assertEquals(0, payout(health = null, hasFriend = true, standard = false))
        assertEquals(0, payout(health = 0, hasFriend = false, standard = false))
        assertEquals(0, payout(health = 0, hasFriend = true, standard = true))
        assertEquals(0, payout(health = 0, hasFriend = true, standard = false, alreadyPaid = true))
    }

    private fun payout(
        health: Int?,
        hasFriend: Boolean,
        standard: Boolean,
        alreadyPaid: Boolean = false,
        continued: Boolean = false,
        choice: CameoOffer.Confirm = CameoOffer.Confirm.WalkAway,
    ): Int = CameoOffer.confirmPayout(
        choice = choice,
        health = health,
        standard = standard,
        breathing = false,
        mirror = false,
        quick = false,
        hasFriend = hasFriend,
        reward = 1500,
        alreadyPaid = alreadyPaid,
        continuedFromStandard = continued,
    )
}
