package com.ahmed.rotrun

import com.ahmed.rotrun.logic.DailyBoard
import com.ahmed.rotrun.logic.XpRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyBoardTest {
    @Test
    fun dedupesByUserIdAndKeepsTheFirstRow() {
        val rows = DailyBoard.dedupe(
            listOf(
                XpRow("a", "Ahmed J.", 40),
                XpRow("a", "You", 10),
                XpRow("b", "Sam K.", 40),
                XpRow(" ", "Nobody", 5),
            ),
        )
        assertEquals(listOf("a", "b"), rows.map { it.userId })
        assertEquals("Ahmed J.", rows.first().name)
    }

    @Test
    fun placesAreTheFetchIndexPlusOne() {
        val ranked = DailyBoard.withRanks(
            listOf(
                XpRow("a", "Ada L.", 50),
                XpRow("b", "Sam K.", 50),
                XpRow("me", "Ahmed J.", 20),
            ),
            youId = "me",
        )
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
        assertEquals(listOf(50, 50, 20), ranked.map { it.xp })
        assertTrue(ranked.last().you)
        assertFalse(ranked.first().you)
        assertEquals(listOf(2, 1, 3), DailyBoard.topRow(ranked).map { it.rank })
        assertEquals(listOf(2, 1), DailyBoard.topRow(ranked.take(2)).map { it.rank })
        assertEquals(listOf(1), DailyBoard.topRow(ranked.take(1)).map { it.rank })
        assertTrue(DailyBoard.topRow(emptyList()).isEmpty())
    }

    @Test
    fun namesAreFirstNameAndLastInitial() {
        assertEquals("Ahmed J.", DailyBoard.displayName("  Ahmed   Javed "))
        assertEquals("Ahmed K.", DailyBoard.displayName("Ahmed Javed Khan"))
        assertEquals("Ada", DailyBoard.displayName("Ada"))
        assertEquals(DailyBoard.fallbackName, DailyBoard.displayName(null))
        assertEquals(DailyBoard.fallbackName, DailyBoard.displayName("You"))
        assertEquals(DailyBoard.fallbackName, DailyBoard.displayName("ahmadjaved225@gmail.com"))
        assertNull(DailyBoard.savedFullName("You"))
        assertNull(DailyBoard.savedFullName("ahmadjaved225@gmail.com"))
        assertNull(DailyBoard.savedFullName("https://example.com"))
        assertEquals("Ahmed Javed", DailyBoard.savedFullName("  Ahmed Javed "))
        assertNull(DailyBoard.savedFullName("A".repeat(41)))
        assertEquals("Ahmed", DailyBoard.displayName("Ahmed https://example.com/x"))
        assertFalse(DailyBoard.displayName("You").contains("@"))
        assertTrue(DailyBoard.displayName("A".repeat(80)).length <= 40)
        assertEquals("Ahmed J.", DailyBoard.boardName("Ahmed", "Javed"))
        assertEquals("Ahmed J.", DailyBoard.boardName(" Ahmed ", " javed "))
        assertNull(DailyBoard.boardName("Ahmed", ""))
        assertNull(DailyBoard.boardName("", "Javed"))
        assertNull(DailyBoard.boardName("You", "Javed"))
        assertNull(DailyBoard.boardName("Ahmed", "You"))
        assertFalse(DailyBoard.boardName("Ahmed", "Javed").orEmpty().equals("You", ignoreCase = true))
        assertTrue(DailyBoard.isRealName("Ahmed Javed"))
        assertFalse(DailyBoard.isRealName("You"))
        assertFalse(DailyBoard.isRealName("RotRun user"))
        assertFalse(DailyBoard.isRealName("a@b.com"))
        assertEquals("Ahmed" to "J", DailyBoard.splitName("Ahmed J."))
        assertEquals("Ahmed" to "", DailyBoard.splitName("Ahmed"))
        assertTrue(DailyBoard.isBoardName("Ahmed J."))
        assertTrue(DailyBoard.isBoardName("Ahmed Javed"))
        assertFalse(DailyBoard.isBoardName("Ahmed"))
        assertFalse(DailyBoard.isBoardName("You"))
    }

    @Test
    fun fingerprintSkipsAnUnchangedUpload() {
        val finger = DailyBoard.fingerprint("user", "2026-11-07", 120, "Ahmed J.")
        assertTrue(DailyBoard.shouldSkip(finger, null, finger))
        assertFalse(DailyBoard.shouldSkip(finger, "{\"xp\":1}", finger))
        assertFalse(DailyBoard.shouldSkip(null, null, finger))
        val withAvatar = DailyBoard.fingerprint("user", "2026-11-07", 120, "Ahmed J.", "https://cdn.example/a.jpg")
        assertFalse(DailyBoard.shouldSkip(finger, null, withAvatar))
        assertFalse(DailyBoard.shouldUpload(authorized = false, hasReading = true))
        assertFalse(DailyBoard.shouldUpload(authorized = true, hasReading = false))
        assertTrue(DailyBoard.shouldUpload(authorized = true, hasReading = true))
    }

    @Test
    fun uploadOmitsAvatarWhenItIsMissing() {
        val bare = DailyBoard.xpUploadBody("user", "2026-11-11", 10, "Ahmed J.", null, "2026-11-11T00:00:00Z")
        assertFalse(bare.has("avatar_url"))
        assertEquals(10, bare.getInt("xp"))
        val blank = DailyBoard.xpUploadBody("user", "2026-11-11", 10, "Ahmed J.", "  ", "2026-11-11T00:00:00Z")
        assertFalse(blank.has("avatar_url"))
        val photo = DailyBoard.xpUploadBody("user", "2026-11-11", 10, "Ahmed J.", "https://cdn.example/a.jpg", "2026-11-11T00:00:00Z")
        assertEquals("https://cdn.example/a.jpg", photo.getString("avatar_url"))
    }

    @Test
    fun outsideRankUsesTheHigherCount() {
        assertEquals(51, DailyBoard.outsideRank(50, 10))
        assertNull(DailyBoard.outsideRank(null, 10))
        assertEquals(7, DailyBoard.countFromContentRange("*/7"))
        assertEquals(120, DailyBoard.countFromContentRange("0-49/120"))
        assertNull(DailyBoard.countFromContentRange(null))
    }
}
