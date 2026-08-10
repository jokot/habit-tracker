package com.jktdeveloper.habitto.timer

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantTimerState
import com.habittracker.domain.usecase.LogWantUseCase
import com.jktdeveloper.habitto.R
import com.jktdeveloper.habitto.notifications.NotificationChannels
import com.jktdeveloper.habitto.notifications.NotificationPreferences
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import com.jktdeveloper.habitto.notifications.PermissionUtils
import kotlinx.datetime.Clock

/**
 * On app start, finalize any RUNNING timers whose endsAt has passed (OS killed the
 * service during low memory or the device rebooted). Resumes still-running ones
 * by re-launching the service.
 */
class WantTimerRecovery(
    private val context: Context,
    private val timerRepo: WantTimerRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val logWantUseCase: LogWantUseCase,
    private val notificationPreferences: NotificationPreferences,
) {
    suspend fun scanOnStart() {
        val now = Clock.System.now()
        val running = timerRepo.getAllRunning()
        for (t in running) {
            if (t.endsAt <= now) {
                timerRepo.setState(t.id, WantTimerState.FINISHED)
                val activity = wantActivityRepo
                    .getAllWantActivitiesForUser(t.userId)
                    .firstOrNull { it.id == t.activityId }
                val pointsSegment: String = if (activity != null && activity.unit == "min") {
                    val taps = (t.durationSec / 60).coerceAtLeast(1)
                    val result = logWantUseCase.execute(
                        userId = t.userId,
                        activityId = t.activityId,
                        taps = taps,
                        deviceMode = DeviceMode.THIS_DEVICE,
                    )
                    result.fold(
                        onSuccess = { " · $taps min logged · −${it.pointsSpent} pt" },
                        onFailure = { "" },
                    )
                } else ""
                postFinishedNotif(activity?.name ?: "Timer", pointsSegment)
            } else if (timerNotificationsAllowed()) {
                val intent = Intent(context, WantTimerService::class.java).apply {
                    action = WantTimerService.ACTION_START
                    putExtra(WantTimerService.EXTRA_TIMER_ID, t.id)
                }
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            } else {
                // Same deal as WantTimerController.start: a foreground service would post
                // an ongoing notification the user switched off, so finish by worker.
                WantTimerFinalizeWorker.enqueue(
                    context,
                    (t.endsAt - now).inWholeSeconds.coerceAtLeast(1).toInt(),
                )
            }
        }
    }

    private suspend fun timerNotificationsAllowed(): Boolean {
        val prefs = notificationPreferences.current()
        return prefs.masterEnabled && prefs.isEnabled(NotificationTypeId.WANT_TIMER_END)
    }

    private suspend fun postFinishedNotif(activityName: String, pointsSegment: String) {
        if (!timerNotificationsAllowed()) return
        if (!PermissionUtils.hasNotificationPermission(context)) return
        val builder = NotificationCompat.Builder(context, NotificationChannels.WANT_TIMER_END)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Habitto")
            .setContentText("$activityName timer finished$pointsSegment")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        NotificationManagerCompat.from(context).notify(WantTimerService.NOTIF_END_ID, builder.build())
    }
}
