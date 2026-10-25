package com.jktdeveloper.habitto.timer

import android.content.Context
import android.content.Intent
import android.os.Build
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.WantTimerState
import com.habittracker.domain.usecase.ChargeWantTimerUseCase
import com.jktdeveloper.habitto.notifications.NotificationPreferences
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import kotlinx.datetime.Clock

/**
 * On app start, and on each run of [WantTimerFinalizeWorker], charge every RUNNING timer.
 * The charge spends the points that the timer owes, and finishes a timer whose endsAt
 * has passed (OS killed the service during low memory or the device rebooted). A timer
 * that still runs continues in the service, or in the next run of the worker.
 */
class WantTimerRecovery(
    private val context: Context,
    private val timerRepo: WantTimerRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val chargeWantTimer: ChargeWantTimerUseCase,
    private val notificationPreferences: NotificationPreferences,
) {
    suspend fun scanOnStart() {
        for (t in timerRepo.getAllRunning()) {
            val charge = chargeWantTimer.charge(t.id) ?: continue
            if (charge.timer.state != WantTimerState.RUNNING) {
                val activityName = wantActivityRepo
                    .getAllWantActivitiesForUser(t.userId)
                    .firstOrNull { it.id == t.activityId }
                    ?.name ?: "Timer"
                onTimerStopped(context, notificationPreferences, activityName, charge)
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
                // an ongoing notification the user switched off, so charge by worker.
                WantTimerFinalizeWorker.enqueueNextCharge(context, charge.timer, Clock.System.now())
            }
        }
    }

    private suspend fun timerNotificationsAllowed(): Boolean {
        val prefs = notificationPreferences.current()
        return prefs.masterEnabled && prefs.isEnabled(NotificationTypeId.WANT_TIMER_END)
    }
}
