package com.jktdeveloper.habitto.util

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Emits today's [LocalDate] on subscribe and re-emits whenever the local calendar day
 * changes. Polls every [pollInterval] (default 60s) to detect day flips.
 *
 * Polling instead of `delay(timeUntilMidnight)` because `delay` does not advance the
 * coroutine clock while the host process is suspended (e.g. laptop sleep with the
 * emulator paused). On resume, a delay-until-midnight scheduled before sleep would
 * still hold the same remaining duration even though wall-clock midnight has passed.
 * Polling at a fixed cadence converges within one interval after wake.
 */
fun dayBoundaryFlow(
    tz: TimeZone = TimeZone.currentSystemDefault(),
    clock: Clock = Clock.System,
    pollIntervalMs: Long = 60_000L,
): Flow<LocalDate> = flow {
    var lastEmitted: LocalDate? = null
    while (currentCoroutineContext().isActive) {
        val today = clock.now().toLocalDateTime(tz).date
        if (today != lastEmitted) {
            emit(today)
            lastEmitted = today
        }
        delay(pollIntervalMs)
    }
}
