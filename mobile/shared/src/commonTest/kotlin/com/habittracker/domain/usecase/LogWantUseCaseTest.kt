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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogWantUseCaseTest {
    private val activityRepo = FakeWantActivityRepository()
    private val wantLogRepo = FakeWantLogRepository()
    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()
    private val balance = GetPointBalanceUseCase(habitLogRepo, wantLogRepo, habitRepo, activityRepo)
    private val streak = ComputeStreakUseCase(habitLogRepo, habitRepo)
    private val streakOnDay = GetUserStreakOnDayUseCase(streak)
    private val useCase = LogWantUseCase(wantLogRepo, activityRepo, balance, streakOnDay)
    private val userId = "user1"

    /** Earns `pts` points via a dummy habit log so spending can be tested. */
    private suspend fun giveBalance(pts: Int) {
        habitRepo.saveHabit(Habit("h1", userId, "tpl", "Earn", "units", 1.0, pts, Clock.System.now(), Clock.System.now()))
        habitLogRepo.insertLog("hl1", userId, "h1", pts.toDouble(), Clock.System.now())
    }

    @Test
    fun `returns correct points spent for youtube`() = runTest {
        giveBalance(5)
        // Old: 0.1 pt/min × 30 min = 3 pts. New: unitsPerPoint=10, taps=3 → 3 pts.
        activityRepo.activities.add(WantActivity("a1", "YouTube long-form", "minutes", 10))
        val result = useCase.execute(userId, "a1", taps = 3, deviceMode = DeviceMode.OTHER).getOrThrow()
        assertEquals(3, result.pointsSpent)
    }

    @Test
    fun `records device mode correctly`() = runTest {
        giveBalance(10)
        activityRepo.activities.add(WantActivity("a1", "Scroll", "minutes", 1))
        val result = useCase.execute(userId, "a1", taps = 10, deviceMode = DeviceMode.THIS_DEVICE).getOrThrow()
        assertEquals(DeviceMode.THIS_DEVICE, result.log.deviceMode)
    }

    @Test
    fun `fails for unknown activity`() = runTest {
        val result = useCase.execute(userId, "unknown", taps = 1, deviceMode = DeviceMode.OTHER)
        assertTrue(result.isFailure)
    }

    // Dropped: `partial-point spend rounds up to 1 pt` — under the new unitsPerPoint
    // model, points are stamped per tap (1 pt per tap), so the decimal-cost ceiling
    // semantics no longer exist. This is the bug being fixed.

    // Dropped: `twitter 1 minute costs 1 pt via ceil` — same reason as above.

    @Test
    fun `blocks log when balance insufficient`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "Scroll", "minutes", 1))
        // Balance 0, trying to spend 10 pts (taps=10). Reject.
        val result = useCase.execute(userId, "a1", taps = 10, deviceMode = DeviceMode.OTHER)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InsufficientPointsException)
        // And no log was written.
        assertEquals(0, wantLogRepo.logs.size)
    }

    @Test
    fun `allows spend exactly equal to balance`() = runTest {
        giveBalance(3)
        // unitsPerPoint=10, taps=3 → 3 pts; balance 3 → allowed.
        activityRepo.activities.add(WantActivity("a1", "YouTube", "minutes", 10))
        val result = useCase.execute(userId, "a1", taps = 3, deviceMode = DeviceMode.OTHER).getOrThrow()
        assertEquals(3, result.pointsSpent)
    }
}
