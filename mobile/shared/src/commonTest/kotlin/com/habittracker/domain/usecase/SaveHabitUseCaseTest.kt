package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFails

class SaveHabitUseCaseTest {
    private val now = Instant.fromEpochSeconds(1_000)
    private val fixedClock = object : Clock { override fun now(): Instant = now }

    @Test
    fun `create custom habit inserts with null templateId and links identities at now`() = runTest {
        val habits = FakeHabitRepository()
        val identities = FakeIdentityRepository(clock = fixedClock)
        val sut = SaveHabitUseCase(habits, identities, fixedClock)

        val newId = sut.create(
            userId = "u1",
            name = "Walk outside",
            unit = "min",
            threshold = 15.0,
            target = 2,
            identityIds = setOf("identityA", "identityB"),
            templateId = null,
        )

        val saved = habits.getHabitsForUser("u1").single()
        assertEquals(newId, saved.id)
        assertNull(saved.templateId)
        assertEquals("Walk outside", saved.name)
        assertEquals(now, saved.effectiveFrom)
        assertNull(saved.effectiveTo)
        val links = identities.getHabitIdentityLinksForUser("u1")
            .filter { it.habitId == newId }
        assertEquals(setOf("identityA", "identityB"), links.map { it.identityId }.toSet())
        links.forEach {
            assertEquals(now, it.effectiveFrom)
            assertNull(it.effectiveTo)
        }
    }

    @Test
    fun `update mutates fields and leaves links untouched when set unchanged`() = runTest {
        val habits = FakeHabitRepository()
        val identities = FakeIdentityRepository(clock = fixedClock)
        habits.saveHabit(seedHabit("h1", templateId = "tpl"))
        identities.linkHabitToIdentities("h1", setOf("ix"))

        val sut = SaveHabitUseCase(habits, identities, fixedClock)
        sut.update(
            userId = "u1",
            habitId = "h1",
            name = "Renamed",
            unit = "reps",
            threshold = 5.0,
            target = 3,
            newIdentityIds = setOf("ix"),
        )

        val out = habits.getHabitsForUser("u1").single()
        assertEquals("Renamed", out.name)
        assertEquals("reps", out.unit)
        assertEquals(5.0, out.thresholdPerPoint)
        assertEquals(3, out.dailyTarget)
        assertEquals(now, out.updatedAt)
        assertEquals("tpl", out.templateId)
    }

    @Test
    fun `update with link diff adds new and soft-removes old`() = runTest {
        val habits = FakeHabitRepository()
        val identities = FakeIdentityRepository(clock = fixedClock)
        habits.saveHabit(seedHabit("h1"))
        identities.linkHabitToIdentities("h1", setOf("ix"))

        val sut = SaveHabitUseCase(habits, identities, fixedClock)
        sut.update(
            userId = "u1",
            habitId = "h1",
            name = "n",
            unit = "u",
            threshold = 1.0,
            target = 1,
            newIdentityIds = setOf("iy"), // remove ix, add iy
        )

        val links = identities.getHabitIdentityLinksForUser("u1")
            .filter { it.habitId == "h1" }
        val ix = links.single { it.identityId == "ix" }
        val iy = links.single { it.identityId == "iy" }
        assertEquals(now, ix.effectiveTo)
        assertNull(iy.effectiveTo)
        assertEquals(now, iy.effectiveFrom)
    }

    @Test
    fun `update resumes a previously-removed identity by clearing effectiveTo`() = runTest {
        val habits = FakeHabitRepository()
        val identities = FakeIdentityRepository(clock = fixedClock)
        habits.saveHabit(seedHabit("h1"))
        identities.linkHabitToIdentities("h1", setOf("ix"))
        // then remove it
        identities.markHabitIdentityRemoved("h1", "ix", Instant.fromEpochSeconds(500))

        val sut = SaveHabitUseCase(habits, identities, fixedClock)
        sut.update(
            userId = "u1",
            habitId = "h1",
            name = "n",
            unit = "u",
            threshold = 1.0,
            target = 1,
            newIdentityIds = setOf("ix"), // re-add ix
        )

        val link = identities.getHabitIdentityLinksForUser("u1")
            .single { it.habitId == "h1" && it.identityId == "ix" }
        assertNull(link.effectiveTo) // resumed
        // effectiveFrom intentionally NOT advanced (per spec — gap glossed over)
    }

    @Test
    fun `create rejects empty name`() = runTest {
        val sut = SaveHabitUseCase(FakeHabitRepository(), FakeIdentityRepository(clock = fixedClock), fixedClock)
        assertFails {
            sut.create("u1", "  ", "min", 1.0, 1, setOf("ix"), null)
        }
    }

    @Test
    fun `create rejects empty identity set`() = runTest {
        val sut = SaveHabitUseCase(FakeHabitRepository(), FakeIdentityRepository(clock = fixedClock), fixedClock)
        assertFails {
            sut.create("u1", "Name", "min", 1.0, 1, emptySet(), null)
        }
    }

    @Test
    fun `create rejects threshold lt= 0`() = runTest {
        val sut = SaveHabitUseCase(FakeHabitRepository(), FakeIdentityRepository(clock = fixedClock), fixedClock)
        assertFails {
            sut.create("u1", "Name", "min", 0.0, 1, setOf("ix"), null)
        }
    }

    @Test
    fun `create rejects target lt 1`() = runTest {
        val sut = SaveHabitUseCase(FakeHabitRepository(), FakeIdentityRepository(clock = fixedClock), fixedClock)
        assertFails {
            sut.create("u1", "Name", "min", 1.0, 0, setOf("ix"), null)
        }
    }

    @Test
    fun `create rejects empty unit`() = runTest {
        val sut = SaveHabitUseCase(FakeHabitRepository(), FakeIdentityRepository(clock = fixedClock), fixedClock)
        assertFails {
            sut.create("u1", "Name", "  ", 1.0, 1, setOf("ix"), null)
        }
    }

    private fun seedHabit(id: String, templateId: String? = null) = Habit(
        id = id, userId = "u1", templateId = templateId, name = "old",
        unit = "u", thresholdPerPoint = 1.0, dailyTarget = 1,
        createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
    )
}
