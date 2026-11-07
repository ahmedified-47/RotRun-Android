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
}
