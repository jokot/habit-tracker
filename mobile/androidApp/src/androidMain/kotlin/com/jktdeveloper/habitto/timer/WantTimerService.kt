package com.jktdeveloper.habitto.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
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
            ACTION_STOP_PARTIAL_LOG -> {
                tickJob?.cancel()
                val container = (applicationContext as HabitTrackerApplication).container
                lifecycleScope.launch {
                    runCatching { container.wantTimerController.cancelWithPartialLog(container.currentUserId()) }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundForTimer() {
        val n = buildRunningNotification(
            activityName = null,
            activityId = null,
            minLeft = 0,
            elapsedMin = 0,
            totalMin = 1,
            pointsSpent = 0,
        )
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
            val totalMin = (initial.durationSec / 60).coerceAtLeast(1)
            val minLeft = ((remainingSec + 59) / 60).toInt()
            val elapsedMin = (totalMin - minLeft).coerceAtLeast(0)
            val pointsSpent = elapsedMin / ((activity?.unitsPerPoint ?: 1).coerceAtLeast(1))
            NotificationManagerCompat.from(applicationContext)
                .notify(
                    NOTIF_RUNNING_ID,
                    buildRunningNotification(
                        activityName = activityName,
                        activityId = initial.activityId,
                        minLeft = minLeft,
                        elapsedMin = elapsedMin,
                        totalMin = totalMin,
                        pointsSpent = pointsSpent,
                    ),
                )
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
                .setContentIntent(openTimerScreenPendingIntent(null))
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_END_ID, builder.build())
        }
    }

    private fun buildRunningNotification(
        activityName: String?,
        activityId: String?,
        minLeft: Int,
        elapsedMin: Int,
        totalMin: Int,
        pointsSpent: Int,
    ): Notification {
        val cancelIntent = Intent(this, WantTimerService::class.java).apply { action = ACTION_STOP_PARTIAL_LOG }
        val cancelPi = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = activityName?.let { "$it timer" } ?: "Want timer"
        val body = "$minLeft min left · −$pointsSpent pt spent"
        return NotificationCompat.Builder(this, NotificationChannels.WANT_TIMER_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(totalMin.coerceAtLeast(1), elapsedMin, false)
            .setContentIntent(openTimerScreenPendingIntent(activityId))
            .addAction(0, "Cancel", cancelPi)
            .build()
    }

    private fun openTimerScreenPendingIntent(activityId: String?): PendingIntent {
        val uri = if (activityId != null) {
            Uri.parse("com.jktdeveloper.habitto://want-timer/$activityId")
        } else {
            Uri.parse("com.jktdeveloper.habitto://want-timer")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val requestCode = (activityId?.hashCode() ?: 0) and 0xffff
        return PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_START = "com.jktdeveloper.habitto.timer.START"
        const val ACTION_STOP = "com.jktdeveloper.habitto.timer.STOP"
        const val ACTION_STOP_PARTIAL_LOG = "com.jktdeveloper.habitto.timer.STOP_PARTIAL_LOG"
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
