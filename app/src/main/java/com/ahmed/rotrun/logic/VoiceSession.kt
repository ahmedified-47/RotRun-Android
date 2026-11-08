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

    /** agent-qa returns this on HTTP 429 when the user has no verified entitlements row. */
    fun isPaywallRequired(errorCode: String?): Boolean = errorCode == "paywall_required"

    /**
     * A 429 that is not paywall_required is a voice cap. paywall_required must not be
     * reported as "resets tomorrow".
     */
    fun isVoiceLimit(status: Int, errorCode: String?): Boolean {
        if (isPaywallRequired(errorCode)) return false
        if (errorCode == "voice_daily_limit" || errorCode == "voice_monthly_limit" || errorCode == "budget_exhausted") {
            return true
        }
        return status == 429
    }

    enum class PaywallAction { ShowPaywall, RetryPurchaseSync, ShowVerificationFailure }

    /** A local Play grant with no server row retries the purchase upload once, then says why. */
    fun paywallAction(locallyPremium: Boolean, alreadyRetried: Boolean): PaywallAction = when {
        !locallyPremium -> PaywallAction.ShowPaywall
        !alreadyRetried -> PaywallAction.RetryPurchaseSync
        else -> PaywallAction.ShowVerificationFailure
    }

    fun spokenFailure(error: String?, message: String?, fallback: String): String {
        val reason = serverMessage(error, message, "")
        if (reason.isBlank()) return fallback
        if (reason.startsWith("Voice")) return reason
        return "Voice couldn't start. $reason"
    }

    fun verificationFailure(reason: String?): String {
        val detail = reason?.trim().orEmpty().ifBlank {
            "This Google account has no active RotRun Premium purchase to verify."
        }
        return spokenFailure(detail, null, detail)
    }

    fun noticeCanRetry(message: String): Boolean {
        val text = message.lowercase()
        if (text.contains("premium")) return false
        if (text.contains("limit")) return false
        if (text.contains("permission")) return false
        if (text.contains("sign in")) return false
        return true
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
