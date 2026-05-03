package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddIdentityWithHabitsUseCaseTest {
    // Use real SeedData identity IDs — SeedData uses UUIDs.
    // "00000000-0000-0000-0000-000000000003" = Athlete
    // "00000000-0000-0000-0000-000000000002" = Builder (used as the "other identity" stand-in)
    private val athleteId = "00000000-0000-0000-0000-000000000003"
    private val builderId = "00000000-0000-0000-0000-000000000002"

    private val seed = listOf(
        Identity(id = athleteId, name = "Athlete",  description = "", icon = ""),
        Identity(id = builderId, name = "Builder",  description = "", icon = ""),
    )
    private val habitRepo = FakeHabitRepository()
    private val identityRepo = FakeIdentityRepository(seed)
    private val templates = GetHabitTemplatesForIdentitiesUseCase()
    private val useCase = AddIdentityWithHabitsUseCase(
        habitRepo = habitRepo,
        identityRepo = identityRepo,
        templates = templates,
        clock = Clock.System,
    )
    private val userId = "u1"

    @Test
    fun `inserts user_identity row`() = runTest {
        useCase.execute(userId, athleteId, selectedTemplateIds = emptySet<String>())
        val active = identityRepo.observeUserIdentities(userId).first()
        assertTrue(active.any { it.id == athleteId })
    }

    @Test
    fun `creates new habit and link for each selected template not yet owned`() = runTest {
        // GetHabitTemplatesForIdentitiesUseCase returns List<TemplateWithIdentities>.
        // Filter to templates recommended by the athlete identity.
        val athleteTemplates = templates.execute(setOf(athleteId))
            .filter { twi -> twi.recommendedBy.any { it.id == athleteId } }
        require(athleteTemplates.size >= 2) { "Need ≥2 athlete templates for this test" }
        val tplA = athleteTemplates[0].template.id
        val tplB = athleteTemplates[1].template.id

        useCase.execute(userId, athleteId, selectedTemplateIds = setOf(tplA, tplB))

        val habits = habitRepo.getHabitsForUser(userId)
        assertEquals(2, habits.size)

        // Verify the habit_identity links were written — use the snapshot since
        // FakeIdentityRepository.observeHabitsForIdentity requires habits be seeded
        // into its own internal store as well.
        val links = identityRepo.habitIdentitiesSnapshot.filter { it.identityId == athleteId }
        assertEquals(2, links.size)
        val linkedHabitIds = links.map { it.habitId }.toSet()
        val createdHabitIds = habits.map { it.id }.toSet()
        assertEquals(createdHabitIds, linkedHabitIds)
    }

    @Test
    fun `reuses existing habit when templateId already in user's habits`() = runTest {
        val athleteTemplates = templates.execute(setOf(athleteId))
            .filter { twi -> twi.recommendedBy.any { it.id == athleteId } }
        require(athleteTemplates.isNotEmpty())
        val sharedTpl = athleteTemplates.first().template.id

        // Seed an existing habit for the user with that templateId, linked to builder identity.
        val now = Clock.System.now()
        val existing = Habit(
            id = "h-existing",
            userId = userId,
            templateId = sharedTpl,
            name = "Pre-existing",
            unit = "min",
            thresholdPerPoint = 1.0,
            dailyTarget = 1,
            createdAt = now,
            updatedAt = now,
            syncedAt = null,
        )
        habitRepo.saveHabit(existing)
        identityRepo.linkHabitToIdentities("h-existing", setOf(builderId))

        useCase.execute(userId, athleteId, selectedTemplateIds = setOf(sharedTpl))

        val habits = habitRepo.getHabitsForUser(userId)
        assertEquals(1, habits.size, "should not create duplicate habit")

        // Verify that the existing habit was linked to the athlete identity (not a new one).
        val athleteLinks = identityRepo.habitIdentitiesSnapshot.filter { it.identityId == athleteId }
        assertEquals(1, athleteLinks.size)
        assertEquals("h-existing", athleteLinks.first().habitId)
    }

    @Test
    fun `new habit has effectiveFrom set to now`() = runTest {
        val fixedNow = Clock.System.now()
        val fixedClock = object : Clock { override fun now() = fixedNow }
        val useCaseWithFixedClock = AddIdentityWithHabitsUseCase(
            habitRepo = habitRepo,
            identityRepo = identityRepo,
            templates = templates,
            clock = fixedClock,
        )
        val athleteTemplates = templates.execute(setOf(athleteId))
            .filter { twi -> twi.recommendedBy.any { it.id == athleteId } }
        require(athleteTemplates.isNotEmpty()) { "Need athlete templates seeded" }
        val tplId = athleteTemplates.first().template.id

        useCaseWithFixedClock.execute(userId, athleteId, selectedTemplateIds = setOf(tplId))

        val habits = habitRepo.getHabitsForUser(userId)
        assertEquals(1, habits.size)
        assertEquals(fixedNow, habits.first().effectiveFrom)
    }
}
