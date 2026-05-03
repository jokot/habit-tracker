package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.StreakDayState
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ComputePerHabitStreakUseCaseTest {
    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 5, 10)
    private val fakeClock = object : Clock {
        override fun now(): Instant = today.atStartOfDayIn(tz).plus(12, DateTimeUnit.HOUR, tz)
    }
    private val userId = "u1"
    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()

    private fun useCase() = ComputePerHabitStreakUseCase(habitLogRepo, habitRepo, tz, fakeClock)

    private suspend fun seedHabit(
        id: String = "h1",
        threshold: Double = 1.0,
        target: Int = 1,
        effectiveFrom: Instant? = LocalDate(2026, 4, 1).atStartOfDayIn(tz),
        effectiveTo: Instant? = null,
    ) {
        val now = today.atStartOfDayIn(tz)
        habitRepo.saveHabit(
            Habit(
                id = id, userId = userId, templateId = "t", name = "H",
                unit = "p", thresholdPerPoint = threshold, dailyTarget = target,
                createdAt = now, updatedAt = now,
                effectiveFrom = effectiveFrom, effectiveTo = effectiveTo,
            )
        )
    }

    private suspend fun seedLog(habitId: String, date: LocalDate, quantity: Double = 1.0) {
        habitLogRepo.insertLog(
            id = "log-$habitId-$date",
            userId = userId,
            habitId = habitId,
            quantity = quantity,
            loggedAt = date.atStartOfDayIn(tz).plus(10, DateTimeUnit.HOUR, tz),
        )
    }

    @Test
    fun `unknown habit returns empty result`() = runTest {
        val result = useCase().computeNow(userId, habitId = "doesNotExist")
        assertEquals("doesNotExist", result.habitId)
        assertEquals(0, result.totalLogs)
        assertEquals(0, result.currentStreak)
        assertEquals(0, result.longestStreak)
        assertNull(result.firstLogDate)
        assertEquals(30, result.last30Days.size)
        assertEquals(today, result.last30Days.last().date)
    }

    @Test
    fun `totalLogs counts logs for habit only`() = runTest {
        seedHabit("h1")
        seedHabit("h2")
        seedLog("h1", today.minus(1, DateTimeUnit.DAY))
        seedLog("h1", today.minus(2, DateTimeUnit.DAY))
        seedLog("h2", today)
        val result = useCase().computeNow(userId, "h1")
        assertEquals(2, result.totalLogs)
    }

    @Test
    fun `pointsEarned sums respecting threshold`() = runTest {
        seedHabit("h1", threshold = 2.0)
        seedLog("h1", today.minus(1, DateTimeUnit.DAY), quantity = 6.0) // 3 pts
        seedLog("h1", today.minus(2, DateTimeUnit.DAY), quantity = 1.0) // 0 pts
        val result = useCase().computeNow(userId, "h1")
        assertEquals(3, result.pointsEarned)
    }

    @Test
    fun `firstLogDate equals earliest log date`() = runTest {
        seedHabit("h1")
        seedLog("h1", today.minus(5, DateTimeUnit.DAY))
        seedLog("h1", today.minus(3, DateTimeUnit.DAY))
        val result = useCase().computeNow(userId, "h1")
        assertEquals(today.minus(5, DateTimeUnit.DAY), result.firstLogDate)
    }

    @Test
    fun `consecutive complete days produce currentStreak`() = runTest {
        seedHabit("h1")
        for (i in 0..4) seedLog("h1", today.minus(i, DateTimeUnit.DAY))
        val result = useCase().computeNow(userId, "h1")
        assertEquals(5, result.currentStreak)
        assertEquals(5, result.longestStreak)
    }

    @Test
    fun `today not yet logged with yesterday logged keeps streak via TODAY_PENDING`() = runTest {
        seedHabit("h1")
        seedLog("h1", today.minus(1, DateTimeUnit.DAY))
        seedLog("h1", today.minus(2, DateTimeUnit.DAY))
        val result = useCase().computeNow(userId, "h1")
        assertEquals(2, result.currentStreak)
        val todayCell = result.last30Days.last { it.date == today }
        assertEquals(StreakDayState.TODAY_PENDING, todayCell.state)
    }

    @Test
    fun `longest streak finds max run across history`() = runTest {
        seedHabit("h1")
        for (offset in listOf(10, 9, 8, 5, 4, 3, 2, 1)) seedLog("h1", today.minus(offset, DateTimeUnit.DAY))
        val result = useCase().computeNow(userId, "h1")
        assertEquals(5, result.longestStreak)
    }

    @Test
    fun `last30Days has 30 entries ending on today`() = runTest {
        seedHabit("h1")
        seedLog("h1", today)
        val result = useCase().computeNow(userId, "h1")
        assertEquals(30, result.last30Days.size)
        assertEquals(today, result.last30Days.last().date)
        assertEquals(today.minus(29, DateTimeUnit.DAY), result.last30Days.first().date)
    }

    @Test
    fun `past day before habit existed renders as EMPTY not BROKEN`() = runTest {
        val effectiveFrom = today.minus(5, DateTimeUnit.DAY).atStartOfDayIn(tz)
        seedHabit("h1", effectiveFrom = effectiveFrom)
        seedLog("h1", today)
        seedLog("h1", today.minus(1, DateTimeUnit.DAY))
        val result = useCase().computeNow(userId, "h1")
        val tenDaysAgo = today.minus(10, DateTimeUnit.DAY)
        val cell = result.last30Days.firstOrNull { it.date == tenDaysAgo }
        assertNotNull(cell)
        assertEquals(StreakDayState.EMPTY, cell.state)
    }

    @Test
    fun `partial-quantity log still counts as complete day`() = runTest {
        seedHabit("h1", threshold = 5.0)
        seedLog("h1", today.minus(1, DateTimeUnit.DAY), quantity = 1.0)
        seedLog("h1", today, quantity = 1.0)
        val result = useCase().computeNow(userId, "h1")
        assertEquals(2, result.currentStreak)
    }
}
