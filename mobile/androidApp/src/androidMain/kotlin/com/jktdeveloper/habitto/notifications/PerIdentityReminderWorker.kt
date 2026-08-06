package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.R
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Fires a per-identity nudge if the user logged no habits linked to the identity today.
 * Identity id is passed via input data under [KEY_IDENTITY_ID].
 */
class PerIdentityReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val identityId = inputData.getString(KEY_IDENTITY_ID) ?: return@runCatching Result.success()
        val app = applicationContext.applicationContext as HabitTrackerApplication
        val container = app.container
        val prefs = container.notificationPreferences.current()
        if (!prefs.masterEnabled) return@runCatching Result.success()
        if (!prefs.isEnabled(NotificationTypeId.DAILY_REMINDER_PER_IDENTITY)) return@runCatching Result.success()
        if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

        val userId = container.currentUserId()
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val start = today.atStartOfDayIn(tz)
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)

        val identity = container.identityRepository.getAllIdentities()
            .firstOrNull { it.id == identityId } ?: return@runCatching Result.success()

        val habits = container.identityRepository
            .observeHabitsForIdentity(userId, identityId)
            .first()
        if (habits.isEmpty()) return@runCatching Result.success()

        val any = habits.any { h ->
            container.habitLogRepository
                .getActiveLogsForHabitOnDay(userId, h.id, start, end)
                .isNotEmpty()
        }
        if (any) return@runCatching Result.success()

        val store = container.notificationFiringDateStore
        val key = NotificationFiringDateStore.perIdentityKey(identityId)
        if (store.getLastFired(key) == today) return@runCatching Result.success()

        val builder = NotificationCompat.Builder(applicationContext, NotificationChannels.REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Habitto")
            .setContentText("${identity.name} hasn't shown up today.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIF_BASE_ID + (identityId.hashCode() and 0xffff), builder.build())
        store.setLastFired(key, today)
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        const val KEY_IDENTITY_ID = "identity_id"
        const val NOTIF_BASE_ID = 5000
    }
}
