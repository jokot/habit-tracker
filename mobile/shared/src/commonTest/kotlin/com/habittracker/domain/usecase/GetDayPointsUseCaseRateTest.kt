package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeWantActivityRepository
import com.habittracker.data.repository.FakeWantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 6 Task 5: GetDayPoints applies rate-at-day to Want spending.
 * Habit earning remains base-rate (NOT rate-multiplied).
 */
class GetDayPointsUseCaseRateTest {
    private val tz = TimeZone.UTC
    private val userId = "u1"

    @Test
    fun `day with streak 14 applies rate 1_2 to want spend on that day`() = runTest {
        val today = LocalDate(2026, 5, 30)
        val now = LocalDateTime(today, LocalTime(20, 0)).toInstant(tz)
        val clock = object : Clock { override fun now(): Instant = now }
        val habitRepo = FakeHabitRepository()
        val habitLogRepo = FakeHabitLogRepository()
        val wantActivityRepo = FakeWantActivityRepository()
        val wantLogRepo = FakeWantLogRepository()

        val anchor = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz)
        habitRepo.saveHabit(
            Habit(
                id = "h1", userId = userId, templateId = null, name = "h", unit = "x",
                thresholdPerPoint = 1.0, dailyTarget = 5,
                createdAt = anchor, updatedAt = anchor, effectiveFrom = anchor,
            )
        )
        // 14 consecutive complete days ending today → streak=14 → rate 1.2
        (0..13).forEach { offset ->
            val d = today.minus(offset, DateTimeUnit.DAY)
            habitLogRepo.insertLog(
                id = "l-$d", userId = userId, habitId = "h1",
                quantity = 1.0,
                loggedAt = LocalDateTime(d, LocalTime(10, 0)).toInstant(tz),
            )
        }
        wantActivityRepo.activities.add(
            WantActivity(id = "a1", name = "a", unit = "u", costPerUnit = 5.0)
        )
        // Spend today: qty 1 × cost 5 × rate 1.2 = ceil(6.0) = 6
        wantLogRepo.insertLog(
            id = "w-today", userId = userId, activityId = "a1", quantity = 1.0,
            deviceMode = DeviceMode.OTHER,
            loggedAt = LocalDateTime(today, LocalTime(11, 0)).toInstant(tz),
        )

        val streak = ComputeStreakUseCase(habitLogRepo, habitRepo, tz, clock)
        val streakOnDay = GetUserStreakOnDayUseCase(streak, tz)
        val sut = GetDayPointsUseCase(
            habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo, tz, streakOnDay,
        )

        val day = sut.execute(userId, today).getOrThrow()
        assertEquals(6, day.spent)
        assertEquals(1, day.earned)  // earn unaffected by rate
    }

    @Test
    fun `earn is NOT rate-multiplied at non-trivial earn quantity`() = runTest {
        val today = LocalDate(2026, 5, 30)
        val now = LocalDateTime(today, LocalTime(20, 0)).toInstant(tz)
        val clock = object : Clock { override fun now(): Instant = now }
        val habitRepo = FakeHabitRepository()
        val habitLogRepo = FakeHabitLogRepository()
        val wantActivityRepo = FakeWantActivityRepository()
        val wantLogRepo = FakeWantLogRepository()

        val anchor = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz)
        habitRepo.saveHabit(
            Habit(
                id = "h1", userId = userId, templateId = null, name = "h", unit = "x",
                thresholdPerPoint = 1.0, dailyTarget = 100,
                createdAt = anchor, updatedAt = anchor, effectiveFrom = anchor,
            )
        )
        // 14 days of qty=3 logs each day to keep the day "complete" → streak=14 → rate 1.2
        (0..13).forEach { offset ->
            val d = today.minus(offset, DateTimeUnit.DAY)
            habitLogRepo.insertLog(
                id = "l-$d", userId = userId, habitId = "h1",
                quantity = 3.0,
                loggedAt = LocalDateTime(d, LocalTime(10, 0)).toInstant(tz),
            )
        }

        val streak = ComputeStreakUseCase(habitLogRepo, habitRepo, tz, clock)
        val streakOnDay = GetUserStreakOnDayUseCase(streak, tz)
        val sut = GetDayPointsUseCase(
            habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo, tz, streakOnDay,
        )

        // Without rate-multiplication: pointsEarned(3.0, 1.0) = 3.
        // If a regression rate-multiplied earn: ceil(3.0 × 1.2) = 4. Catches that case.
        val day = sut.execute(userId, today).getOrThrow()
        assertEquals(3, day.earned)
        assertEquals(0, day.spent)
    }
}
