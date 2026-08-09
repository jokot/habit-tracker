package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

private val TODAY = LocalDate(2026, 4, 22)
private val YESTERDAY = LocalDate(2026, 4, 21)

private fun makeClock(now: Instant): Clock = object : Clock {
    override fun now(): Instant = now
}

private fun at(date: LocalDate, hour: Int = 10): Instant =
    date.atStartOfDayIn(TimeZone.UTC) + hour.hours

class GetTodayHabitsUseCaseTest {
    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()
    private val userId = "user1"

    private fun useCase() = GetTodayHabitsUseCase(
        habitRepo, habitLogRepo,
        timeZone = TimeZone.UTC,
        clock = makeClock(at(TODAY, hour = 12)),
    )

    private fun habit(
        id: String,
        threshold: Double = 1.0,
        dailyTarget: Int = 3,
        effectiveTo: Instant? = null,
    ): Habit = Habit(
        id = id, userId = userId, templateId = "tpl", name = id, unit = "units",
        thresholdPerPoint = threshold, dailyTarget = dailyTarget,
        createdAt = at(TODAY), updatedAt = at(TODAY), effectiveTo = effectiveTo,
    )

    @Test
    fun `empty when user has no habits`() = runTest {
        assertEquals(emptyList(), useCase().execute(userId))
    }

    @Test
    fun `pointsToday sums only today's logs, excludes yesterday`() = runTest {
        habitRepo.saveHabit(habit("h1", threshold = 1.0, dailyTarget = 5))
        habitLogRepo.insertLog("l0", userId, "h1", 2.0, at(YESTERDAY))
        habitLogRepo.insertLog("l1", userId, "h1", 3.0, at(TODAY, hour = 9))
        val result = useCase().execute(userId)
        assertEquals(1, result.size)
        assertEquals(3, result.single().pointsToday)
    }

    @Test
    fun `excludes soft-deleted habits`() = runTest {
        habitRepo.saveHabit(habit("h1", effectiveTo = at(TODAY, hour = 1)))
        habitRepo.saveHabit(habit("h2"))
        val result = useCase().execute(userId)
        assertEquals(listOf("h2"), result.map { it.habit.id })
    }

    @Test
    fun `isGoalMet reflects dailyTarget comparison`() = runTest {
        habitRepo.saveHabit(habit("h1", threshold = 1.0, dailyTarget = 3))
        habitLogRepo.insertLog("l1", userId, "h1", 3.0, at(TODAY, hour = 9))
        val result = useCase().execute(userId)
        assertTrue(result.single().isGoalMet)
    }
}
