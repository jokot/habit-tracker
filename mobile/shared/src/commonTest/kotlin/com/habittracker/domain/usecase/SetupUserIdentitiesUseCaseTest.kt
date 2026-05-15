package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

    @Test
    fun seedCatalogExposes13IdentitiesWithCanonicalHueAndMaterialIcon() {
        // Canonical-catalog proof: the reconcile path consumes SeedData.identities directly,
        // so asserting catalog shape is the strongest fact-level test available without a
        // dedicated reconcile entry point on SetupUserIdentitiesUseCase.
        assertEquals(13, SeedData.identities.size)

        // No duplicate IDs.
        assertEquals(13, SeedData.identities.map { it.id }.toSet().size)

        // Spot-check canonical hue + Material icon shape for two well-known identities.
        val athlete = SeedData.identities.single { it.id == "00000000-0000-0000-0000-000000000003" }
        assertEquals("Athlete", athlete.name)
        assertEquals("directions_run", athlete.icon)
        assertEquals(5, athlete.hue)

        val creator = SeedData.identities.single { it.id == "00000000-0000-0000-0000-000000000009" }
        assertEquals("Creator", creator.name)
        assertEquals("palette", creator.icon)
        assertEquals(315, creator.hue)

        // Every identity has a Material icon name (snake_case, no emoji) and a hue in [0, 360].
        SeedData.identities.forEach { id ->
            assertNotNull(id.icon.takeIf { it.isNotBlank() }, "identity ${id.id} has blank icon")
            assertEquals(true, id.icon.all { it.isLowerCase() || it == '_' }, "identity ${id.id} icon is not snake_case Material name: ${id.icon}")
            assertEquals(true, id.hue in 0..360, "identity ${id.id} hue ${id.hue} out of [0,360]")
        }
    }
}
