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
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 7 pivot: GetPointBalance sums [WantLog.pointsSpent] stamped at write time.
 * The read path no longer recomputes from cost or applies a rate — it just sums the
 * persisted per-log point cost across the week. Habit earning remains base-rate.
 */
class GetPointBalanceUseCaseRateTest {
    private val tz = TimeZone.UTC
    private val userId = "u1"

    @Test
    fun `weekly spent sums pointsSpent across want logs in the week`() = runTest {
        // Week start Mon 2026-04-27. Today = Sun 2026-05-03 (7 days).
        // Two want logs: Mon stamped pointsSpent=5, Sun stamped pointsSpent=6. Total = 11.
        val monday = LocalDate(2026, 4, 27)
        val sunday = LocalDate(2026, 5, 3)
        val now = LocalDateTime(sunday, LocalTime(20, 0)).toInstant(tz)
        val clock = object : Clock { override fun now(): Instant = now }

        val habitRepo = FakeHabitRepository()
        val habitLogRepo = FakeHabitLogRepository()
        val wantActivityRepo = FakeWantActivityRepository()
        val wantLogRepo = FakeWantLogRepository()

        // Streak habit kept so the balance has earn coverage across the week.
        val streakHabit = makeHabit(
            id = "streakH",
            dailyTarget = 1,
            effectiveFromInstant = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz),
        )
        habitRepo.saveHabit(streakHabit)
        for (offset in 0..6) {
            val day = monday.plus(offset, DateTimeUnit.DAY)
            habitLogRepo.insertLog(
                id = "streak-$day",
                userId = userId,
                habitId = "streakH",
                quantity = 1.0,
                loggedAt = LocalDateTime(day, LocalTime(8, 0)).toInstant(tz),
            )
        }

        wantActivityRepo.activities.add(
            WantActivity(id = "a1", name = "Scroll", unit = "unit", unitsPerPoint = 1),
        )
        wantLogRepo.insertLog(
            id = "w-mon",
            userId = userId,
            activityId = "a1",
            quantity = 5.0,
            pointsSpent = 5,
            deviceMode = DeviceMode.OTHER,
            loggedAt = LocalDateTime(monday, LocalTime(12, 0)).toInstant(tz),
        )
        wantLogRepo.insertLog(
            id = "w-sun",
            userId = userId,
            activityId = "a1",
            quantity = 6.0,
            pointsSpent = 6,
            deviceMode = DeviceMode.OTHER,
            loggedAt = LocalDateTime(sunday, LocalTime(12, 0)).toInstant(tz),
        )

        val streak = ComputeStreakUseCase(habitLogRepo, habitRepo, tz, clock)
        val streakOnDay = GetUserStreakOnDayUseCase(streak, tz)
        val balance = GetPointBalanceUseCase(
            habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo,
            timeZone = tz,
            clock = clock,
            getUserStreakOnDayUseCase = streakOnDay,
        )

        val result = balance.execute(userId).getOrThrow()
        assertEquals(11, result.spent)
        // Sun is "today"; spentToday is just the Sun stamped pointsSpent = 6.
        assertEquals(6, result.spentToday)
    }

    @Test
    fun `habit earning is NOT rate-multiplied`() = runTest {
        // Today = Sun 2026-05-03. 14 consecutive complete days ending today → Phase 7 rate 1.4 if applied.
        // Earned today must be base value (3 from 3.0 quantity at threshold 1.0), NOT 5 (3 * 1.4 ceiled).
        val today = LocalDate(2026, 5, 3)
        val now = LocalDateTime(today, LocalTime(20, 0)).toInstant(tz)
        val clock = object : Clock { override fun now(): Instant = now }

        val habitRepo = FakeHabitRepository()
        val habitLogRepo = FakeHabitLogRepository()
        val wantActivityRepo = FakeWantActivityRepository()
        val wantLogRepo = FakeWantLogRepository()

        // Habit threshold 1.0, dailyTarget 100 (so cap doesn't kick in), no spending.
        val habit = makeHabit(
            id = "h1",
            dailyTarget = 100,
            thresholdPerPoint = 1.0,
            effectiveFromInstant = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz),
        )
        habitRepo.saveHabit(habit)
        // Log every day for 14 days ending today (tier 3, Phase 7 rate 1.4 if it were ever applied).
        for (offset in 0..13) {
            val day = today.minus(offset, DateTimeUnit.DAY)
            habitLogRepo.insertLog(
                id = "log-$day",
                userId = userId,
                habitId = "h1",
                quantity = 3.0,
                loggedAt = LocalDateTime(day, LocalTime(10, 0)).toInstant(tz),
            )
        }

        val streak = ComputeStreakUseCase(habitLogRepo, habitRepo, tz, clock)
        val streakOnDay = GetUserStreakOnDayUseCase(streak, tz)
        val balance = GetPointBalanceUseCase(
            habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo,
            timeZone = tz,
            clock = clock,
            getUserStreakOnDayUseCase = streakOnDay,
        )

        val result = balance.execute(userId).getOrThrow()
        // earnedToday MUST be 3 (3 quantity / 1 threshold = 3 pts), NOT 5 (3 * 1.4 = 4.2 ceil).
        assertEquals(3, result.earnedToday)
    }

    // ── helpers ─────────────────────────────────────────────────────

    private fun makeHabit(
        id: String,
        dailyTarget: Int = 1,
        thresholdPerPoint: Double = 1.0,
        effectiveFromInstant: Instant,
    ): Habit = Habit(
        id = id,
        userId = userId,
        templateId = null,
        name = id,
        unit = "x",
        thresholdPerPoint = thresholdPerPoint,
        dailyTarget = dailyTarget,
        createdAt = effectiveFromInstant,
        updatedAt = effectiveFromInstant,
        effectiveFrom = effectiveFromInstant,
    )
}
