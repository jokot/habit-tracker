package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

class UnpinIdentityUseCaseTest {
    private val repo = FakeIdentityRepository()
    private val useCase = UnpinIdentityUseCase(repo)
    private val userId = "u1"

    @Test
    fun `unpins identity`() = runTest {
        repo.seedUserIdentity(userId, "athlete", isPinned = true)
        useCase.execute(userId, "athlete")
        assertFalse(repo.isPinned(userId, "athlete"))
    }
}
