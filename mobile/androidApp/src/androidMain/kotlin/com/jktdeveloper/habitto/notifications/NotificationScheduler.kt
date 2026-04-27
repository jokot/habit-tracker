package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.work.CoroutineWorker
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

class NotificationScheduler(
    private val context: Context,
    private val prefs: NotificationPreferences,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun ensureChannels() = NotificationChannels.ensureChannels(context)

    fun cancelAll() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_DAILY)
        wm.cancelUniqueWork(WORK_RISK)
        wm.cancelUniqueWork(WORK_DAY_BOUNDARY)
    }

    suspend fun reschedule() {
        val snap = prefs.current()
        val wm = WorkManager.getInstance(context)

        if (snap.dailyReminderEnabled)
            wm.enqueueUniquePeriodicWork(
                WORK_DAILY,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicAt<DailyReminderWorker>(snap.dailyReminderMinutes),
            )
        else wm.cancelUniqueWork(WORK_DAILY)

        if (snap.streakRiskEnabled)
            wm.enqueueUniquePeriodicWork(
                WORK_RISK,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicAt<StreakRiskWorker>(snap.streakRiskMinutes),
            )
        else wm.cancelUniqueWork(WORK_RISK)

        if (snap.streakFrozenEnabled || snap.streakResetEnabled)
            wm.enqueueUniquePeriodicWork(
                WORK_DAY_BOUNDARY,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicAt<DayBoundaryWorker>(30),  // 00:30 local
            )
        else wm.cancelUniqueWork(WORK_DAY_BOUNDARY)
    }

    private inline fun <reified W : CoroutineWorker> periodicAt(minutesOfDay: Int): PeriodicWorkRequest {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val candidate = today.atStartOfDayIn(timeZone) + minutesOfDay.minutes
        val target = if (candidate > now) candidate
            else today.atStartOfDayIn(timeZone) + (minutesOfDay + 24 * 60).minutes
        val initialDelayMs = (target - now).inWholeMilliseconds.coerceAtLeast(0)
        return PeriodicWorkRequestBuilder<W>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()
    }

    companion object {
        const val WORK_DAILY = "phase4-daily-reminder"
        const val WORK_RISK = "phase4-streak-risk"
        const val WORK_DAY_BOUNDARY = "phase4-day-boundary"
    }
}
