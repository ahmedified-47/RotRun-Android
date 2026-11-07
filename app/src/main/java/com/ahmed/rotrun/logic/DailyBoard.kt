package com.ahmed.rotrun.logic

data class XpRow(val userId: String, val name: String, val xp: Int, val avatarUrl: String? = null)

data class RankedXp(val userId: String, val name: String, val xp: Int, val rank: Int, val you: Boolean, val avatarUrl: String? = null)

data class BoardSnapshot(
    val rows: List<RankedXp> = emptyList(),
    val pinned: RankedXp? = null,
    val offline: Boolean = false,
    val message: String? = null,
    val signedOut: Boolean = false,
)

/** Daily XP board rules shared by the uploader and the Stats screen. */
object DailyBoard {
    const val fallbackName = "RotRun user"

    fun displayName(raw: String?): String {
        var text = raw.orEmpty().trim()
        text = text.replace(Regex("(?i)https?://\\S+"), " ")
        text = text.replace(Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"), " ")
        text = text.replace(Regex("\\s+"), " ").trim()
        if (text.isBlank() || text.contains("@") || text.contains("://")) return fallbackName
        val parts = text.split(" ").filter { part -> part.any(Char::isLetter) }
        if (parts.isEmpty()) return fallbackName
        val formatted = if (parts.size == 1) {
            parts[0]
        } else {
            val initial = parts.last().first { it.isLetter() }.uppercaseChar()
            "${parts.first()} $initial."
        }
        val shown = formatted.take(40).trim().ifBlank { fallbackName }
        if (shown.equals("You", ignoreCase = true)) return fallbackName
        return shown
    }

    /** Name stored in auth metadata. Emails, URLs, and the placeholder You are rejected. */
    fun savedFullName(raw: String?): String? {
        var text = raw.orEmpty().trim()
        text = text.replace(Regex("(?i)https?://\\S+"), " ")
        text = text.replace(Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"), " ")
        text = text.replace(Regex("\\s+"), " ").trim()
        if (text.length !in 1..40) return null
        if (text.equals("You", ignoreCase = true)) return null
        if (displayName(text) == fallbackName) return null
        return text
    }

    fun fingerprint(userId: String, day: String, xp: Int, name: String, avatarUrl: String? = null): String =
        "$userId|$day|$xp|$name|${avatarUrl.orEmpty()}"

    fun shouldUpload(authorized: Boolean, hasReading: Boolean): Boolean = authorized && hasReading

    fun shouldSkip(storedFingerprint: String?, pending: String?, fingerprint: String): Boolean =
        pending.isNullOrBlank() && storedFingerprint == fingerprint

    fun dedupe(rows: List<XpRow>): List<XpRow> {
        val seen = mutableSetOf<String>()
        return rows.mapNotNull { row ->
            val id = row.userId.trim()
            if (id.isEmpty() || !seen.add(id)) null
            else row.copy(userId = id, name = row.name.ifBlank { fallbackName }, xp = row.xp.coerceAtLeast(0), avatarUrl = row.avatarUrl?.takeIf { it.startsWith("http") })
        }
    }

    /** Same xp shares one displayed rank. Input is already xp desc, updated_at asc. */
    fun withRanks(rows: List<XpRow>, youId: String): List<RankedXp> {
        val ordered = dedupe(rows)
        var lastXp = Int.MIN_VALUE
        var lastRank = 0
        return ordered.mapIndexed { index, row ->
            val rank = if (row.xp == lastXp) lastRank else index + 1
            lastXp = row.xp
            lastRank = rank
            RankedXp(row.userId, row.name, row.xp, rank, youId.isNotBlank() && row.userId == youId, row.avatarUrl)
        }
    }

    fun countFromContentRange(header: String?): Int? {
        val total = header?.substringAfter('/', "")?.trim().orEmpty()
        if (total.isBlank() || total == "*") return null
        return total.toIntOrNull()
    }

    fun outsideRank(higher: Int?, yourXp: Int?): Int? {
        if (yourXp == null || higher == null) return null
        return higher + 1
    }
}
