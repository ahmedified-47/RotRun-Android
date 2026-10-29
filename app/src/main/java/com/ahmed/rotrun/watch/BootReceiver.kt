package com.ahmed.rotrun.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ahmed.rotrun.data.RotRunGraph

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!RotRunGraph.ready()) return
        val state = RotRunGraph.repo.state.value
        WatchController.sync(context, state)
        BlockScheduler.scheduleNext(context, state)
        RefreshWork.enqueue(context)
    }
}
