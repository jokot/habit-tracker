package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import com.habittracker.data.repository.FakeWantActivityRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SetupUserWantActivitiesUseCaseTest {
    private val userId = "u1"
    private val fixedClock = object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(123_000) }

    private fun newSut(): Pair<SetupUserWantActivitiesUseCase, FakeWantActivityRepository> {
        val repo = FakeWantActivityRepository()
        return SetupUserWantActivitiesUseCase(repo, SeedData.wantActivities, fixedClock) to repo
    }

    @Test
    fun `reconcile inserts all 14 seed items for new user`() = runTest {
        val (sut, repo) = newSut()
        sut.reconcile(userId).getOrThrow()
        assertEquals(SeedData.wantActivities.size, repo.getAllWantActivitiesForUser(userId).size)
    }

    @Test
    fun `reconcile preserves customized cost on existing seed row`() = runTest {
        val (sut, repo) = newSut()
        val customized = SeedData.wantActivities.first().copy(costPerUnit = 5.0)
        repo.saveWantActivity(customized, userId)

        sut.reconcile(userId).getOrThrow()

        val tiktok = repo.getAllWantActivitiesForUser(userId)
            .single { it.id == customized.id }
        assertEquals(5.0, tiktok.costPerUnit)
    }

    @Test
    fun `reconcile preserves hidden state on existing seed row`() = runTest {
        val (sut, repo) = newSut()
        val seed = SeedData.wantActivities.first()
        repo.saveWantActivity(seed, userId)
        repo.hideWantActivity(seed.id, userId, Instant.fromEpochMilliseconds(2_000))

        sut.reconcile(userId).getOrThrow()

        val row = repo.getAllWantActivitiesForUser(userId).single { it.id == seed.id }
        assertNotNull(row.hiddenAt)
    }

    @Test
    fun `reconcile is idempotent`() = runTest {
        val (sut, repo) = newSut()
        sut.reconcile(userId).getOrThrow()
        val firstCount = repo.getAllWantActivitiesForUser(userId).size
        sut.reconcile(userId).getOrThrow()
        val secondCount = repo.getAllWantActivitiesForUser(userId).size
        assertEquals(firstCount, secondCount)
    }
}
