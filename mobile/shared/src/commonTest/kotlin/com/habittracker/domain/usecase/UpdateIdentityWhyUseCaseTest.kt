package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateIdentityWhyUseCaseTest {
    private val repo = FakeIdentityRepository()
    private val useCase = UpdateIdentityWhyUseCase(repo)
    private val userId = "u1"

    @Test
    fun `sets whyText to provided string`() = runTest {
        repo.seedUserIdentity(userId, "athlete")
        useCase.execute(userId, "athlete", "I want to be strong.")
        assertEquals("I want to be strong.", repo.getWhyText(userId, "athlete"))
    }

    @Test
    fun `whitespace-only string normalizes to null`() = runTest {
        repo.seedUserIdentity(userId, "athlete")
        useCase.execute(userId, "athlete", "   \n  ")
        assertNull(repo.getWhyText(userId, "athlete"))
    }

    @Test
    fun `empty string normalizes to null`() = runTest {
        repo.seedUserIdentity(userId, "athlete")
        useCase.execute(userId, "athlete", "")
        assertNull(repo.getWhyText(userId, "athlete"))
    }
}
