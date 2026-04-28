package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.R
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext.applicationContext as HabitTrackerApplication
        val container = app.container
        val prefs = container.notificationPreferences.current()
        if (!prefs.dailyReminderEnabled) return@runCatching Result.success()
        if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

        val userId = container.currentUserId()
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val start = today.atStartOfDayIn(tz)
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)

        val count = container.habitLogRepository.countActiveLogsBetween(userId, start, end)
        if (count == 0) {
            fireDailyReminder(applicationContext)
        }
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        const val NOTIF_ID = 4001

        fun fireDailyReminder(context: Context) {
            val builder = NotificationCompat.Builder(context, NotificationChannels.DAILY_REMINDER)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Habitto")
                .setContentText("Log your habits to keep your streak alive.")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
        }
    }
}
