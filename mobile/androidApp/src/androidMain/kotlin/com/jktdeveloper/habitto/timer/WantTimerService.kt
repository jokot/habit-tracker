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
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import com.habittracker.domain.usecase.WantTimerPoints
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.MainActivity
import com.jktdeveloper.habitto.R
import com.jktdeveloper.habitto.notifications.NotificationChannels
import com.jktdeveloper.habitto.widget.WidgetUpdates
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
        val initial = container.wantTimerRepository.getById(timerId) ?: run {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        val activity = container.wantActivityRepository
            .getAllWantActivitiesForUser(initial.userId)
            .firstOrNull { it.id == initial.activityId }
        val activityName = activity?.name ?: "Timer"
        val totalMin = WantTimerPoints.totalMinutes(initial)

        while (true) {
            // Each tick spends the points of the blocks that are complete, and stops the
            // timer at the end or when the balance is too low. Null: the timer stopped
            // somewhere else, for example a cancel on a screen.
            val charge = container.chargeWantTimerUseCase.charge(timerId) ?: break
            runCatching { WidgetUpdates.updateAll(applicationContext) }
            val current = charge.timer
            if (current.state != WantTimerState.RUNNING) {
                onTimerStopped(
                    context = applicationContext,
                    notificationPreferences = container.notificationPreferences,
                    activityName = activityName,
                    charge = charge,
                    contentIntent = openTimerScreenPendingIntent(null),
                )
                break
            }
            val now = Clock.System.now()
            val remainingSec = (current.endsAt - now).inWholeSeconds.coerceAtLeast(0)
            val minLeft = ((remainingSec + 59) / 60).toInt()
            NotificationManagerCompat.from(applicationContext)
                .notify(
                    NOTIF_RUNNING_ID,
                    buildRunningNotification(
                        activityName = activityName,
                        activityId = initial.activityId,
                        minLeft = minLeft,
                        elapsedMin = WantTimerPoints.elapsedMinutes(current, now),
                        totalMin = totalMin,
                        pointsSpent = current.pointsSpent,
                    ),
                )
            delay(untilNextMinute(current, now))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Wake just after the next full minute of the timer. A block and the end are full
     * minutes, so this tick sees them. The margin keeps the tick from coming a little early.
     */
    private fun untilNextMinute(timer: WantTimer, now: Instant): Long {
        val elapsedMs = (now - timer.startedAt).inWholeMilliseconds.coerceAtLeast(0)
        return 60_000L - (elapsedMs % 60_000L) + 250L
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
