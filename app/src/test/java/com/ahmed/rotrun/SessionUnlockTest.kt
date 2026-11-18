package com.ahmed.rotrun

import com.ahmed.rotrun.data.ProfilePhoto
import com.ahmed.rotrun.data.SessionUnlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

class SessionUnlockTest {
    @Test
    fun keystoreTagFailureCanBeReset() {
        val wrapped = RuntimeException(AEADBadTagException("tag mismatch"))
        assertTrue(SessionUnlock.recoverable(wrapped))
        assertTrue(SessionUnlock.recoverable(GeneralSecurityException("mac")))
        assertTrue(SessionUnlock.recoverable(IOException("prefs")))
        assertFalse(SessionUnlock.recoverable(IllegalStateException("other")))
    }

    @Test
    fun photoShrinksUntilItFits() {
        assertEquals(512 to 70, ProfilePhoto.nextAttempt(512, 82))
        assertEquals(256 to 82, ProfilePhoto.nextAttempt(512, 35))
        assertNull(ProfilePhoto.nextAttempt(128, 35))
    }
}
