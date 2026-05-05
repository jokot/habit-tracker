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

class LogWantUseCaseRateTest {
    private val tz = TimeZone.UTC
    private val userId = "u1"

    @Test
    fun `streak 0 applies rate 1_0`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val sut = makeSut(today, completeDaysEndingToday = 0)
        seedActivity(sut.wantActivityRepo, "a1", costPerUnit = 1.0)
        seedEarn(sut.habitLogRepo, sut.habitRepo, today, points = 100)
        val result = sut.useCase.execute(userId, "a1", quantity = 10.0, deviceMode = DeviceMode.THIS_DEVICE).getOrThrow()
        assertEquals(10, result.pointsSpent)
    }

    @Test
    fun `streak 14 applies rate 1_2`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val sut = makeSut(today, completeDaysEndingToday = 14)
        seedActivity(sut.wantActivityRepo, "a1", costPerUnit = 1.0)
        seedEarn(sut.habitLogRepo, sut.habitRepo, today, points = 100)
        val result = sut.useCase.execute(userId, "a1", quantity = 10.0, deviceMode = DeviceMode.THIS_DEVICE).getOrThrow()
        assertEquals(12, result.pointsSpent)
    }

    @Test
    fun `streak 30 applies rate 1_4`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val sut = makeSut(today, completeDaysEndingToday = 30)
        seedActivity(sut.wantActivityRepo, "a1", costPerUnit = 1.0)
        seedEarn(sut.habitLogRepo, sut.habitRepo, today, points = 100)
        val result = sut.useCase.execute(userId, "a1", quantity = 10.0, deviceMode = DeviceMode.THIS_DEVICE).getOrThrow()
        assertEquals(14, result.pointsSpent)
    }

    // ── helpers ─────────────────────────────────────────────────────

    private data class Sut(
        val useCase: LogWantUseCase,
        val habitRepo: FakeHabitRepository,
        val habitLogRepo: FakeHabitLogRepository,
        val wantActivityRepo: FakeWantActivityRepository,
        val wantLogRepo: FakeWantLogRepository,
    )

    /**
     * Build a Sut where the user has `completeDaysEndingToday` consecutive COMPLETE days
     * ending on `today`. A single habit with dailyTarget=1 + thresholdPerPoint=1.0 is used,
     * with one log per day on the streak window.
     */
    private suspend fun makeSut(today: LocalDate, completeDaysEndingToday: Int): Sut {
        val habitRepo = FakeHabitRepository()
        val habitLogRepo = FakeHabitLogRepository()
        val wantActivityRepo = FakeWantActivityRepository()
        val wantLogRepo = FakeWantLogRepository()

        val now = LocalDateTime(today, LocalTime(12, 0)).toInstant(tz)
        val clock = object : Clock { override fun now(): Instant = now }

        if (completeDaysEndingToday > 0) {
            val streakHabit = makeHabit("streakH")
            habitRepo.saveHabit(streakHabit)
            for (offset in 0 until completeDaysEndingToday) {
                val day = today.minus(offset, DateTimeUnit.DAY)
                habitLogRepo.insertLog(
                    id = "streak-$day",
                    userId = userId,
                    habitId = "streakH",
                    quantity = 1.0,
                    loggedAt = LocalDateTime(day, LocalTime(10, 0)).toInstant(tz),
                )
            }
        }

        val balance = GetPointBalanceUseCase(habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo, tz, clock)
        val streak = ComputeStreakUseCase(habitLogRepo, habitRepo, tz, clock)
        val streakOnDay = GetUserStreakOnDayUseCase(streak, tz)
        val useCase = LogWantUseCase(
            wantLogRepository = wantLogRepo,
            wantActivityRepository = wantActivityRepo,
            getPointBalanceUseCase = balance,
            getUserStreakOnDayUseCase = streakOnDay,
            clock = clock,
            timeZone = tz,
        )
        return Sut(useCase, habitRepo, habitLogRepo, wantActivityRepo, wantLogRepo)
    }

    private fun seedActivity(repo: FakeWantActivityRepository, id: String, costPerUnit: Double) {
        repo.activities.add(WantActivity(id = id, name = id, unit = "x", costPerUnit = costPerUnit))
    }

    /**
     * Adds a generous habit + log on `today` so balance has at least `points` available.
     * effectiveFrom is set to today's start so the habit doesn't extend retroactively into
     * the streak window — otherwise its missing logs on past days would break the streak.
     */
    private suspend fun seedEarn(
        habitLogRepo: FakeHabitLogRepository,
        habitRepo: FakeHabitRepository,
        today: LocalDate,
        points: Int,
    ) {
        val todayStart = LocalDateTime(today, LocalTime(0, 0)).toInstant(tz)
        val habit = makeHabit(id = "earnH", dailyTarget = points).copy(effectiveFrom = todayStart)
        habitRepo.saveHabit(habit)
        habitLogRepo.insertLog(
            id = "earn-log",
            userId = userId,
            habitId = "earnH",
            quantity = points.toDouble(),
            loggedAt = LocalDateTime(today, LocalTime(11, 0)).toInstant(tz),
        )
    }

    private fun makeHabit(id: String, dailyTarget: Int = 1): Habit = Habit(
        id = id,
        userId = userId,
        templateId = null,
        name = id,
        unit = "x",
        thresholdPerPoint = 1.0,
        dailyTarget = dailyTarget,
        createdAt = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz),
        updatedAt = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz),
        effectiveFrom = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz),
    )
}
