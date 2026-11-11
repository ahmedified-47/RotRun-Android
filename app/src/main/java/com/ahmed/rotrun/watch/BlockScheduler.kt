package com.ahmed.rotrun.watch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.logic.BlockSchedule
import java.time.ZonedDateTime

/** Wakes the blocker at the next schedule edge or when a timed unlock ends. */
object BlockScheduler {
    fun scheduleNext(context: Context, state: SavedState) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pending(context)
        val whenMillis = BlockSchedule.nextWakeMillis(state, ZonedDateTime.now(), System.currentTimeMillis()) ?: run {
            alarm.cancel(pending)
            return
        }
        val exact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        if (exact) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
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
        BlockAccessibilityService.recheck()
    }
}

