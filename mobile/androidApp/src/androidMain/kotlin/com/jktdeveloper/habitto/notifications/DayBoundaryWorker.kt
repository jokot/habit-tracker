package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.StreakDayState
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.R
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class DayBoundaryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext.applicationContext as HabitTrackerApplication
        val container = app.container
        val prefs = container.notificationPreferences.current()
        if (!prefs.streakFrozenEnabled && !prefs.streakResetEnabled) return@runCatching Result.success()
        if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        val range = DateRange(start = yesterday, endExclusive = today.plus(1, DateTimeUnit.DAY))
        val userId = container.currentUserId()
        val result = container.computeStreakUseCase.computeNow(userId, range)
        val yesterdayState = result.days.firstOrNull { it.date == yesterday }?.state

        val firingStore = container.notificationFiringDateStore
        when (yesterdayState) {
            StreakDayState.FROZEN -> if (prefs.streakFrozenEnabled) {
                if (firingStore.getLastFired(NotificationFiringDateStore.EVENT_FROZEN) != yesterday) {
                    fire(applicationContext, NOTIF_FROZEN, "Missed yesterday. Don't miss today, or your streak resets.")
                    firingStore.setLastFired(NotificationFiringDateStore.EVENT_FROZEN, yesterday)
                }
            }
            StreakDayState.BROKEN -> if (prefs.streakResetEnabled) {
                if (firingStore.getLastFired(NotificationFiringDateStore.EVENT_RESET) != yesterday) {
                    fire(applicationContext, NOTIF_RESET, "Streak reset. Start fresh today.")
                    firingStore.setLastFired(NotificationFiringDateStore.EVENT_RESET, yesterday)
                }
            }
            else -> Unit
        }

        Result.success()
    }.getOrElse { Result.retry() }

    private fun fire(context: Context, id: Int, body: String) {
        val builder = NotificationCompat.Builder(context, NotificationChannels.STREAK_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Habitto")
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    companion object {
        const val NOTIF_FROZEN = 4003
        const val NOTIF_RESET = 4004
    }
}
