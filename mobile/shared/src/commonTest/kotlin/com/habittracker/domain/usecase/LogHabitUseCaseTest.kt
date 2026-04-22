package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogHabitUseCaseTest {
    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()
    private val useCase = LogHabitUseCase(habitLogRepo, habitRepo)
    private val userId = "user1"

    private fun makeHabit(id: String, threshold: Double) = Habit(
        id = id, userId = userId, templateId = "tpl", name = "Read",
        unit = "pages", thresholdPerPoint = threshold, dailyTarget = 3,
        createdAt = Clock.System.now(),
    )

    @Test
    fun `returns correct points earned`() = runTest {
        habitRepo.saveHabit(makeHabit("h1", 3.0))
        val result = useCase.execute(userId, "h1", 9.0).getOrThrow()
        assertEquals(3, result.pointsEarned)
        assertEquals(9.0, result.log.quantity)
    }

    @Test
    fun `returns zero points below threshold`() = runTest {
        habitRepo.saveHabit(makeHabit("h1", 3.0))
        val result = useCase.execute(userId, "h1", 2.0).getOrThrow()
        assertEquals(0, result.pointsEarned)
    }

    @Test
    fun `fails for unknown habit`() = runTest {
        val result = useCase.execute(userId, "unknown", 5.0)
        assertTrue(result.isFailure)
    }

    @Test
    fun `log is stored in repository`() = runTest {
        habitRepo.saveHabit(makeHabit("h1", 3.0))
        useCase.execute(userId, "h1", 6.0).getOrThrow()
        assertEquals(1, habitLogRepo.logs.size)
        assertEquals("h1", habitLogRepo.logs.first().habitId)
    }

    @Test
    fun `zero points when daily target already reached`() = runTest {
        // Daily target 3, threshold 3.
        habitRepo.saveHabit(makeHabit("h1", 3.0))
        // First log: 9 pages = 3 pts, fills the cap.
        useCase.execute(userId, "h1", 9.0).getOrThrow()
        // Second log: 6 more pages = raw 2 pts but capped → 0.
        val result = useCase.execute(userId, "h1", 6.0).getOrThrow()
        assertEquals(0, result.pointsEarned)
    }

    @Test
    fun `points capped at remaining capacity`() = runTest {
        // Daily target 3, threshold 3.
        habitRepo.saveHabit(makeHabit("h1", 3.0))
        // First log: 6 pages = 2 pts, room for 1 more.
        useCase.execute(userId, "h1", 6.0).getOrThrow()
        // Second log: 9 pages = raw 3 pts but only 1 slot left → 1.
        val result = useCase.execute(userId, "h1", 9.0).getOrThrow()
        assertEquals(1, result.pointsEarned)
    }
}
