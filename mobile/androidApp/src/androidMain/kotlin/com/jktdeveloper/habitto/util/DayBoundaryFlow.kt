package com.jktdeveloper.habitto.util

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Emits today's [LocalDate] now and re-emits each time the local calendar day flips.
 * Foreground-only by design: the delay pauses with the host coroutine scope.
 */
fun dayBoundaryFlow(
    tz: TimeZone = TimeZone.currentSystemDefault(),
    clock: Clock = Clock.System,
): Flow<LocalDate> = flow {
    while (currentCoroutineContext().isActive) {
        val now = clock.now()
        val today = now.toLocalDateTime(tz).date
        emit(today)
        val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)
        delay(nextMidnight - now)
    }
}
