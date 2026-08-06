package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

class PerIdentityReminderScheduler(
    private val context: Context,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    /**
     * Cancels any work for identities no longer in [activeIdentityIds],
     * then (re)enqueues a daily work for each active identity at [minutesOfDay].
     */
    fun reconcile(activeIdentityIds: Set<String>, minutesOfDay: Int, previousIdentityIds: Set<String> = emptySet()) {
        val wm = WorkManager.getInstance(context)
        val toCancel = previousIdentityIds - activeIdentityIds
        for (id in toCancel) wm.cancelUniqueWork(workName(id))
        for (id in activeIdentityIds) {
            wm.enqueueUniquePeriodicWork(
                workName(id),
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicAt(minutesOfDay, id),
            )
        }
    }

    fun cancel(identityId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(identityId))
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    private fun periodicAt(minutesOfDay: Int, identityId: String): PeriodicWorkRequest {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val candidate = today.atStartOfDayIn(timeZone) + minutesOfDay.minutes
        val target = if (candidate > now) candidate
            else today.atStartOfDayIn(timeZone) + (minutesOfDay + 24 * 60).minutes
        val initialDelayMs = (target - now).inWholeMilliseconds.coerceAtLeast(0)
        return PeriodicWorkRequestBuilder<PerIdentityReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .setInputData(Data.Builder().putString(PerIdentityReminderWorker.KEY_IDENTITY_ID, identityId).build())
            .build()
    }

    companion object {
        const val TAG = "phase9-per-identity-reminder"
        fun workName(identityId: String) = "phase9-per-identity-$identityId"
    }
}
