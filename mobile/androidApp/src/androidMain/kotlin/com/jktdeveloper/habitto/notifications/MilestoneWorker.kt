package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.R
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class MilestoneWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext.applicationContext as HabitTrackerApplication
        val container = app.container
        val prefs = container.notificationPreferences.current()
        if (!prefs.masterEnabled) return@runCatching Result.success()
        if (!prefs.isEnabled(NotificationTypeId.MILESTONE_STREAK)) return@runCatching Result.success()
        if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

        val userId = container.currentUserId()
        val summary = container.computeStreakUseCase.computeSummaryNow(userId)
        val milestone = milestoneFor(summary.currentStreak) ?: return@runCatching Result.success()

        val store = container.notificationFiringDateStore
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        if (store.getLastFired(milestone.firingKey) == today) return@runCatching Result.success()

        val body = "${milestone.days}-day streak — keep going."
        val builder = NotificationCompat.Builder(applicationContext, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Habitto")
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(applicationContext).notify(milestone.notifId, builder.build())
        store.setLastFired(milestone.firingKey, today)
        Result.success()
    }.getOrElse { Result.retry() }

    data class Milestone(val days: Int, val firingKey: String, val notifId: Int)

    companion object {
        val MILESTONES = listOf(
            Milestone(7,   NotificationFiringDateStore.EVENT_MILESTONE_7,   4011),
            Milestone(30,  NotificationFiringDateStore.EVENT_MILESTONE_30,  4012),
            Milestone(100, NotificationFiringDateStore.EVENT_MILESTONE_100, 4013),
            Milestone(365, NotificationFiringDateStore.EVENT_MILESTONE_365, 4014),
        )

        fun milestoneFor(streak: Int): Milestone? = MILESTONES.firstOrNull { it.days == streak }
    }
}
