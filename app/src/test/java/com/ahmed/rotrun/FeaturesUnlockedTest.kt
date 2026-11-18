package com.ahmed.rotrun

import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.logic.PaywallGate
import com.ahmed.rotrun.logic.Products
import com.ahmed.rotrun.watch.UpgradeReminders
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesUnlockedTest {
    private val buyer = "11111111-2222-3333-4444-555555555555"
    private val other = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    @After
    fun resetGate() {
        PaywallGate.showsForTest = null
    }

    @Test
    fun gateOffAllowsVoiceSetsPremiumAndHidesPlanRows() {
        PaywallGate.showsForTest = false
        val tester = SavedState()
        assertFalse(tester.proFor("tester"))
        assertTrue(tester.featuresUnlocked("tester"))
        assertTrue(tester.featuresUnlocked(null))
        assertFalse(PaywallGate.premiumPlanRowsShown())
        assertFalse(UpgradeReminders.scheduled(tester.copy(upgradeReminders = true), "tester"))
    }

    @Test
    fun gateOnKeepsTheExistingProChecks() {
        PaywallGate.showsForTest = true
        val free = SavedState()
        val pro = SavedState(
            premium = true,
            productId = Products.MONTHLY,
            grantUserId = buyer,
        )
        assertFalse(free.featuresUnlocked("tester"))
        assertEquals(free.proFor("tester"), free.featuresUnlocked("tester"))
        assertEquals(free.proFor(null), free.featuresUnlocked(null))
        assertTrue(pro.proFor(buyer))
        assertTrue(pro.featuresUnlocked(buyer))
        assertFalse(pro.proFor(other))
        assertFalse(pro.featuresUnlocked(other))
        assertFalse(pro.featuresUnlocked(null))
        assertTrue(PaywallGate.premiumPlanRowsShown())
        assertTrue(UpgradeReminders.scheduled(free.copy(upgradeReminders = true), "tester"))
        assertFalse(UpgradeReminders.scheduled(pro.copy(upgradeReminders = true), buyer))
        assertFalse(UpgradeReminders.scheduled(free, "tester"))
    }
}
