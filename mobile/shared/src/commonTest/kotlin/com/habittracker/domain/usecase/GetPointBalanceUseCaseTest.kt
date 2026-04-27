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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Fixed clock anchored to Wednesday 2025-01-08 12:00 UTC (mid-week).
 * Week start (Monday) = 2025-01-06 00:00 UTC.
 */
private val FIXED_NOW: Instant = Instant.parse("2025-01-08T12:00:00Z")
private val WEEK_START_DATE = LocalDate(2025, 1, 6)

private fun makeClock(now: Instant): Clock = object : Clock {
    override fun now(): Instant = now
}

class GetPointBalanceUseCaseTest {
    private val habitLogRepo = FakeHabitLogRepository()
    private val wantLogRepo = FakeWantLogRepository()
    private val habitRepo = FakeHabitRepository()
    private val activityRepo = FakeWantActivityRepository()
    private val userId = "user1"

    /** Build a use case with a fixed clock and UTC timezone for predictable math. */
    private fun useCase(now: Instant = FIXED_NOW) = GetPointBalanceUseCase(
        habitLogRepo, wantLogRepo, habitRepo, activityRepo,
        timeZone = TimeZone.UTC,
        clock = makeClock(now),
    )

    /** Instant for HH:mm on the given date (UTC). */
    private fun at(date: LocalDate, hour: Int = 10, minute: Int = 0): Instant =
        date.atStartOfDayIn(TimeZone.UTC) + hour.hours + minute.minutes

    // ─── Baseline tests ──────────────────────────────────────────────────────

    @Test
    fun `balance is zero with no logs`() = runTest {
        val result = useCase().execute(userId).getOrThrow()
        assertEquals(0, result.earned)
        assertEquals(0, result.spent)
        assertEquals(0, result.balance)
    }

    @Test
    fun `computes earned from habit logs this week`() = runTest {
        // habit: 3 pages = 1 pt, dailyTarget 3
        habitRepo.saveHabit(habit("h1", threshold = 3.0, dailyTarget = 3))
        habitLogRepo.insertLog("l1", userId, "h1", 9.0, at(WEEK_START_DATE))
        val result = useCase().execute(userId).getOrThrow()
        assertEquals(3, result.earned)
    }

