package com.ahmed.rotrun.watch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ahmed.rotrun.data.Blocker
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.data.SavedState
import java.time.ZonedDateTime

/** Wakes the blocker at the next schedule edge. Accessibility still checks every app switch. */
object BlockScheduler {
    fun scheduleNext(context: Context, state: SavedState) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pending(context)
        val whenMillis = nextEdge(state) ?: run {
            alarm.cancel(pending)
            return
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
    }

    private fun nextEdge(state: SavedState): Long? {
        val now = ZonedDateTime.now()
        var best: ZonedDateTime? = null
        state.blockers.filter { it.mode == "schedule" || it.mode == "dailyLimit" }.forEach { blocker ->
            listOf(edge(now, blocker.startHour), edge(now, blocker.endHour)).forEach { candidate ->
                if (candidate.isAfter(now) && (best == null || candidate.isBefore(best))) best = candidate
            }
        }
        return best?.toInstant()?.toEpochMilli()
    }

    private fun edge(now: ZonedDateTime, hour: Int): ZonedDateTime {
        val today = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(5).withNano(0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleTickReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            71,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ScheduleTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!RotRunGraph.ready()) return
        val repo = RotRunGraph.repo
        repo.refreshUsage()
        BlockScheduler.scheduleNext(context, repo.state.value)
        WatchController.sync(context, repo.state.value)
    }
}

fun scheduleCovers(blocker: Blocker, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
    val day = now.dayOfWeek.value % 7
    if (day !in blocker.days) return false
    if (blocker.mode != "schedule") return true
    val hour = now.hour
    val start = blocker.startHour
    val end = blocker.endHour
    return if (start == end) true else if (start < end) hour in start until end else hour >= start || hour < end
}
