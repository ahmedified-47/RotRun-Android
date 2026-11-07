package com.ahmed.rotrun.logic

import java.net.URLEncoder

/** xAI realtime session details. The API key stays on the server. */
object VoiceSession {
    fun realtimeUrl(model: String, agentId: String): String {
        val safeModel = model.trim().ifBlank { "grok-voice-latest" }
        val base = "wss://api.x.ai/v1/realtime?model=${encode(safeModel)}"
        val agent = agentId.trim()
        return if (agent.isEmpty()) base else "$base&agent_id=${encode(agent)}"
    }

    /** Standing personality is included only when the saved agent id is absent. */
    fun instructions(agentId: String, personality: String, live: String): String {
        val reading = live.trim()
        if (agentId.isNotBlank()) return reading
        val standing = personality.trim()
        return if (standing.isEmpty()) reading else "$standing\n$reading"
    }

    fun liveReading(authorized: Boolean, screen: String, pickups: String, offenders: String): String {
        val screenLine = if (authorized && screen.isNotBlank()) screen else "not in yet"
        val pickupLine = if (authorized && pickups.isNotBlank()) pickups else "not in yet"
        val offenderLine = if (authorized && offenders.isNotBlank()) offenders else "not in yet"
        return """
            You are Rot, the user's brain, on a live voice call. Sound warm and brief, like a friend.
            Environment from the stored home reading:
            screen_time=$screenLine
            pickups=$pickupLine
            top_offenders=$offenderLine
            leaderboard=unknown
            Answer the field they asked about. If they ask about pickups, use pickups only. If they ask about top offenders, use top_offenders only. If a field says not in yet, say it is not in yet. Do not answer a pickups or top offenders question with the screen time line. Never invent minutes, app names, ranks, or scores. If leaderboard says unknown, say you do not have the scores.
        """.trimIndent()
    }

    fun serverMessage(error: String?, message: String?, fallback: String): String {
        val exact = error?.trim().orEmpty().ifBlank { message?.trim().orEmpty() }
        return exact.ifBlank { fallback }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
