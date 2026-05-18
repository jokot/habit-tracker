package com.jktdeveloper.habitto.timer

import android.content.Context
import android.content.Intent
import android.os.Build
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * UI-facing facade for starting/cancelling want timers.
 * Persists state via [WantTimerRepository], then nudges [WantTimerService] which owns
 * the foreground countdown notification.
 */
class WantTimerController(
    private val context: Context,
    private val repository: WantTimerRepository,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun start(userId: String, activityId: String, durationSec: Int) {
        require(durationSec in 1..(24 * 60 * 60))
        val now = Clock.System.now()
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

    suspend fun cancel(userId: String) {
        val active = repository.getActive(userId) ?: return
        repository.setState(active.id, WantTimerState.CANCELLED)
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
