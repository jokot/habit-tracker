package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jktdeveloper.habitto.HabitTrackerApplication
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class StreakRiskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext.applicationContext as HabitTrackerApplication
        val container = app.container
        val prefs = container.notificationPreferences.current()
        if (!prefs.streakRiskEnabled) return@runCatching Result.success()
        if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

        val userId = container.currentUserId()
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val start = today.atStartOfDayIn(tz)
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)

        val count = container.habitLogRepository.countActiveLogsBetween(userId, start, end)
        if (count > 0) return@runCatching Result.success()

        val summary = container.computeStreakUseCase.computeSummaryNow(userId)
        if (summary.currentStreak <= 0) return@runCatching Result.success()

        val builder = NotificationCompat.Builder(applicationContext, NotificationChannels.STREAK_RISK)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Habitto")
            .setContentText("${summary.currentStreak}-day streak at risk. Log a habit before midnight.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, builder.build())
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        const val NOTIF_ID = 4002
    }
}
