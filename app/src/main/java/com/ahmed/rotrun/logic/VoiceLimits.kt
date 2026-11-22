package com.ahmed.rotrun.logic

/** Pro voice caps. The xAI key stays on the server; this only bounds a session the token already allowed. */
object VoiceLimits {
    const val dailyCap = 5
    const val monthlyCap = 30
    const val maxSeconds = 300.0
    const val dailyMessage = "Voice limit reached, resets tomorrow"
    const val monthlyMessage = "Voice limit reached, resets next month"

    fun denial(dayCount: Int, monthCount: Int): String? = when {
        monthCount >= monthlyCap -> monthlyMessage
        dayCount >= dailyCap -> dailyMessage
        else -> null
    }

    /** Never run longer than five minutes, and never longer than the server token window. */
    fun cappedSeconds(serverSeconds: Double?): Double {
        val server = serverSeconds?.takeIf { it.isFinite() && it > 0.0 } ?: return maxSeconds
        return minOf(server, maxSeconds)
    }
}
