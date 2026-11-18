package com.automsgforward.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.automsgforward.sms.InboxCatchUp
import java.util.concurrent.TimeUnit

/**
 * Periodic check that brings forwarding back if the OS killed the app.
 *
 * Some ROMs kill the keep-alive service and block its sticky restart, leaving the app dead
 * until the next reboot. WorkManager survives that, so this job restarts the service and
 * forwards any texts that arrived while it was down.
 */
class WatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        KeepAliveService.sync(applicationContext)
        InboxCatchUp.run(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "keep-alive-watchdog"

        /** WorkManager's minimum periodic interval. */
        private const val INTERVAL_MINUTES = 15L

        /** Idempotent: an already-scheduled watchdog keeps its existing schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
