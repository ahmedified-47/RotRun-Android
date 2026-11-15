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
    fun zeroHealthWithAStandardStyleDoesNotOfferCameo() {
        assertFalse(CameoOffer.standardStylesUnusable(standard = true, breathing = false, mirror = false, quick = false))
        assertEquals("standard", CameoOffer.mode(0, true, false, false, false, hasFriend = true))
        assertEquals("breathing", CameoOffer.mode(0, false, true, false, false, hasFriend = true))
        assertEquals("mirror", CameoOffer.mode(0, false, false, true, false, hasFriend = true))
        assertEquals("quick", CameoOffer.mode(0, false, false, false, true, hasFriend = true))
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
    fun noBailoutAboveZeroHealthOrWithoutAFriend() {
        assertEquals(0, payout(health = 1, hasFriend = true, standard = false))
        assertEquals(0, payout(health = 12, hasFriend = true, standard = false))
        assertEquals(0, payout(health = null, hasFriend = true, standard = false))
        assertEquals(0, payout(health = 0, hasFriend = false, standard = false))
        assertEquals(0, payout(health = 0, hasFriend = true, standard = true))
        assertEquals(0, payout(health = 0, hasFriend = true, standard = false, alreadyPaid = true))
    }

    private fun payout(health: Int?, hasFriend: Boolean, standard: Boolean, alreadyPaid: Boolean = false): Int =
        CameoOffer.confirmPayout(
            choice = CameoOffer.Confirm.WalkAway,
            health = health,
            standard = standard,
            breathing = false,
            mirror = false,
            quick = false,
            hasFriend = hasFriend,
            reward = 1500,
            alreadyPaid = alreadyPaid,
        )
}
