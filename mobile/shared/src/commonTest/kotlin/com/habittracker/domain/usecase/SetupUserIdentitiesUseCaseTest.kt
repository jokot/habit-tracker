package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SetupUserIdentitiesUseCaseTest {

    private val seed = listOf(
        Identity("a", "Reader", "", ""),
        Identity("b", "Athlete", "", ""),
        Identity("c", "Calm", "", ""),
    )

    @Test
    fun replacesUserIdentitySet() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = SetupUserIdentitiesUseCase(repo)

        sut.execute("user-1", setOf("a", "b")).getOrThrow()

        val rows = repo.userIdentitiesSnapshot.filter { it.userId == "user-1" }.map { it.identityId }.toSet()
        assertEquals(setOf("a", "b"), rows)
    }

    @Test
    fun replaceRemovesPriorIdentityNotInNewSet() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = SetupUserIdentitiesUseCase(repo)
        sut.execute("user-1", setOf("a", "b")).getOrThrow()

        sut.execute("user-1", setOf("b", "c")).getOrThrow()

        val rows = repo.userIdentitiesSnapshot.filter { it.userId == "user-1" }.map { it.identityId }.toSet()
        assertEquals(setOf("b", "c"), rows)
    }

    @Test
    fun emptySetClearsUser() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = SetupUserIdentitiesUseCase(repo)
        sut.execute("user-1", setOf("a")).getOrThrow()

        sut.execute("user-1", emptySet()).getOrThrow()

        val rows = repo.userIdentitiesSnapshot.filter { it.userId == "user-1" }
        assertEquals(0, rows.size)
    }
}
