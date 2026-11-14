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

    /** First name plus last initial. Both parts are required. "You" is never stored. */
    fun boardName(givenName: String?, familyName: String?): String? {
        val given = cleanPart(givenName) ?: return null
        val family = cleanPart(familyName) ?: return null
        val initial = family.firstOrNull { it.isLetter() } ?: return null
        val shown = "$given ${initial.uppercaseChar()}.".take(40).trim()
        return shown.takeIf { isRealName(it) }
    }

    fun isRealName(raw: String?): Boolean {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.equals("You", ignoreCase = true) || text.equals(fallbackName, ignoreCase = true)) {
            return false
        }
        val shown = displayName(text)
        return shown != fallbackName && !shown.equals("You", ignoreCase = true)
    }

    /** A stored name counts only when both a first name and a last name are present. */
    fun isBoardName(raw: String?): Boolean {
        val (given, family) = splitName(raw)
        return boardName(given, family) != null
    }

    /** Splits a saved board name back into the two fields. A single word leaves the last name blank. */
    fun splitName(raw: String?): Pair<String, String> {
        val parts = raw.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size < 2) return raw.orEmpty().trim() to ""
        val family = parts.last().dropLastWhile { it == '.' }
        return parts.dropLast(1).joinToString(" ") to family
    }

    private fun cleanPart(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.contains("@") || text.contains("://")) return null
        if (text.equals("You", ignoreCase = true)) return null
        return text
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

    /**
     * Place is the row index plus one, matching iOS `loadSupabaseBoard`.
     * Input stays in fetch order: xp descending, updated_at ascending.
     * Equal scores do not share a place.
     */
    fun withRanks(rows: List<XpRow>, youId: String): List<RankedXp> {
        val ordered = dedupe(rows)
        return ordered.mapIndexed { index, row ->
            RankedXp(row.userId, row.name, row.xp, index + 1, youId.isNotBlank() && row.userId == youId, row.avatarUrl)
        }
    }

    /**
     * Left to right across the top avatars: #2, #1, #3.
     * One or two players omit the missing places instead of leaving a gap.
     */
    fun topRow(rows: List<RankedXp>): List<RankedXp> {
        val top = rows.take(3)
        return when (top.size) {
            0 -> emptyList()
            1 -> top
            2 -> listOf(top[1], top[0])
            else -> listOf(top[1], top[0], top[2])
        }
    }

    /** Daily XP upsert. `avatar_url` is omitted when there is no URL, matching iOS `DailyXPSync.publish`. */
    fun xpUploadBody(id: String, day: String, xp: Int, name: String, avatar: String?, updatedAt: String): org.json.JSONObject {
        val body = org.json.JSONObject()
            .put("user_id", id)
            .put("day", day)
            .put("xp", xp.coerceAtLeast(0))
            .put("display_name", name)
            .put("updated_at", updatedAt)
        val column = avatar?.trim()?.takeIf { it.isNotEmpty() }
        if (column != null) body.put("avatar_url", column)
        return body
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
