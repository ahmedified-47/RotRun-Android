package com.ahmed.rotrun

import com.ahmed.rotrun.logic.VoiceSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionTest {
    @Test
    fun savedAgentIsOnTheRealtimeUrl() {
        assertEquals(
            "wss://api.x.ai/v1/realtime?model=grok-voice-latest",
            VoiceSession.realtimeUrl("grok-voice-latest", ""),
        )
        assertEquals(
            "wss://api.x.ai/v1/realtime?model=grok-voice-latest&agent_id=agent-1",
            VoiceSession.realtimeUrl(" grok-voice-latest ", "agent-1"),
        )
    }

    @Test
    fun personalityIsSkippedWhenTheAgentIdIsSet() {
        val live = "screen_time=1h"
        assertEquals("Standing\nlive", VoiceSession.instructions("", "Standing", "live"))
        assertEquals(live, VoiceSession.instructions("agent-1", "Standing", live))
    }

    @Test
    fun serverErrorIsShownAsReturned() {
        assertEquals("Voice is disabled", VoiceSession.serverMessage("Voice is disabled", null, "fallback"))
        assertEquals("fallback", VoiceSession.serverMessage("  ", "", "fallback"))
        assertFalse(VoiceSession.liveReading(false, "", "", "").contains("0m"))
        assertTrue(VoiceSession.liveReading(false, "", "", "").contains("not in yet"))
    }

    @Test
    fun paywallRequiredIsNotADailyLimit() {
        assertTrue(VoiceSession.isPaywallRequired("paywall_required"))
        assertFalse(VoiceSession.isVoiceLimit(429, "paywall_required"))
        assertTrue(VoiceSession.isVoiceLimit(429, "voice_daily_limit"))
        assertTrue(VoiceSession.isVoiceLimit(429, null))
        assertFalse(VoiceSession.isVoiceLimit(500, null))
    }

    @Test
    fun premiumWithoutARowRetriesThenExplains() {
        assertEquals(VoiceSession.PaywallAction.ShowPaywall, VoiceSession.paywallAction(locallyPremium = false, alreadyRetried = false))
        assertEquals(VoiceSession.PaywallAction.RetryPurchaseSync, VoiceSession.paywallAction(locallyPremium = true, alreadyRetried = false))
        assertEquals(VoiceSession.PaywallAction.ShowVerificationFailure, VoiceSession.paywallAction(locallyPremium = true, alreadyRetried = true))
        assertEquals("Voice is part of RotRun Premium.", VoiceSession.spokenFailure("Voice is part of RotRun Premium.", null, "fallback"))
        assertEquals("Voice couldn't start. no row", VoiceSession.spokenFailure("no row", null, "fallback"))
        assertTrue(VoiceSession.verificationFailure(null).startsWith("Voice couldn't start."))
        assertFalse(VoiceSession.noticeCanRetry("Voice is part of RotRun Premium."))
        assertFalse(VoiceSession.noticeCanRetry("Voice permissions required"))
        assertTrue(VoiceSession.noticeCanRetry("Voice couldn't start. The socket closed."))
    }
}
