package com.ahmed.rotrun.logic

import com.ahmed.rotrun.data.SavedState
import java.time.ZonedDateTime

/** The next moment a block should start or end, including a timed unlock. */
object BlockSchedule {
    fun nextWakeMillis(state: SavedState, now: ZonedDateTime, nowMillis: Long): Long? {
        var best: Long? = null
        fun consider(at: Long) {
            if (at > nowMillis && (best == null || at < best!!)) best = at
        }
        if (state.unlockUntil > nowMillis) consider(state.unlockUntil)
        state.blockers.filter { it.mode == "schedule" || it.mode == "dailyLimit" }.forEach { blocker ->
            listOf(blocker.startHour, blocker.endHour).forEach { hour ->
                consider(edge(now, hour).toInstant().toEpochMilli())
            }
        }
        return best
    }

    fun edge(now: ZonedDateTime, hour: Int): ZonedDateTime {
        val today = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(5).withNano(0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
