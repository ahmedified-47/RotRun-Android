package com.ahmed.rotrun.logic

data class XpRow(val userId: String, val name: String, val xp: Int)

data class RankedXp(val userId: String, val name: String, val xp: Int, val rank: Int, val you: Boolean)

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
        return formatted.take(40).trim().ifBlank { fallbackName }
    }

    fun fingerprint(userId: String, day: String, xp: Int, name: String): String = "$userId|$day|$xp|$name"

    fun shouldUpload(authorized: Boolean, hasReading: Boolean): Boolean = authorized && hasReading

    fun shouldSkip(storedFingerprint: String?, pending: String?, fingerprint: String): Boolean =
        pending.isNullOrBlank() && storedFingerprint == fingerprint

    fun dedupe(rows: List<XpRow>): List<XpRow> {
        val seen = mutableSetOf<String>()
        return rows.mapNotNull { row ->
            val id = row.userId.trim()
            if (id.isEmpty() || !seen.add(id)) null
            else row.copy(userId = id, name = row.name.ifBlank { fallbackName }, xp = row.xp.coerceAtLeast(0))
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
            RankedXp(row.userId, row.name, row.xp, rank, youId.isNotBlank() && row.userId == youId)
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
