package com.ahmed.rotrun.logic

/**
 * Daily intervention counters. Same outcomes as iOS `DashboardViewModel`:
 * one Ask when the popup is shown, nothing more for Nah or a timed unlock,
 * and 100 XP for each ransom. A re-shown overlay or a process restart passes
 * the same open presentation and does not add another Ask.
 */
object InterventionXp {
    const val ASK_XP = 20
    const val RANSOM_XP = 100
    val UNLOCK_MINUTES = setOf(10, 20, 30)

    data class DayLedger(
        val xpDay: String = "",
        val askBrainCount: Int = 0,
        val ransomDebit: Int = 0,
        val askOpen: Boolean = false,
        val lastAskToken: Long = 0L,
    )

    /** Local midnight clears today's counters. A new day is a new presentation. */
    fun roll(ledger: DayLedger, today: String): DayLedger {
        if (ledger.xpDay == today) return ledger
        return DayLedger(xpDay = today)
    }

    /**
     * Count one Ask the first time this presentation is shown.
     * [eventId] is the activity token when there is one. A new timestamp while
     * the presentation is still open does not count. The same token after a
     * relaunch does not count.
     */
    fun presentAsk(ledger: DayLedger, eventId: Long, today: String): DayLedger {
        val current = roll(ledger, today)
        if (current.askOpen) return current
        if (eventId != 0L && eventId == current.lastAskToken) return current
        val token = if (eventId != 0L) eventId else current.lastAskToken
        return current.copy(askBrainCount = current.askBrainCount + 1, askOpen = true, lastAskToken = token)
    }

    /** Nah, you're right. The Ask was already counted when the popup appeared. */
    fun dismiss(ledger: DayLedger): DayLedger = ledger.copy(askOpen = false)

    /** 10, 20, and 30 minute unlocks do not change the Ask or ransom counters. */
    fun unlock(ledger: DayLedger, minutes: Int): DayLedger = ledger.copy(askOpen = false)

    /** One ransom payment. Calling it again is another payment. */
    fun payRansom(ledger: DayLedger, today: String): DayLedger {
        val current = roll(ledger, today)
        return current.copy(ransomDebit = current.ransomDebit + RANSOM_XP)
    }
}
