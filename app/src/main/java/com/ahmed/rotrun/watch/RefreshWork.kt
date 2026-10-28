package com.ahmed.rotrun.watch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ahmed.rotrun.data.RotRunGraph
import java.util.concurrent.TimeUnit

/** Periodic refresh. Schedule edges still use AlarmManager. Focus uses the foreground service. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!RotRunGraph.ready()) return Result.success()
        val repo = RotRunGraph.repo
        repo.refreshUsage()
        BlockScheduler.scheduleNext(applicationContext, repo.state.value)
        RotNudges.consider(applicationContext, repo)
        return Result.success()
    }
}

object RefreshWork {
    fun enqueue(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "rotrun.refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
