package com.jktdeveloper.habitto.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.MainActivity
import com.jktdeveloper.habitto.R
import com.jktdeveloper.habitto.notifications.NotificationChannels
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import com.jktdeveloper.habitto.notifications.PermissionUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class WantTimerService : LifecycleService() {

    private var tickJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_TIMER_ID) ?: run { stopSelf(); return START_NOT_STICKY }
                startForegroundForTimer()
                tickJob?.cancel()
                tickJob = lifecycleScope.launch { runUntilEnd(id) }
            }
            ACTION_STOP -> {
                tickJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundForTimer() {
        val n = buildRunningNotification(remaining = "starting…", activityName = null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_RUNNING_ID, n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_RUNNING_ID, n)
        }
    }

    private suspend fun runUntilEnd(timerId: String) {
        val container = (applicationContext as HabitTrackerApplication).container
        val repo = container.wantTimerRepository
        val initial = repo.getById(timerId) ?: run {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        val activity = container.wantActivityRepository
            .getAllWantActivitiesForUser(initial.userId)
            .firstOrNull { it.id == initial.activityId }
        val activityName = activity?.name ?: "Timer"

        while (true) {
            val current = repo.getById(timerId) ?: break
            if (current.state != WantTimerState.RUNNING) break
            val now = Clock.System.now()
            val remainingSec = (current.endsAt - now).inWholeSeconds.coerceAtLeast(0)
            if (remainingSec <= 0) {
                onTimerFinished(current, activityName)
                break
            }
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIF_RUNNING_ID, buildRunningNotification(formatMmSs(remainingSec.toInt()), activityName))
            val tickDelay = ((remainingSec % 60).coerceAtLeast(1) * 1000L).coerceAtMost(60_000L)
            delay(tickDelay)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun onTimerFinished(timer: WantTimer, activityName: String) {
        val container = (applicationContext as HabitTrackerApplication).container
        val repo = container.wantTimerRepository
        repo.setState(timer.id, WantTimerState.FINISHED)

        val activity = container.wantActivityRepository
            .getAllWantActivitiesForUser(timer.userId)
            .firstOrNull { it.id == timer.activityId }
        val pointsSegment: String = if (activity != null && activity.unit == "min") {
            val taps = (timer.durationSec / 60).coerceAtLeast(1)
            val result = container.logWantUseCase.execute(
                userId = timer.userId,
                activityId = timer.activityId,
                taps = taps,
                deviceMode = DeviceMode.THIS_DEVICE,
            )
            result.fold(
                onSuccess = { " · $taps min logged · −${it.pointsSpent} pt" },
                onFailure = { "" },
            )
        } else ""

        val prefs = container.notificationPreferences.current()
        val canFire = prefs.masterEnabled
            && prefs.isEnabled(NotificationTypeId.WANT_TIMER_END)
            && PermissionUtils.hasNotificationPermission(applicationContext)
        if (canFire) {
            val builder = NotificationCompat.Builder(applicationContext, NotificationChannels.WANT_TIMER_END)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Habitto")
                .setContentText("$activityName timer finished$pointsSegment")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppPendingIntent())
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_END_ID, builder.build())
        }
    }

    private fun buildRunningNotification(remaining: String, activityName: String?): Notification {
        val cancelIntent = Intent(this, WantTimerService::class.java).apply { action = ACTION_STOP }
        val cancelPi = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = activityName?.let { "$it timer" } ?: "Want timer"
        return NotificationCompat.Builder(this, NotificationChannels.WANT_TIMER_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("$remaining remaining")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent())
            .addAction(0, "Cancel", cancelPi)
            .build()
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_START = "com.jktdeveloper.habitto.timer.START"
        const val ACTION_STOP = "com.jktdeveloper.habitto.timer.STOP"
        const val EXTRA_TIMER_ID = "timer_id"
        const val NOTIF_RUNNING_ID = 4201
        const val NOTIF_END_ID = 4202

        fun formatMmSs(totalSec: Int): String {
            val m = totalSec / 60
            val s = totalSec % 60
            return "%02d:%02d".format(m, s)
        }
    }
}
