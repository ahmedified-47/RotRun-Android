package com.ahmed.rotrun.watch

import android.content.Context
import android.content.Intent
import com.ahmed.rotrun.MainActivity
import com.ahmed.rotrun.data.RotRunGraph

/** Brings RotRun forward onto the permission gate. One launch until arming is restored. */
object GatePrompt {
    const val EXTRA = "open_access_gate"

    @Volatile
    private var brought = false

    @Volatile
    private var armed = false

    /**
     * True only on the edge from disarmed to armed. The caller refreshes access,
     * which clears [brought], so the next revoke can bring RotRun forward again.
     */
    fun observe(ready: Boolean): Boolean {
        if (ready == armed) return false
        armed = ready
        if (!ready) return false
        reset()
        return true
    }

    fun latch(ready: Boolean) {
        armed = ready
    }

    fun open(context: Context) {
        if (!RotRunGraph.ready()) return
        RotRunGraph.repo.requestAccessGate()
        if (brought) return
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
            putExtra(EXTRA, true)
        }
        if (runCatching { context.startActivity(intent) }.isSuccess) brought = true
    }

    fun reset() {
        brought = false
    }
}

/** Remember that a shield was up when Accessibility or overlay dropped, so the app stays covered. */
object CoverLoose {
    @Volatile
    var active: Boolean = false

    fun clear() {
        active = false
    }
}

/** Package to keep covered with the overlay after Accessibility is revoked. */
object CoverHold {
    @Volatile
    var packageName: String? = null

    fun hold(packageName: String) {
        if (packageName.isNotBlank()) this.packageName = packageName
    }

    fun clear() {
        packageName = null
    }
}
