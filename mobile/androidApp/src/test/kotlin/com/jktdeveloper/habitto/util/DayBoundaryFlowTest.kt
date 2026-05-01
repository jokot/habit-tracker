package com.jktdeveloper.habitto.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DayBoundaryFlowTest {

    private class FixedClock(var nowInstant: Instant) : Clock {
        override fun now(): Instant = nowInstant
    }

    @Test
    fun `emits today on subscribe`() = runTest {
        val tz = TimeZone.UTC
        val anchor = LocalDate(2026, 5, 1).atStartOfDayIn(tz)
        val clock = FixedClock(anchor)
        val first = dayBoundaryFlow(tz, clock).take(1).toList().single()
        assertEquals(LocalDate(2026, 5, 1), first)
    }

    @Test
    fun `re-emits when day flips`() = runTest {
        val tz = TimeZone.UTC
        val anchor = LocalDate(2026, 5, 1).atStartOfDayIn(tz)
        val clock = FixedClock(anchor)
        val collected = mutableListOf<LocalDate>()
        val job = launch {
            dayBoundaryFlow(tz, clock).take(2).collect { date ->
                collected += date
                clock.nowInstant = LocalDate(2026, 5, 2).atStartOfDayIn(tz)
            }
        }
        testScheduler.advanceUntilIdle()
        job.join()
        assertEquals(listOf(LocalDate(2026, 5, 1), LocalDate(2026, 5, 2)), collected)
    }
}
