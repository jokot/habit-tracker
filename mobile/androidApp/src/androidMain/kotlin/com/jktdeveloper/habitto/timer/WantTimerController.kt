package com.jktdeveloper.habitto.timer

import android.content.Context
import android.content.Intent
import android.os.Build
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import com.habittracker.domain.usecase.GetPointBalanceUseCase
import com.habittracker.domain.usecase.InsufficientPointsException
import com.habittracker.domain.usecase.LogWantUseCase
import com.jktdeveloper.habitto.notifications.NotificationPreferences
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What asking for a timer turned into. Lets Home and want detail share one decision —
 * the screens only choose how to render each case.
 */
sealed interface StartTimerOutcome {
    data object Started : StartTimerOutcome
    /** Another want is already counting down; the caller must confirm the replacement. */
    data class NeedsReplace(
        val otherWantName: String,
        val elapsedMin: Int,
        val minutesLeft: Int,
    ) : StartTimerOutcome
    data object NoPoints : StartTimerOutcome
}

class WantTimerController(
    private val context: Context,
    private val repository: WantTimerRepository,
    private val wantActivityRepository: WantActivityRepository,
    private val logWantUseCase: LogWantUseCase,
    private val getPointBalanceUseCase: GetPointBalanceUseCase,
    private val notificationPreferences: NotificationPreferences = NotificationPreferences(context),
    private val clock: Clock = Clock.System,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun start(userId: String, activityId: String, durationSec: Int) {
        require(durationSec in 1..(24 * 60 * 60))
        val balance = getPointBalanceUseCase.execute(userId).getOrThrow().balance
        if (balance <= 0) throw InsufficientPointsException(available = balance, required = 1)
        val now = clock.now()
        val timer = WantTimer(
            id = Uuid.random().toString(),
            userId = userId,
            activityId = activityId,
            durationSec = durationSec,
            startedAt = now,
            endsAt = now + durationSec.seconds,
            state = WantTimerState.RUNNING,
        )
        repository.startReplacing(timer)
        if (timerNotificationsAllowed()) {
            startService(
                Intent(context, WantTimerService::class.java).apply {
                    action = WantTimerService.ACTION_START
                    putExtra(WantTimerService.EXTRA_TIMER_ID, timer.id)
                },
                foreground = true,
            )
        } else {
            // No service means no mandatory ongoing notification — but also no ticker
            // to finish the timer, so hand that to WorkManager.
            WantTimerFinalizeWorker.enqueue(context, durationSec)
        }
    }

    /**
     * Start unless another want is mid-countdown, in which case the caller decides
     * whether to replace it. A timer already running on *this* want is not an overlap —
     * `start` replaces it, which is what tapping the same want again means.
     */
    suspend fun startUnlessOverlapping(
        userId: String,
        activityId: String,
        durationSec: Int,
    ): StartTimerOutcome {
        val active = repository.getActive(userId)
        if (active != null && active.activityId != activityId) {
            val other = wantActivityRepository
                .getAllWantActivitiesForUser(userId)
                .firstOrNull { it.id == active.activityId }
            val now = clock.now()
            return StartTimerOutcome.NeedsReplace(
                otherWantName = other?.name ?: "another want",
                elapsedMin = ((now - active.startedAt).inWholeSeconds / 60).coerceAtLeast(0).toInt(),
                minutesLeft = ((active.endsAt - now).inWholeSeconds / 60).coerceAtLeast(0).toInt(),
            )
        }
        return startOrNoPoints(userId, activityId, durationSec)
    }

    /** Confirmed replacement: log what the running timer earned, then start the new one. */
    suspend fun replaceAndStart(
        userId: String,
        activityId: String,
        durationSec: Int,
    ): StartTimerOutcome {
        cancelWithPartialLog(userId)
        signalServiceStop()
        return startOrNoPoints(userId, activityId, durationSec)
    }

    private suspend fun startOrNoPoints(
        userId: String,
        activityId: String,
        durationSec: Int,
    ): StartTimerOutcome = try {
        start(userId, activityId, durationSec)
        StartTimerOutcome.Started
    } catch (e: InsufficientPointsException) {
        StartTimerOutcome.NoPoints
    }

    /**
     * A running foreground service always shows a notification, so "timer notifications
     * off" has to mean "no foreground service". Deliberately not gated on
     * POST_NOTIFICATIONS: without that permission nothing is displayed anyway, and the
     * service is the more reliable way to finish the timer.
     */
    private suspend fun timerNotificationsAllowed(): Boolean {
        val prefs = notificationPreferences.current()
        return prefs.masterEnabled && prefs.isEnabled(NotificationTypeId.WANT_TIMER_END)
    }

    /**
     * Flips active timer → CANCELLED. For `unit == "min"` with elapsed ≥ 1 min,
     * logs partial duration via [LogWantUseCase]. Returns [CancelResult] for UI
     * feedback. Does NOT signal the service to stop — callers must invoke
     * [signalServiceStop] separately.
     */
    suspend fun cancelWithPartialLog(userId: String): CancelResult {
        val active = repository.getActive(userId) ?: return CancelResult.NoActiveTimer
        val elapsedSec = (clock.now() - active.startedAt).inWholeSeconds.coerceAtLeast(0)
        val elapsedMin = (elapsedSec / 60).toInt()
        val activity = wantActivityRepository
            .getAllWantActivitiesForUser(userId)
            .firstOrNull { it.id == active.activityId }

        val loggedPoints: Int? = if (activity != null && activity.unit == "min" && elapsedMin >= 1) {
            logWantUseCase.execute(
                userId = userId,
                activityId = active.activityId,
                taps = elapsedMin,
                deviceMode = DeviceMode.THIS_DEVICE,
            ).fold(
                onSuccess = { it.pointsSpent },
                onFailure = { null },
            )
        } else null

        repository.setState(active.id, WantTimerState.CANCELLED)
        return if (loggedPoints != null) {
            CancelResult.Logged(elapsedMin, loggedPoints)
        } else {
            CancelResult.Discarded
        }
    }

    fun signalServiceStop() {
        // The no-service path has a pending worker instead; cancelling the timer has to
        // cancel that too, or it fires late and finalizes a timer that's already gone.
        runCatching { WantTimerFinalizeWorker.cancel(context) }
        // Not a new foreground promotion — the service is already foreground (or not running
        // at all). Calling startForegroundService() here would require the STOP handler to also
        // call startForeground(), which it doesn't, and Android kills the app with
        // ForegroundServiceDidNotStartInTimeException if that contract isn't met.
        startService(
            Intent(context, WantTimerService::class.java).apply {
                action = WantTimerService.ACTION_STOP
            },
            foreground = false,
        )
    }

    private fun startService(intent: Intent, foreground: Boolean) {
        runCatching {
            if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
