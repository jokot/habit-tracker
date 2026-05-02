package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinIdentityUseCaseTest {
    private val repo = FakeIdentityRepository()
    private val useCase = PinIdentityUseCase(repo)
    private val userId = "u1"

    @Test
    fun `pins target identity`() = runTest {
        repo.seedUserIdentity(userId, "athlete")
        useCase.execute(userId, "athlete")
        assertTrue(repo.isPinned(userId, "athlete"))
    }

    @Test
    fun `clears previous pin when pinning a new identity`() = runTest {
        repo.seedUserIdentity(userId, "athlete", isPinned = true)
        repo.seedUserIdentity(userId, "reader")
        useCase.execute(userId, "reader")
        assertFalse(repo.isPinned(userId, "athlete"))
        assertTrue(repo.isPinned(userId, "reader"))
    }
}
