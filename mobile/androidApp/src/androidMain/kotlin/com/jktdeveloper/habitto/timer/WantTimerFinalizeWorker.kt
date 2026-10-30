package com.jktdeveloper.habitto.timer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.usecase.WantTimerPoints
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.widget.WidgetUpdates
import kotlinx.datetime.Instant
import java.util.concurrent.TimeUnit

/**
 * Spends the points of a want timer, and finishes it, when no foreground service runs it.
 *
 * A foreground service must post a visible notification for as long as it lives —
 * Android gives no way to hide it — so with timer notifications switched off we don't
 * start one. Something still has to spend 1 point at the end of each block, and flip
 * the timer to FINISHED at `endsAt`, and this is it.
 *
 * Reuses [WantTimerRecovery.scanOnStart], which charges every RUNNING timer and enqueues
 * the next run. A charge spends only what the timer still owes, so a run is a no-op if
 * the service (or an app launch) got there first, and a late run catches up.
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
         * One pending run at a time — a new timer replaces any older one, and
         * REPLACE also covers a timer cancelled before its worker fired.
         * The run is at the next charge of [timer]: the end of a block, or the end.
         */
        fun enqueueNextCharge(context: Context, timer: WantTimer, now: Instant) {
            val delayMs = (WantTimerPoints.nextChargeAt(timer, now) - now).inWholeMilliseconds
            val request = OneTimeWorkRequestBuilder<WantTimerFinalizeWorker>()
                // A small margin, so that the run comes after the block ends, not before.
                .setInitialDelay(delayMs.coerceAtLeast(0) + 1_000, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
