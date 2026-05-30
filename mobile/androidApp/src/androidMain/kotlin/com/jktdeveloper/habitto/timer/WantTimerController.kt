package com.jktdeveloper.habitto.timer

import android.content.Context
import android.content.Intent
import android.os.Build
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import com.habittracker.domain.usecase.LogWantUseCase
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WantTimerController(
    private val context: Context,
    private val repository: WantTimerRepository,
    private val wantActivityRepository: WantActivityRepository,
    private val logWantUseCase: LogWantUseCase,
    private val clock: Clock = Clock.System,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun start(userId: String, activityId: String, durationSec: Int) {
        require(durationSec in 1..(24 * 60 * 60))
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
        startService(Intent(context, WantTimerService::class.java).apply {
            action = WantTimerService.ACTION_START
            putExtra(WantTimerService.EXTRA_TIMER_ID, timer.id)
        })
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
        startService(Intent(context, WantTimerService::class.java).apply {
            action = WantTimerService.ACTION_STOP
        })
    }

    private fun startService(intent: Intent) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
