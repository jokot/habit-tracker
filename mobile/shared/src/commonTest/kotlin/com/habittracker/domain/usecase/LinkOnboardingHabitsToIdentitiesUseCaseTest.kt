package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkOnboardingHabitsToIdentitiesUseCaseTest {

    private val seed = listOf(
        Identity("reader", "Reader", "", ""),
        Identity("athlete", "Athlete", "", ""),
        Identity("calm", "Calm", "", ""),
    )

    @Test
    fun habitRecommendedByTwoSelectedIdentitiesYieldsTwoJoinRows() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = LinkOnboardingHabitsToIdentitiesUseCase(repo)
        val templateIdToHabitId = mapOf("tpl-1" to "habit-1")
        val recommendedBy = mapOf("tpl-1" to setOf("reader", "athlete"))

        sut.executeWithMap(
            userId = "user-1",
            templateIdToHabitId = templateIdToHabitId,
            selectedIdentityIds = setOf("reader", "athlete"),
            templateRecommendedBy = recommendedBy,
        ).getOrThrow()

        val rows = repo.habitIdentitiesSnapshot.filter { it.habitId == "habit-1" }.map { it.identityId }.toSet()
        assertEquals(setOf("reader", "athlete"), rows)
    }

    @Test
    fun habitRecommendedByTwoIdentitiesOnlyOneSelectedYieldsOneRow() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = LinkOnboardingHabitsToIdentitiesUseCase(repo)
        val recommendedBy = mapOf("tpl-1" to setOf("reader", "athlete"))

        sut.executeWithMap(
            userId = "user-1",
            templateIdToHabitId = mapOf("tpl-1" to "habit-1"),
            selectedIdentityIds = setOf("reader"),
            templateRecommendedBy = recommendedBy,
        ).getOrThrow()

        val rows = repo.habitIdentitiesSnapshot.filter { it.habitId == "habit-1" }.map { it.identityId }.toSet()
        assertEquals(setOf("reader"), rows)
    }

    @Test
    fun habitNotRecommendedByAnySelectedIdentityYieldsNoRows() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = LinkOnboardingHabitsToIdentitiesUseCase(repo)
        val recommendedBy = mapOf("tpl-1" to setOf("calm"))

        sut.executeWithMap(
            userId = "user-1",
            templateIdToHabitId = mapOf("tpl-1" to "habit-1"),
            selectedIdentityIds = setOf("reader", "athlete"),
            templateRecommendedBy = recommendedBy,
        ).getOrThrow()

        assertEquals(0, repo.habitIdentitiesSnapshot.size)
    }
}
