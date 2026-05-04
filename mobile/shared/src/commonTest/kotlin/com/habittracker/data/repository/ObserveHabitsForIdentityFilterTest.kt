package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveHabitsForIdentityFilterTest {
    @Test
    fun `unlinked habit (effectiveTo set) is excluded from observeHabitsForIdentity`() = runTest {
        val repo = FakeIdentityRepository()
        val h1 = makeHabit("h1", "u1")
        val h2 = makeHabit("h2", "u1")
        repo.seedHabit(h1)
        repo.seedHabit(h2)
        repo.linkHabitToIdentities("h1", setOf("identityX"))
        repo.linkHabitToIdentities("h2", setOf("identityX"))

        // Unlink h2
        repo.markHabitIdentityRemoved("h2", "identityX", Instant.fromEpochSeconds(500))

        val active = repo.observeHabitsForIdentity("u1", "identityX").first()
        assertEquals(listOf("h1"), active.map { it.id })
    }

    private fun makeHabit(id: String, userId: String) = Habit(
        id = id, userId = userId, templateId = null, name = id, unit = "x",
        thresholdPerPoint = 1.0, dailyTarget = 1,
        createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
    )
}
