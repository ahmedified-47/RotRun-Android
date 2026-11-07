package com.ahmed.rotrun

import com.ahmed.rotrun.logic.BrainCallPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainCallPolicyTest {
    @Test
    fun faceTimeDeclinesBackToTheBlockAndDoesNotStartVoice() {
        assertTrue(BrainCallPolicy.isCall("mirror"))
        assertFalse(BrainCallPolicy.isCall("standard"))
        assertEquals("shield", BrainCallPolicy.afterDecline())
        assertFalse(BrainCallPolicy.answerStartsVoice())
    }

    @Test
    fun fullScreenIntentIsPostedOnceAndOnlyWhenAllowed() {
        assertTrue(BrainCallPolicy.postFullScreenIntent(api = 33, allowed = false, alreadyPosted = false))
        assertFalse(BrainCallPolicy.postFullScreenIntent(api = 34, allowed = false, alreadyPosted = false))
        assertTrue(BrainCallPolicy.postFullScreenIntent(api = 34, allowed = true, alreadyPosted = false))
        assertFalse(BrainCallPolicy.postFullScreenIntent(api = 34, allowed = true, alreadyPosted = true))
    }
}