    @Test
    fun `computes spent from want logs this week`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "YouTube", "minutes", 0.1))
        wantLogRepo.insertLog("l1", userId, "a1", 30.0, DeviceMode.OTHER, at(WEEK_START_DATE))
        val result = useCase().execute(userId).getOrThrow()
        assertEquals(3, result.spent)
    }

    @Test
    fun `balance is earned minus spent`() = runTest {
        habitRepo.saveHabit(habit("h1", threshold = 3.0, dailyTarget = 3))
        activityRepo.activities.add(WantActivity("a1", "YouTube", "minutes", 0.1))
        habitLogRepo.insertLog("l1", userId, "h1", 9.0, at(WEEK_START_DATE))
        wantLogRepo.insertLog("l2", userId, "a1", 30.0, DeviceMode.OTHER, at(WEEK_START_DATE))
        val result = useCase().execute(userId).getOrThrow()
        assertEquals(3, result.earned)
        assertEquals(3, result.spent)
        assertEquals(0, result.balance)
    }

    @Test
    fun `balance clamps at zero even if raw spent exceeds earned`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "Scroll", "minutes", 1.0))
        wantLogRepo.insertLog("l1", userId, "a1", 10.0, DeviceMode.OTHER, at(WEEK_START_DATE))
        val result = useCase().execute(userId).getOrThrow()
        assertEquals(0, result.balance)
    }

    @Test
    fun `earned is capped at daily_target per habit per day`() = runTest {
        // Habit: 3 pages = 1 pt, daily target 3 (max 3 pts/day).
        habitRepo.saveHabit(habit("h1", threshold = 3.0, dailyTarget = 3))
        // Log 30 pages → raw 10 pts, capped to 3.
        habitLogRepo.insertLog("l1", userId, "h1", 30.0, at(WEEK_START_DATE))
        val result = useCase().execute(userId).getOrThrow()
        assertEquals(3, result.earned)
    }

    @Test
    fun `excludes logs from before this week`() = runTest {
        habitRepo.saveHabit(habit("h1", threshold = 3.0, dailyTarget = 3))
        val lastWeek = WEEK_START_DATE.plus(-1, DateTimeUnit.DAY)
        habitLogRepo.insertLog("l1", userId, "h1", 3.0, at(lastWeek))
        habitLogRepo.insertLog("l2", userId, "h1", 3.0, at(WEEK_START_DATE))
        val result = useCase().execute(userId).getOrThrow()
        assertEquals(1, result.earned)
    }

    // ─── Rollover cap tests ───────────────────────────────────────────────────

    @Test
    fun `rollover cap kept when carry is under cap`() = runTest {
        // dailyTarget=3 → cap=6. Day 1 earns 3, day 2 earns 3.
        // Day 1 end balance = 3 (under cap=6, no clip). Day 2: 3 + 3 = 6.
        habitRepo.saveHabit(habit("h1", threshold = 1.0, dailyTarget = 3))
        val day1 = WEEK_START_DATE                          // Monday
        val day2 = day1.plus(1, DateTimeUnit.DAY)           // Tuesday
        // Clock is day2 noon so both days are walked.
        val clock = makeClock(at(day2, hour = 12))
        habitLogRepo.insertLog("l1", userId, "h1", 3.0, at(day1))
        habitLogRepo.insertLog("l2", userId, "h1", 3.0, at(day2))
        val result = GetPointBalanceUseCase(
            habitLogRepo, wantLogRepo, habitRepo, activityRepo,
            timeZone = TimeZone.UTC, clock = clock,
        ).execute(userId).getOrThrow()
        assertEquals(6, result.totalEarned())
        assertEquals(6, result.balance)
    }

    @Test
    fun `rollover cap clips when carry exceeds cap`() = runTest {
        // dailyTarget=3 → rolloverCap=6. Earn 3 pts every day, no spending.
        // Day1: balance=3. Day2 midnight clip: min(3,6)=3 → 3+3=6. Day3 midnight: min(6,6)=6 → 6+3=9.
        // Day4 midnight: min(9,6)=6 → 6+3=9. Day5 midnight: min(9,6)=6 → 6+3=9. (saturated)
        habitRepo.saveHabit(habit("h1", threshold = 1.0, dailyTarget = 3))
        val day1 = WEEK_START_DATE
        for (i in 0..4) {
            val d = day1.plus(i, DateTimeUnit.DAY)
            habitLogRepo.insertLog("l$i", userId, "h1", 3.0, at(d))
        }
        // Clock is end of day5.
        val day5 = day1.plus(4, DateTimeUnit.DAY)
        val clock = makeClock(at(day5, hour = 23))
        val result = GetPointBalanceUseCase(
            habitLogRepo, wantLogRepo, habitRepo, activityRepo,
            timeZone = TimeZone.UTC, clock = clock,
        ).execute(userId).getOrThrow()
        // After day 3 balance saturates at 9 (cap=6 → +3 = 9).
        assertEquals(9, result.balance)
    }

    @Test
    fun `spent before midnight reduces carry into next day`() = runTest {
        // dailyTarget=3 → rolloverCap=6.
        // Day1: earn 3, spend 2 → end balance=1.
        // Day2 midnight: min(1,6)=1 → 1+3=4.
        habitRepo.saveHabit(habit("h1", threshold = 1.0, dailyTarget = 3))
        activityRepo.activities.add(WantActivity("a1", "Activity", "units", 1.0))
        val day1 = WEEK_START_DATE
        val day2 = day1.plus(1, DateTimeUnit.DAY)
        habitLogRepo.insertLog("l1", userId, "h1", 3.0, at(day1))
        wantLogRepo.insertLog("w1", userId, "a1", 2.0, DeviceMode.OTHER, at(day1, hour = 20))
        habitLogRepo.insertLog("l2", userId, "h1", 3.0, at(day2))
        val clock = makeClock(at(day2, hour = 12))
        val result = GetPointBalanceUseCase(
            habitLogRepo, wantLogRepo, habitRepo, activityRepo,
            timeZone = TimeZone.UTC, clock = clock,
        ).execute(userId).getOrThrow()
        assertEquals(4, result.balance)
    }

    @Test
    fun `balance never goes negative`() = runTest {
        // Spend more than earned in a single day.
        habitRepo.saveHabit(habit("h1", threshold = 1.0, dailyTarget = 3))
        activityRepo.activities.add(WantActivity("a1", "Activity", "units", 1.0))
        val day1 = WEEK_START_DATE
        habitLogRepo.insertLog("l1", userId, "h1", 3.0, at(day1))
        wantLogRepo.insertLog("w1", userId, "a1", 10.0, DeviceMode.OTHER, at(day1, hour = 20))
        val result = useCase().execute(userId).getOrThrow()
        assertEquals(0, result.balance)
    }

    @Test
    fun `Monday week reset wipes carry from previous week`() = runTest {
        // Clock on Monday (week-start day). Only logs from Monday onward count.
        habitRepo.saveHabit(habit("h1", threshold = 1.0, dailyTarget = 3))
        val monday = WEEK_START_DATE
        val lastSunday = monday.plus(-1, DateTimeUnit.DAY)
        // Log from last Sunday — must be excluded.
        habitLogRepo.insertLog("l1", userId, "h1", 3.0, at(lastSunday))
        // Log from Monday — included.
        habitLogRepo.insertLog("l2", userId, "h1", 3.0, at(monday))
        // Clock = Monday noon.
        val result = useCase(now = at(monday, hour = 12)).execute(userId).getOrThrow()
        assertEquals(3, result.earned)
        assertEquals(3, result.balance)
    }

    @Test
    fun `user with no habits has zero earn cap and zero balance`() = runTest {
        // No habits → dailyEarnCap=0, rolloverCap=0. Even if logs existed, nothing earned.
        val result = useCase().execute(userId).getOrThrow()
        assertEquals(0, result.earned)
        assertEquals(0, result.balance)
    }

    // ─── currentWeekStart / currentWeekStartDate helpers ─────────────────────

    @Test
    fun `currentWeekStart returns local Monday midnight`() {
        val uc = useCase(now = FIXED_NOW) // Wednesday 2025-01-08
        val weekStart = uc.currentWeekStartDate()
        assertEquals(LocalDate(2025, 1, 6), weekStart) // Monday
    }

    @Test
    fun `currentWeekStart on Monday returns same day`() {
        val monday = at(WEEK_START_DATE, hour = 9) // 2025-01-06 09:00
        val uc = useCase(now = monday)
        assertEquals(WEEK_START_DATE, uc.currentWeekStartDate())
    }

    @Test
    fun `currentWeekStart on Sunday returns prior Monday`() {
        val sunday = at(WEEK_START_DATE.plus(6, DateTimeUnit.DAY), hour = 23) // 2025-01-12
        val uc = useCase(now = sunday)
        assertEquals(WEEK_START_DATE, uc.currentWeekStartDate())
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun habit(id: String, threshold: Double, dailyTarget: Int): Habit {
        val now = FIXED_NOW
        return Habit(id, userId, "tpl", id, "units", threshold, dailyTarget, now, now)
    }

    /** Convenience: total earned across the week (ignores rollover semantics). */
    private fun com.habittracker.domain.model.PointBalance.totalEarned() = earned
}
