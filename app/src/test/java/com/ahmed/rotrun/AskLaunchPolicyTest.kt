package com.ahmed.rotrun

import android.content.Intent
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
}
