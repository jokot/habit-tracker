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
    private val useCase = LogWantUseCase(wantLogRepo, activityRepo, balance)
    private val userId = "user1"

    /** Earns `pts` points via a dummy habit log so spending can be tested. */
    private suspend fun giveBalance(pts: Int) {
        habitRepo.saveHabit(Habit("h1", userId, "tpl", "Earn", "units", 1.0, pts, Clock.System.now(), Clock.System.now()))
        habitLogRepo.insertLog("hl1", userId, "h1", pts.toDouble(), Clock.System.now())
    }

    @Test
    fun `returns correct points spent for youtube`() = runTest {
        giveBalance(5)
        activityRepo.activities.add(WantActivity("a1", "YouTube long-form", "minutes", 0.1))
        val result = useCase.execute(userId, "a1", 30.0, DeviceMode.OTHER).getOrThrow()
        assertEquals(3, result.pointsSpent)
    }

    @Test
    fun `records device mode correctly`() = runTest {
        giveBalance(10)
        activityRepo.activities.add(WantActivity("a1", "Scroll", "minutes", 1.0))
        val result = useCase.execute(userId, "a1", 10.0, DeviceMode.THIS_DEVICE).getOrThrow()
        assertEquals(DeviceMode.THIS_DEVICE, result.log.deviceMode)
    }

    @Test
    fun `fails for unknown activity`() = runTest {
        val result = useCase.execute(userId, "unknown", 10.0, DeviceMode.OTHER)
        assertTrue(result.isFailure)
    }

    @Test
    fun `partial-point spend rounds up to 1 pt`() = runTest {
        giveBalance(5)
        activityRepo.activities.add(WantActivity("a1", "YouTube long-form", "minutes", 0.1))
        // 5 min × 0.1 = 0.5 pts → ceil = 1 pt (any positive consumption costs at least 1).
        val result = useCase.execute(userId, "a1", 5.0, DeviceMode.OTHER).getOrThrow()
        assertEquals(1, result.pointsSpent)
    }

    @Test
    fun `twitter 1 minute costs 1 pt via ceil`() = runTest {
        giveBalance(5)
        // Cost 0.5 pt/min (1 pt per 2 min).
        activityRepo.activities.add(WantActivity("a1", "Twitter", "minutes", 0.5))
        val result = useCase.execute(userId, "a1", 1.0, DeviceMode.OTHER).getOrThrow()
        assertEquals(1, result.pointsSpent)
    }

    @Test
    fun `blocks log when balance insufficient`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "Scroll", "minutes", 1.0))
        // Balance 0, trying to spend 10. Reject.
        val result = useCase.execute(userId, "a1", 10.0, DeviceMode.OTHER)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InsufficientPointsException)
        // And no log was written.
        assertEquals(0, wantLogRepo.logs.size)
    }

    @Test
    fun `allows spend exactly equal to balance`() = runTest {
        giveBalance(3)
        activityRepo.activities.add(WantActivity("a1", "YouTube", "minutes", 0.1))
        // 30 × 0.1 = 3 pts, balance 3 → allowed.
        val result = useCase.execute(userId, "a1", 30.0, DeviceMode.OTHER).getOrThrow()
        assertEquals(3, result.pointsSpent)
    }
}
