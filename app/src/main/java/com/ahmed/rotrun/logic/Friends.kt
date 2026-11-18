package com.ahmed.rotrun.logic

import java.util.Locale

/**
 * Friend rules shared with iOS `FriendCameoPick`, `FriendshipPair`, and `FriendCode`
 * in commit 04683512. The shield and the intervention both call [FriendCameoPick.select].
 * Only ids passed in as accepted friends can be chosen.
 */
object FriendCode {
    const val length = 6
    const val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Spaces and dashes are ignored. Anything else the wrong length or alphabet is refused. */
    fun normalize(raw: String): String? {
        val cleaned = raw.uppercase(Locale.US).filter { it.isLetter() || it.isDigit() }
        if (cleaned.length != length) return null
        if (cleaned.any { it !in alphabet }) return null
        return cleaned
    }
}

/** Unordered pair. The same two accounts are one friendship either way. A self pair is refused. */
data class FriendshipPair(val low: String, val high: String) {
    companion object {
        fun of(left: String, right: String): FriendshipPair? {
            val a = canonical(left) ?: return null
            val b = canonical(right) ?: return null
            if (a == b) return null
            return if (a < b) FriendshipPair(a, b) else FriendshipPair(b, a)
        }

        private fun canonical(raw: String): String? {
            val trimmed = raw.trim().lowercase(Locale.US)
            if (!UUID_PATTERN.matches(trimmed)) return null
            return trimmed
        }

        private val UUID_PATTERN =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}

data class FriendshipRecord(
    val id: String,
    val requesterId: String,
    val addresseeId: String,
    val status: String,
)

data class FriendGraph(
    val yourUserId: String,
    val records: List<FriendshipRecord>,
    val inviteCode: String?,
    val friendXp: List<FriendXPRow>,
    val names: Map<String, String>,
)

data class FriendXPRow(
    val userId: String,
    val name: String,
    val xp: Int,
    val avatarUrl: String?,
)

enum class FriendActionState { Add, Pending, Incoming, Friends }

object FriendCameoPick {
    const val fallback = "No friends on the board yet. Your Daily XP still counts."

    data class Candidate(
        val userId: String,
        val name: String,
        val xp: Int,
        val avatarUrl: String? = null,
    )

    data class Choice(
        val userId: String,
        val name: String,
        val xp: Int,
        val yourXp: Int,
        val avatarUrl: String?,
        val line: String,
    )

    /**
     * Prefers someone strictly ahead on today's XP, and among those the highest XP.
     * If nobody is ahead, the accepted friend with the highest XP is used.
     * The signed-in user is never chosen.
     */
    fun select(friends: List<Candidate>, yourUserId: String, yourXp: Int): Choice? {
        val me = canonical(yourUserId)
        val yours = yourXp.coerceAtLeast(0)
        val accepted = friends.filter { candidate ->
            val id = canonical(candidate.userId)
            id.isNotEmpty() && id != me && candidate.xp >= 0
        }
        if (accepted.isEmpty()) return null
        val ahead = accepted.filter { it.xp > yours }
        val pool = if (ahead.isEmpty()) accepted else ahead
        val best = pool.maxWith(Comparator { lhs, rhs ->
            if (lhs.xp != rhs.xp) return@Comparator lhs.xp.compareTo(rhs.xp)
            val names = displayName(rhs.name).compareTo(displayName(lhs.name))
            if (names != 0) return@Comparator names
            canonical(rhs.userId).compareTo(canonical(lhs.userId))
        })
        val name = displayName(best.name)
        val xp = best.xp.coerceAtLeast(0)
        return Choice(
            userId = best.userId,
            name = name,
            xp = xp,
            yourXp = yours,
            avatarUrl = httpsAvatar(best.avatarUrl),
            line = "$name is at $xp XP, you're at $yours",
        )
    }

    /** First name and last initial. "Sara Jones" and "Sara J." both become "Sara J." */
    fun displayName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.contains("@")) return "A friend"
        val parts = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val first = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return "A friend"
        val firstName = first.take(40)
        if (parts.size < 2) return firstName
        val last = parts[1].trim('.')
        val initial = last.firstOrNull() ?: return firstName
        return "$firstName ${initial.uppercaseChar()}."
    }

    fun otherId(record: FriendshipRecord, me: String): String =
        if (record.requesterId.equals(me, ignoreCase = true)) record.addresseeId else record.requesterId

    fun match(me: String, other: String, records: List<FriendshipRecord>): FriendshipRecord? {
        val wanted = FriendshipPair.of(me, other) ?: return null
        return records.firstOrNull { record ->
            FriendshipPair.of(record.requesterId, record.addresseeId) == wanted
        }
    }

    fun actionState(me: String, other: String, records: List<FriendshipRecord>): FriendActionState {
        val record = match(me, other, records) ?: return FriendActionState.Add
        return when (record.status) {
            "accepted" -> FriendActionState.Friends
            "pending" -> if (record.requesterId.equals(me, ignoreCase = true)) FriendActionState.Pending else FriendActionState.Incoming
            else -> FriendActionState.Add
        }
    }

    fun httpsAvatar(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        return trimmed
    }

    private fun canonical(raw: String): String = raw.trim().lowercase(Locale.US)
}

data class FriendCameoPayload(
    val writtenAtMillis: Long,
    val yourUserId: String,
    val yourXp: Int,
    val friends: List<FriendCameoPick.Candidate>,
)

sealed class FriendCameoSurface {
    data class Friend(val choice: FriendCameoPick.Choice) : FriendCameoSurface()
    data class Fallback(val text: String) : FriendCameoSurface()
    data object Hidden : FriendCameoSurface()
}

object FriendCameoCache {
    const val ttlMillis = 15 * 60 * 1000L

    /** `enabled == false` keeps the older shield copy. A missing, stale, or empty cache is the fallback. */
    fun resolve(payload: FriendCameoPayload?, enabled: Boolean, nowMillis: Long): FriendCameoSurface {
        if (!enabled) return FriendCameoSurface.Hidden
        if (payload == null) return FriendCameoSurface.Fallback(FriendCameoPick.fallback)
        val age = nowMillis - payload.writtenAtMillis
        if (age < -60_000L || age > ttlMillis) return FriendCameoSurface.Fallback(FriendCameoPick.fallback)
        val choice = FriendCameoPick.select(payload.friends, payload.yourUserId, payload.yourXp)
            ?: return FriendCameoSurface.Fallback(FriendCameoPick.fallback)
        return FriendCameoSurface.Friend(choice)
    }
}
