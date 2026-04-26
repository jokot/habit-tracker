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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class GetPointBalanceUseCaseTest {
    private val habitLogRepo = FakeHabitLogRepository()
    private val wantLogRepo = FakeWantLogRepository()
    private val habitRepo = FakeHabitRepository()
    private val activityRepo = FakeWantActivityRepository()
    private val useCase = GetPointBalanceUseCase(habitLogRepo, wantLogRepo, habitRepo, activityRepo)
    private val userId = "user1"

    private fun weekStart() = useCase.currentWeekStart()

    @Test
    fun `balance is zero with no logs`() = runTest {
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(0, result.earned)
        assertEquals(0, result.spent)
        assertEquals(0, result.balance)
    }

    @Test
    fun `computes earned from habit logs this week`() = runTest {
        habitRepo.saveHabit(Habit("h1", userId, "tpl", "Read", "pages", 3.0, 3, Clock.System.now(), Clock.System.now()))
        habitLogRepo.insertLog("l1", userId, "h1", 9.0, Clock.System.now())
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(3, result.earned)
    }

    @Test
    fun `computes spent from want logs this week`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "YouTube", "minutes", 0.1))
        wantLogRepo.insertLog("l1", userId, "a1", 30.0, DeviceMode.OTHER, Clock.System.now())
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(3, result.spent)
    }

    @Test
    fun `balance is earned minus spent`() = runTest {
        habitRepo.saveHabit(Habit("h1", userId, "tpl", "Read", "pages", 3.0, 3, Clock.System.now(), Clock.System.now()))
        activityRepo.activities.add(WantActivity("a1", "YouTube", "minutes", 0.1))
        habitLogRepo.insertLog("l1", userId, "h1", 9.0, Clock.System.now())
        wantLogRepo.insertLog("l2", userId, "a1", 30.0, DeviceMode.OTHER, Clock.System.now())
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(3, result.earned)
        assertEquals(3, result.spent)
        assertEquals(0, result.balance)
    }

    @Test
    fun `balance clamps at zero even if raw spent exceeds earned`() = runTest {
        // Direct write (bypasses LogWantUseCase lock) to verify display clamp.
        activityRepo.activities.add(WantActivity("a1", "Scroll", "minutes", 1.0))
        wantLogRepo.insertLog("l1", userId, "a1", 10.0, DeviceMode.OTHER, Clock.System.now())
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(0, result.balance)
    }

    @Test
    fun `earned is capped at daily_target per habit per day`() = runTest {
        // Habit: 3 pages = 1 pt, daily target 3 (max 3 pts/day).
        habitRepo.saveHabit(Habit("h1", userId, "tpl", "Read", "pages", 3.0, 3, Clock.System.now(), Clock.System.now()))
        // Log 30 pages → raw 10 pts, capped to 3.
        habitLogRepo.insertLog("l1", userId, "h1", 30.0, Clock.System.now())
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(3, result.earned)
    }

    @Test
    fun `excludes logs from before this week`() = runTest {
        habitRepo.saveHabit(Habit("h1", userId, "tpl", "Read", "pages", 3.0, 3, Clock.System.now(), Clock.System.now()))
        val lastWeek = weekStart() - 1.days
        habitLogRepo.insertLog("l1", userId, "h1", 3.0, lastWeek)
        habitLogRepo.insertLog("l2", userId, "h1", 3.0, Clock.System.now())
        val result = useCase.execute(userId).getOrThrow()
        assertEquals(1, result.earned)
    }
}
