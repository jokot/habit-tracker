package com.jktdeveloper.habitto.timer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.widget.WidgetUpdates
import java.util.concurrent.TimeUnit

/**
 * Finalizes a want timer when no foreground service is running it.
 *
 * A foreground service must post a visible notification for as long as it lives —
 * Android gives no way to hide it — so with timer notifications switched off we don't
 * start one. Something still has to flip the timer to FINISHED and log the want at
 * `endsAt`, and this is it.
 *
 * Reuses [WantTimerRecovery.scanOnStart], which finalizes every RUNNING timer already
 * past its end. That makes this idempotent: if the service (or an app launch) got there
 * first, the scan finds nothing and the run is a no-op.
 */
class WantTimerFinalizeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val container = (applicationContext as HabitTrackerApplication).container
        container.wantTimerRecovery.scanOnStart()
        // The service repaints widgets as it ticks; without it, this is the only
        // chance to show the spent points before the user next opens the app.
        WidgetUpdates.updateAll(applicationContext)
        Result.success()
    }.getOrElse { Result.failure() }

    companion object {
        private const val WORK_NAME = "want_timer_finalize"

        /**
         * One pending finalize at a time — a new timer replaces any older one, and
         * REPLACE also covers a timer cancelled before its worker fired.
         */
        fun enqueue(context: Context, delaySec: Int) {
            val request = OneTimeWorkRequestBuilder<WantTimerFinalizeWorker>()
                .setInitialDelay(delaySec.toLong(), TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
