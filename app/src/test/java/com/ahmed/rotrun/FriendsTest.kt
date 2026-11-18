package com.ahmed.rotrun

import com.ahmed.rotrun.logic.FriendCameoCache
import com.ahmed.rotrun.logic.FriendCameoPayload
import com.ahmed.rotrun.logic.FriendCameoPick
import com.ahmed.rotrun.logic.FriendCameoSurface
import com.ahmed.rotrun.logic.FriendCode
import com.ahmed.rotrun.logic.FriendshipPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsTest {
    private val you = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    private val sara = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    private val mia = "cccccccc-cccc-cccc-cccc-cccccccccccc"
    private val ben = "dddddddd-dddd-dddd-dddd-dddddddddddd"

    @Test
    fun pairAndCodeMatchIos() {
        assertEquals(FriendshipPair.of(you, sara), FriendshipPair.of(sara, you))
        assertNull(FriendshipPair.of(you, you))
        assertNull(FriendshipPair.of("not-a-uuid", sara))
        assertEquals("H3KM9P", FriendCode.normalize(" h3km-9p "))
        assertNull(FriendCode.normalize("h3km9"))
        assertNull(FriendCode.normalize("H3KM9P1"))
        assertNull(FriendCode.normalize("H3KM0P"))
    }

    @Test
    fun cameoPicksTheFriendAheadAndFallsBack() {
        val saraRow = FriendCameoPick.Candidate(sara, "Sara Jones", 900, "https://example.com/sara.jpg")
        val miaRow = FriendCameoPick.Candidate(mia, "Mia Chen", 400, null)
        val benRow = FriendCameoPick.Candidate(ben, "Ben Lee", 100, "http://example.com/ben.jpg")
        val ahead = FriendCameoPick.select(listOf(benRow, miaRow, saraRow), you, 300)
        assertEquals(sara, ahead?.userId)
        assertEquals("Sara J.", ahead?.name)
        assertEquals("Sara J. is at 900 XP, you're at 300", ahead?.line)
        assertEquals("https://example.com/sara.jpg", ahead?.avatarUrl)

        val justAhead = FriendCameoPick.select(listOf(benRow, miaRow), you, 300)
        assertEquals(mia, justAhead?.userId)
        assertEquals("Mia C.", justAhead?.name)

        val behind = FriendCameoPick.select(listOf(benRow), you, 300)
        assertEquals(ben, behind?.userId)
        assertNull(behind?.avatarUrl)

        assertNull(FriendCameoPick.select(listOf(FriendCameoPick.Candidate(you, "You", 900, null)), you, 300))
        assertNull(FriendCameoPick.select(emptyList(), you, 300))
        assertEquals("Sara J.", FriendCameoPick.displayName("Sara J."))

        val now = 1_700_000_000_000L
        val fresh = FriendCameoPayload(now, you, 300, listOf(saraRow, miaRow))
        val shown = FriendCameoCache.resolve(fresh, enabled = true, nowMillis = now)
        assertTrue(shown is FriendCameoSurface.Friend)
        assertEquals(ahead, (shown as FriendCameoSurface.Friend).choice)

        val stale = fresh.copy(writtenAtMillis = now - (FriendCameoCache.ttlMillis + 1))
        assertEquals(
            FriendCameoSurface.Fallback(FriendCameoPick.fallback),
            FriendCameoCache.resolve(stale, enabled = true, nowMillis = now),
        )
        assertEquals(
            FriendCameoSurface.Fallback(FriendCameoPick.fallback),
            FriendCameoCache.resolve(null, enabled = true, nowMillis = now),
        )
        assertEquals(
            FriendCameoSurface.Fallback(FriendCameoPick.fallback),
            FriendCameoCache.resolve(fresh.copy(friends = emptyList()), enabled = true, nowMillis = now),
        )
        assertEquals(FriendCameoSurface.Hidden, FriendCameoCache.resolve(fresh, enabled = false, nowMillis = now))
        assertEquals("No friends on the board yet. Your Daily XP still counts.", FriendCameoPick.fallback)
    }
}
