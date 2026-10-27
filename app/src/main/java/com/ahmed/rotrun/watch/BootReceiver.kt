package com.ahmed.rotrun.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ahmed.rotrun.data.RotRunGraph

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!RotRunGraph.ready()) return
        WatchController.sync(context, RotRunGraph.repo.state.value)
    }
}
