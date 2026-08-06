package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.StreakDayState
import com.habittracker.domain.usecase.ExchangeRateCalculator
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
        if (!prefs.masterEnabled) return@runCatching Result.success()
        if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        val range = DateRange(start = yesterday, endExclusive = today.plus(1, DateTimeUnit.DAY))
        val userId = container.currentUserId()
        val result = container.computeStreakUseCase.computeNow(userId, range)
        val yesterdayState = result.days.firstOrNull { it.date == yesterday }?.state

        val firingStore = container.notificationFiringDateStore

        if (prefs.isEnabled(NotificationTypeId.STREAK_FROZEN) && yesterdayState == StreakDayState.FROZEN) {
            if (firingStore.getLastFired(NotificationFiringDateStore.EVENT_FROZEN) != yesterday) {
                fire(applicationContext, NOTIF_FROZEN, NotificationChannels.STATUS,
                    "Missed yesterday. Don't miss today, or your streak resets.",
                    NotificationCompat.PRIORITY_LOW)
                firingStore.setLastFired(NotificationFiringDateStore.EVENT_FROZEN, yesterday)
            }
        }
        if (prefs.isEnabled(NotificationTypeId.STREAK_RESET) && yesterdayState == StreakDayState.BROKEN) {
            if (firingStore.getLastFired(NotificationFiringDateStore.EVENT_RESET) != yesterday) {
                fire(applicationContext, NOTIF_RESET, NotificationChannels.STATUS,
                    "Streak reset. Start fresh today.",
                    NotificationCompat.PRIORITY_LOW)
                firingStore.setLastFired(NotificationFiringDateStore.EVENT_RESET, yesterday)
            }
        }

        if (prefs.isEnabled(NotificationTypeId.TIER_ADVANCED)) {
            val summary = container.computeStreakUseCase.computeSummaryNow(userId)
            val currentTier = ExchangeRateCalculator.tierFor(summary.currentStreak)
            val yesterdayStreak = (summary.currentStreak - 1).coerceAtLeast(0)
            val previousTier = ExchangeRateCalculator.tierFor(yesterdayStreak)
            if (currentTier.level > previousTier.level &&
                firingStore.getLastFired(NotificationFiringDateStore.EVENT_TIER_ADVANCED) != today
            ) {
                fire(applicationContext, NOTIF_TIER, NotificationChannels.STATUS,
                    "Tier ${currentTier.level} unlocked — ${currentTier.rate}× spending.",
                    NotificationCompat.PRIORITY_DEFAULT)
                firingStore.setLastFired(NotificationFiringDateStore.EVENT_TIER_ADVANCED, today)
            }
        }

        Result.success()
    }.getOrElse { Result.retry() }

    private fun fire(context: Context, id: Int, channel: String, body: String, priority: Int) {
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Habitto")
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(priority)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    companion object {
        const val NOTIF_FROZEN = 4003
        const val NOTIF_RESET = 4004
        const val NOTIF_TIER = 4005
    }
}
