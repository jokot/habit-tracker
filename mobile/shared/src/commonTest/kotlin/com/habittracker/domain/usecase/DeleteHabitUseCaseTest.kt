package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeleteHabitUseCaseTest {
    @Test
    fun `delete sets effectiveTo to clock now`() = runTest {
        val now = Instant.fromEpochSeconds(2000)
        val clock = object : Clock { override fun now(): Instant = now }
        val habits = FakeHabitRepository()
        habits.saveHabit(
            Habit(
                id = "h1", userId = "u1", templateId = null, name = "n",
                unit = "u", thresholdPerPoint = 1.0, dailyTarget = 1,
                createdAt = Instant.fromEpochSeconds(0),
                updatedAt = Instant.fromEpochSeconds(0),
            )
        )
        val sut = DeleteHabitUseCase(habits, clock)

        sut.execute("u1", "h1")

        val out = habits.getHabitsForUser("u1").single()
        assertEquals(now, out.effectiveTo)
        assertNull(out.syncedAt)
    }
}
