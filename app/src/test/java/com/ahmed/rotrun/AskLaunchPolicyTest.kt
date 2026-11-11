package com.ahmed.rotrun

import android.content.Intent
import com.ahmed.rotrun.data.AskIntervention
import com.ahmed.rotrun.data.AskInterventionPlan
import com.ahmed.rotrun.data.GoogleNonce
import com.ahmed.rotrun.data.GoogleSignInPolicy
import com.ahmed.rotrun.logic.BrainCallPolicy
import com.ahmed.rotrun.watch.AskLaunchPolicy
import com.ahmed.rotrun.watch.ForegroundGate
import com.ahmed.rotrun.watch.ShieldSession
import com.ahmed.rotrun.watch.ShieldStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AskLaunchPolicyTest {
    @Test
    fun failedStartDismissesTheShieldAndDoesNotRedrawIt() {
        val failed = AskLaunchPolicy.shieldAfterAttempt(startFailed = true)
        assertTrue(failed.dismiss)
        assertFalse(failed.redraw)
        assertFalse(failed.releaseHold)
        assertFalse(AskLaunchPolicy.releaseHold(startFailed = true))
        assertFalse(AskLaunchPolicy.showShield(chatting = true))
        assertTrue(AskLaunchPolicy.showShield(chatting = false))
        assertTrue(AskLaunchPolicy.leaveOverlayUp(launching = true))
        assertFalse(AskLaunchPolicy.leaveOverlayUp(launching = false))
        val flags = AskLaunchPolicy.BRING_FORWARD_FLAGS
        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }

    @Test
    fun chatHoldSurvivesReleaseUntilTalkCloses() {
        ShieldSession.endChat()
        ShieldSession.holdForChat("com.instagram.android")
        ShieldSession.release()
        assertEquals("com.instagram.android", ShieldSession.chatPackage())
        assertTrue(ShieldSession.blocks("com.instagram.android"))
        assertFalse(AskLaunchPolicy.showShield(ShieldSession.chatPackage() != null))
        ShieldSession.endChat()
        assertNull(ShieldSession.chatPackage())
        assertFalse(ShieldSession.blocks("com.instagram.android"))
        assertTrue(AskLaunchPolicy.showShield(chatting = false))
    }

    @Test
    fun askBrainKeepsOneInterventionUntilTheUserLeaves() {
        assertEquals("standard", AskInterventionPlan.mode(null))
        assertEquals("standard", AskInterventionPlan.mode(" "))
        assertEquals("quick", AskInterventionPlan.mode("quick"))
        val showing = AskIntervention("com.instagram.android", "Instagram", "standard")
        assertTrue(AskInterventionPlan.alreadyShowing(showing))
        assertFalse(AskInterventionPlan.alreadyShowing(null))
        ShieldSession.endChat()
        ShieldSession.holdForChat("com.instagram.android", "Instagram")
        assertEquals("Instagram", ShieldSession.chatLabel())
        assertFalse(AskLaunchPolicy.showShield(ShieldSession.chatPackage() != null))
        ShieldSession.endChat()
        assertEquals("", ShieldSession.chatLabel())
    }

    @Test
    fun shieldStaysUpWhenColorOsSendsItsOwnWindowEvents() {
        assertTrue(AskLaunchPolicy.keepShownShield("com.instagram.android", "com.instagram.android", "com.ahmed.rotrun"))
        assertTrue(AskLaunchPolicy.keepShownShield("com.instagram.android", "com.ahmed.rotrun", "com.ahmed.rotrun"))
        assertTrue(AskLaunchPolicy.keepShownShield("com.instagram.android", "com.android.systemui", "com.ahmed.rotrun"))
        assertTrue(AskLaunchPolicy.keepShownShield("com.instagram.android", "com.coloros.oppoguardelf", "com.ahmed.rotrun"))
        assertTrue(AskLaunchPolicy.keepShownShield("com.instagram.android", "com.oplus.systemui", "com.ahmed.rotrun"))
        assertFalse(AskLaunchPolicy.keepShownShield("com.instagram.android", "com.chrome", "com.ahmed.rotrun"))
        assertFalse(AskLaunchPolicy.keepShownShield(null, "com.android.systemui", "com.ahmed.rotrun"))
        assertFalse(AskLaunchPolicy.acceptTap(inFlight = true))
        assertTrue(AskLaunchPolicy.acceptTap(inFlight = false))
        assertFalse(AskLaunchPolicy.raiseShieldActivity(alreadyShowing = true, elapsedMs = 20_000))
        assertFalse(AskLaunchPolicy.raiseShieldActivity(alreadyShowing = false, elapsedMs = 1_000))
        assertFalse(AskLaunchPolicy.raiseShieldActivity(alreadyShowing = false, elapsedMs = 8_000))
        assertFalse(AskLaunchPolicy.raiseShieldActivity(alreadyShowing = false, elapsedMs = 60_000))
        assertEquals("overlay", AskLaunchPolicy.shieldCover(accessibilityConnected = true, canDrawOverlays = false))
        assertEquals("overlay", AskLaunchPolicy.shieldCover(accessibilityConnected = false, canDrawOverlays = true))
        assertEquals("none", AskLaunchPolicy.shieldCover(accessibilityConnected = false, canDrawOverlays = false))
    }

    @Test
    fun openingRotRunDoesNotCountAsTheBlockedApp() {
        val own = "com.ahmed.rotrun"
        val safety = setOf(own, "com.android.systemui", "com.android.launcher3")
        assertEquals("com.ahmed.rotrun", ForegroundGate.latestResume(listOf("com.instagram.android", own)))
        assertTrue(ForegroundGate.ignore(own, own))
        assertTrue(ForegroundGate.ignore("com.android.systemui", own))
        assertTrue(ForegroundGate.ignore("com.coloros.oppoguardelf", own))
        assertTrue(ForegroundGate.ignore("com.oplus.systemui", own))
        assertTrue(ForegroundGate.ignore("com.oppo.launcher", own))
        assertFalse(ForegroundGate.ignore("com.instagram.android", own))
        assertFalse(ForegroundGate.shouldShowShield(own, own, safety, blocked = true))
        assertFalse(ForegroundGate.shouldShowShield("com.android.systemui", own, safety, blocked = true))
        assertFalse(ForegroundGate.shouldShowShield("com.android.launcher3", own, safety, blocked = true))
        assertFalse(ForegroundGate.shouldShowShield("com.instagram.android", own, safety, blocked = false))
        assertTrue(ForegroundGate.shouldShowShield("com.instagram.android", own, safety, blocked = true))
        assertFalse(ForegroundGate.shouldShowShield(null, own, safety, blocked = true))
    }

    @Test
    fun blockedAppStillGetsAShieldWhenRotRunAndSystemUiAreNoise() {
        val own = "com.ahmed.rotrun"
        val safety = setOf(own, "android", "com.android.systemui", "com.android.launcher3", "com.oppo.launcher")
        val instagram = "com.instagram.android"
        val shownAt = 5_000L
        assertEquals(
            ShieldStep.Show,
            ForegroundGate.onUsage(instagram, 4_000L, null, 0L, own, safety, blocked = true),
        )
        assertEquals(
            ShieldStep.Show,
            ForegroundGate.onWindow(instagram, windowStateChanged = true, shownFor = null, own, safety, eventBlocked = true),
        )
        assertEquals(
            ShieldStep.Keep,
            ForegroundGate.onUsage(null, 0L, instagram, shownAt, own, safety, blocked = false),
        )
        assertEquals(
            ShieldStep.Leave,
            ForegroundGate.onUsage(null, 0L, null, 0L, own, safety, blocked = true),
        )
        val calendar = "com.google.android.calendar"
        assertEquals(calendar, ForegroundGate.packageToCover("com.android.systemui", calendar, own, safety))
        assertEquals(calendar, ForegroundGate.packageToCover(null, calendar, own, safety))
        assertEquals(calendar, ForegroundGate.packageToCover("com.coloros.oppoguardelf", calendar, own, safety))
        assertEquals(
            ShieldStep.Show,
            ForegroundGate.onWindow(
                ForegroundGate.packageToCover("com.android.systemui", calendar, own, safety).orEmpty(),
                windowStateChanged = false,
                shownFor = null,
                own,
                safety,
                eventBlocked = true,
            ),
        )
        assertEquals(
            ShieldStep.Show,
            ForegroundGate.onUsage(
                ForegroundGate.packageToCover(null, calendar, own, safety),
                0L,
                null,
                0L,
                own,
                safety,
                blocked = true,
            ),
        )
        assertEquals(own, ForegroundGate.packageToCover(own, calendar, own, safety))
        assertEquals("com.android.launcher3", ForegroundGate.packageToCover("com.android.launcher3", calendar, own, safety))
        assertEquals("com.android.systemui", ForegroundGate.packageToCover("com.android.systemui", null, own, safety))
        assertEquals(
            ShieldStep.Leave,
            ForegroundGate.onWindow(own, windowStateChanged = true, shownFor = null, own, safety, eventBlocked = true),
        )
        assertEquals(
            ShieldStep.Leave,
            ForegroundGate.onWindow("com.android.systemui", windowStateChanged = true, shownFor = null, own, safety, eventBlocked = true),
        )
        assertEquals(
            ShieldStep.Keep,
            ForegroundGate.onWindow(own, windowStateChanged = false, shownFor = instagram, own, safety, eventBlocked = false),
        )
        assertEquals(
            ShieldStep.Keep,
            ForegroundGate.onWindow("com.android.systemui", windowStateChanged = true, shownFor = instagram, own, safety, eventBlocked = false),
        )
        assertEquals(
            ShieldStep.Keep,
            ForegroundGate.onWindow("com.android.systemui", windowStateChanged = false, shownFor = instagram, own, safety, eventBlocked = false),
        )
        assertEquals(
            ShieldStep.Hide,
            ForegroundGate.onUsage("com.android.systemui", 6_000L, instagram, shownAt, own, safety, blocked = false),
        )
        assertEquals(
            ShieldStep.Keep,
            ForegroundGate.onUsage("com.android.systemui", 1_000L, instagram, shownAt, own, safety, blocked = false),
        )
        assertEquals(
            ShieldStep.Show,
            ForegroundGate.onUsage("com.google.android.calendar", 4_000L, null, 0L, own, safety, blocked = true),
        )
        assertEquals(
            ShieldStep.Show,
            ForegroundGate.onWindow("com.google.android.calendar", windowStateChanged = true, shownFor = null, own, safety, eventBlocked = true),
        )
        assertEquals(
            ShieldStep.Hide,
            ForegroundGate.onUsage(own, 6_000L, "com.google.android.calendar", shownAt, own, safety, blocked = true),
        )
        assertEquals(
            ShieldStep.Keep,
            ForegroundGate.onWindow("com.coloros.oppoguardelf", windowStateChanged = false, shownFor = instagram, own, safety, eventBlocked = false),
        )
        assertEquals(
            ShieldStep.Leave,
            ForegroundGate.onUsage(own, 1_000L, instagram, shownAt, own, safety, blocked = false),
        )
        assertEquals(
            ShieldStep.Hide,
            ForegroundGate.onUsage(own, 6_000L, instagram, shownAt, own, safety, blocked = false),
        )
        assertEquals(
            ShieldStep.Keep,
            ForegroundGate.onWindow(own, windowStateChanged = true, shownFor = instagram, own, safety, eventBlocked = false),
        )
        assertEquals(
            ShieldStep.Keep,
            ForegroundGate.onWindow("com.google.android.calendar", windowStateChanged = true, shownFor = "com.google.android.calendar", own, safety, eventBlocked = true),
        )
        assertFalse(AskLaunchPolicy.fullScreenAfterStart(startSucceeded = true))
        assertTrue(AskLaunchPolicy.fullScreenAfterStart(startSucceeded = false))
        assertEquals(
            ShieldStep.Hide,
            ForegroundGate.onUsage("com.oppo.launcher", 6_000L, instagram, shownAt, own, safety, blocked = true),
        )
        assertEquals(
            ShieldStep.Hide,
            ForegroundGate.onWindow("com.android.launcher3", windowStateChanged = true, shownFor = instagram, own, safety, eventBlocked = false),
        )
        assertEquals(
            ShieldStep.Hide,
            ForegroundGate.onUsage(own, 6_000L, null, 0L, own, safety, blocked = true),
        )
        assertFalse(ForegroundGate.shouldShowShield(own, own, safety, blocked = true))
        assertFalse(ForegroundGate.shouldShowShield("com.android.systemui", own, safety, blocked = true))
        assertFalse(ForegroundGate.shouldShowShield("com.oppo.launcher", own, safety, blocked = true))
    }

    @Test
    fun blockedPackageInFrontIsShownEvenWhenTheEventIsTheLauncher() {
        val own = "com.ahmed.rotrun"
        val safety = setOf(own, "android", "com.android.systemui", "com.android.launcher3", "com.oppo.launcher")
        val calendar = "com.google.android.calendar"
        val launcher = "com.android.launcher3"
        val oppo = "com.oppo.launcher"
        val shownAt = 5_000L
        val blocked = { pkg: String -> pkg == calendar }
        val noFocus = ForegroundGate.orderedPackages(
            listOf(oppo to false, "com.android.systemui" to false, calendar to false),
            own,
            safety,
        )
        assertEquals(calendar, noFocus.first())
        val fromLauncherEvent = ForegroundGate.coverChoice(
            reported = oppo,
            windowsFrontToBack = noFocus,
            usagePackage = null,
            usageResumedAt = 0L,
            shownFor = null,
            shownAt = 0L,
            ownPackage = own,
            safety = safety,
            blocked = blocked,
            windowStateChanged = true,
        )
        assertEquals(ShieldStep.Show, fromLauncherEvent.step)
        assertEquals(calendar, fromLauncherEvent.packageName)
        val focused = ForegroundGate.orderedPackages(listOf(launcher to false, calendar to true), own, safety)
        val focusedChoice = ForegroundGate.coverChoice(
            reported = launcher,
            windowsFrontToBack = focused,
            usagePackage = launcher,
            usageResumedAt = 1_000L,
            shownFor = null,
            shownAt = 0L,
            ownPackage = own,
            safety = safety,
            blocked = blocked,
            windowStateChanged = true,
        )
        assertEquals(ShieldStep.Show, focusedChoice.step)
        assertEquals(calendar, focusedChoice.packageName)
        val home = ForegroundGate.orderedPackages(listOf(launcher to true, calendar to false), own, safety)
        val homeChoice = ForegroundGate.coverChoice(
            reported = launcher,
            windowsFrontToBack = home,
            usagePackage = null,
            usageResumedAt = 0L,
            shownFor = calendar,
            shownAt = shownAt,
            ownPackage = own,
            safety = safety,
            blocked = blocked,
            windowStateChanged = true,
        )
        assertEquals(ShieldStep.Hide, homeChoice.step)
        assertFalse(homeChoice.packageName == launcher || homeChoice.packageName == own)
        val again = ForegroundGate.coverChoice(
            reported = "com.coloros.oppoguardelf",
            windowsFrontToBack = listOf(calendar),
            usagePackage = null,
            usageResumedAt = 0L,
            shownFor = calendar,
            shownAt = shownAt,
            ownPackage = own,
            safety = safety,
            blocked = blocked,
            windowStateChanged = true,
        )
        assertEquals(ShieldStep.Keep, again.step)
        assertEquals(calendar, again.packageName)
        assertEquals(
            ShieldStep.Leave,
            ForegroundGate.coverChoice(own, listOf(own), null, 0L, null, 0L, own, safety, blocked, true).step,
        )
        assertEquals(
            ShieldStep.Leave,
            ForegroundGate.coverChoice("com.android.systemui", listOf("com.android.systemui"), null, 0L, null, 0L, own, safety, blocked, true).step,
        )
        assertEquals(
            ShieldStep.Hide,
            ForegroundGate.coverChoice(launcher, listOf(launcher), null, 0L, calendar, shownAt, own, safety, blocked, true).step,
        )
        assertEquals(
            ShieldStep.Keep,
            ForegroundGate.coverChoice(own, listOf(own), own, 1_000L, calendar, shownAt, own, safety, blocked, true).step,
        )
        assertEquals(
            ShieldStep.Hide,
            ForegroundGate.coverChoice(own, listOf(own), own, 6_000L, calendar, shownAt, own, safety, blocked, true).step,
        )
        assertEquals(
            ShieldStep.Show,
            ForegroundGate.coverChoice(null, emptyList(), calendar, 0L, null, 0L, own, safety, blocked, false).step,
        )
        assertEquals(
            ShieldStep.Keep,
            ForegroundGate.coverChoice(null, emptyList(), null, 0L, calendar, shownAt, own, safety, blocked, false).step,
        )
        assertEquals(
            ShieldStep.Leave,
            ForegroundGate.coverChoice(null, emptyList(), null, 0L, null, 0L, own, safety, blocked, false).step,
        )
        assertEquals("none", AskLaunchPolicy.shieldCover(accessibilityConnected = false, canDrawOverlays = false))
        assertEquals("overlay", AskLaunchPolicy.shieldCover(accessibilityConnected = true, canDrawOverlays = false))
        assertEquals("overlay", AskLaunchPolicy.shieldCover(accessibilityConnected = false, canDrawOverlays = true))
        assertFalse(AskLaunchPolicy.raiseShieldActivity(alreadyShowing = false, elapsedMs = 0))
        assertEquals("dismiss", BrainCallPolicy.afterDecline())
    }

    @Test
    fun nahEndsTheChatWithoutOpeningTheBlockedApp() {
        ShieldSession.endChat()
        ShieldSession.holdForChat("com.instagram.android", "Instagram")
        ShieldSession.endChat()
        assertFalse(ShieldSession.openedInto("com.instagram.android"))
        assertFalse(ShieldSession.blocks("com.instagram.android"))
        ShieldSession.hold("com.instagram.android")
        assertFalse(ShieldSession.openedInto("com.instagram.android"))
        assertTrue(ShieldSession.blocks("com.instagram.android"))
        ShieldSession.release()
    }

    @Test
    fun googleNonceIsLowerHexAndAMissingGrantRetriesTheAccountList() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            GoogleNonce.hash("hello"),
        )
        assertEquals(64, GoogleNonce.hash("hello").length)
        assertTrue(GoogleSignInPolicy.retryWithAccountList("NoCredentialException", ""))
        assertTrue(GoogleSignInPolicy.retryWithAccountList("GetCredentialProviderConfigurationException", ""))
        assertFalse(GoogleSignInPolicy.retryWithAccountList("GetCredentialCancellationException", "activity is cancelled by the user"))
        assertTrue(GoogleSignInPolicy.message("NoCredentialException", "").contains("Google account"))
        assertTrue(GoogleSignInPolicy.message("GetCredentialException", "Developer console is not set up correctly. 10:").contains("SHA-1"))
    }
}
