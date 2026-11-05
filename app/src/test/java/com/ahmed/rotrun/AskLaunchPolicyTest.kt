package com.ahmed.rotrun

import android.content.Intent
import com.ahmed.rotrun.data.AskIntervention
import com.ahmed.rotrun.data.AskInterventionPlan
import com.ahmed.rotrun.data.GoogleNonce
import com.ahmed.rotrun.data.GoogleSignInPolicy
import com.ahmed.rotrun.watch.AskLaunchPolicy
import com.ahmed.rotrun.watch.ShieldSession
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
