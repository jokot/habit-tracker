package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoveIdentityUseCaseTest {
    private val seed = listOf(
        Identity(id = "athlete", name = "Athlete", description = "", icon = ""),
    )
    private val repo = FakeIdentityRepository(seed)
    private val useCase = RemoveIdentityUseCase(repo, clock = Clock.System)
    private val userId = "u1"

    @Test
    fun `soft-deletes identity and clears its pin`() = runTest {
        repo.seedUserIdentity(userId, "athlete", isPinned = true)
        useCase.execute(userId, "athlete")
        // Active list excludes removed
        val active = repo.observeUserIdentities(userId).first()
        assertTrue(active.none { it.id == "athlete" })
        // Pin cleared
        assertFalse(repo.isPinned(userId, "athlete"))
    }
}
