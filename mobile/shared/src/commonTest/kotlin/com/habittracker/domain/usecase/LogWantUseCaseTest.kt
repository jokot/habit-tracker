package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeWantActivityRepository
import com.habittracker.data.repository.FakeWantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogWantUseCaseTest {
    private val activityRepo = FakeWantActivityRepository()
    private val wantLogRepo = FakeWantLogRepository()
    private val useCase = LogWantUseCase(wantLogRepo, activityRepo)
    private val userId = "user1"

    @Test
    fun `returns correct points spent for youtube`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "YouTube long-form", "minutes", 0.1))
        val result = useCase.execute(userId, "a1", 30.0, DeviceMode.OTHER).getOrThrow()
        assertEquals(3, result.pointsSpent)
    }

    @Test
    fun `records device mode correctly`() = runTest {
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
    fun `zero points for quantity below cost threshold`() = runTest {
        activityRepo.activities.add(WantActivity("a1", "YouTube long-form", "minutes", 0.1))
        val result = useCase.execute(userId, "a1", 5.0, DeviceMode.OTHER).getOrThrow()
        assertEquals(0, result.pointsSpent)
    }
}
