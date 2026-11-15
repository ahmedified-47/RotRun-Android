package com.ahmed.rotrun

import com.ahmed.rotrun.logic.CameoOffer
import com.ahmed.rotrun.logic.DeviceOnline
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
        assertEquals(3, CameoOffer.motivationCount())
        val ahead = CameoOffer.friendLine("Sam", friendXp = 900, yourXp = 0)
        assertEquals("Sam", ahead.speaker)
        assertEquals("It's Sam. I'm 900 XP ahead of you today — close this and catch me.", ahead.sentence)
        val tied = CameoOffer.friendLine("Sara J.", friendXp = 40, yourXp = 40)
        assertEquals("Sara", tied.speaker)
        assertEquals("We're level at 40 XP. Don't let this app be the reason I pass you.", tied.sentence)
        val behind = CameoOffer.friendLine("Sam Jones", friendXp = 10, yourXp = 30)
        assertEquals("Sam", behind.speaker)
        assertEquals("You're 20 XP ahead of me. Don't throw it away on this app.", behind.sentence)
        listOf(ahead, tied, behind).forEach { line ->
            assertFalse(line.sentence.contains("Not now"))
            assertFalse(line.sentence.contains("workout"))
        }
        assertEquals(CameoOffer.confirmQuestion, "You're at 0 XP. Do you want to continue and open the app?")
    }

    @Test
    fun randomPoolIncludesCameoAboveZeroOnlyWithAFriendAndOnline() {
        assertTrue(DeviceOnline.connected(hasNetwork = true, internet = true, validated = true))
        assertFalse(DeviceOnline.connected(hasNetwork = true, internet = true, validated = false))
        assertFalse(DeviceOnline.connected(hasNetwork = false, internet = true, validated = true))
        assertTrue(CameoOffer.cameoInRandomPool(hasFriend = true, online = true))
        assertFalse(CameoOffer.cameoInRandomPool(hasFriend = false, online = true))
        assertFalse(CameoOffer.cameoInRandomPool(hasFriend = true, online = false))
        val withCameo = CameoOffer.pool(true, true, true, true, cameo = true)
        assertTrue(withCameo.contains("cameo"))
        assertEquals(listOf("standard", "breathing", "mirror", "quick", "cameo"), withCameo)
        assertFalse(CameoOffer.lastResort(40, true, true, true, true, hasFriend = true))
        assertEquals("cameo", CameoOffer.mode(40, false, false, false, false, hasFriend = true, online = true))
        assertEquals("standard", CameoOffer.mode(40, true, false, false, false, hasFriend = false, online = true))
        assertFalse(CameoOffer.pool(true, true, true, true, cameo = CameoOffer.cameoInRandomPool(true, false)).contains("cameo"))
        assertEquals("standard", CameoOffer.mode(80, true, false, false, false, hasFriend = true, online = false))
        repeat(24) {
            val picked = CameoOffer.mode(40, true, true, true, true, hasFriend = true, online = true)
            assertTrue(picked in setOf("standard", "breathing", "mirror", "quick", "cameo"))
        }
    }

    @Test
    fun randomCameoDeclinePaysNothingAndStillGoesHome() {
        val random = CameoOffer.walkAway(lastResortConfirm = false, reward = 1500, alreadyPaid = false)
        assertEquals(0, random.payout)
        assertTrue(random.goHome)
        val first = CameoOffer.walkAway(lastResortConfirm = true, reward = 1500, alreadyPaid = false)
        assertEquals(1500, first.payout)
        assertTrue(first.goHome)
        val again = CameoOffer.walkAway(lastResortConfirm = true, reward = 1500, alreadyPaid = true)
        assertEquals(0, again.payout)
        assertTrue(again.goHome)
        assertEquals(0, payout(health = 40, hasFriend = true, standard = true))
        assertEquals(1500, payout(health = 0, hasFriend = true, standard = false))
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
