package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HabitRepositoryNullableTemplateTest {
    @Test
    fun `custom habit with null templateId roundtrips through fake repo`() = runTest {
        val repo = FakeHabitRepository()
        val custom = Habit(
            id = "h1",
            userId = "u1",
            templateId = null,
            name = "Walk outside",
            unit = "min",
            thresholdPerPoint = 15.0,
            dailyTarget = 2,
            createdAt = Instant.fromEpochSeconds(0),
            updatedAt = Instant.fromEpochSeconds(0),
        )
        repo.saveHabit(custom)
        val out = repo.getHabitsForUser("u1").single()
        assertNull(out.templateId)
        assertEquals("Walk outside", out.name)
    }
}
