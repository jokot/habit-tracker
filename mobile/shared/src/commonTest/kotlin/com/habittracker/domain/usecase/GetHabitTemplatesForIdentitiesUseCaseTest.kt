package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetHabitTemplatesForIdentitiesUseCaseTest {

    private val sut = GetHabitTemplatesForIdentitiesUseCase()

    @Test
    fun emptyInputReturnsEmpty() {
        assertEquals(emptyList(), sut.execute(emptySet()))
    }

    @Test
    fun singleIdentityReturnsItsTemplates() {
        val firstIdentityId = SeedData.identityHabitMap.keys.first()
        val expectedSize = SeedData.identityHabitMap[firstIdentityId]!!.size

        val out = sut.execute(setOf(firstIdentityId))

        assertEquals(expectedSize, out.size)
        out.forEach { row ->
            assertEquals(setOf(firstIdentityId), row.recommendedBy.map { it.id }.toSet())
        }
    }

    @Test
    fun unionDedupesSharedTemplateAcrossTwoIdentities() {
        val ids = SeedData.identityHabitMap.keys.toList()
        val pair = ids.firstNotNullOfOrNull { a ->
            ids.firstOrNull { b -> b != a && SeedData.identityHabitMap[a]!!.intersect(SeedData.identityHabitMap[b]!!.toSet()).isNotEmpty() }
                ?.let { a to it }
        }
        assertTrue(pair != null, "Seed has no overlapping templates between identities — extend seed or rewrite test.")
        val (a, b) = pair!!
        val sharedTemplateId = SeedData.identityHabitMap[a]!!.intersect(SeedData.identityHabitMap[b]!!.toSet()).first()

        val out = sut.execute(setOf(a, b))

        val shared = out.first { it.template.id == sharedTemplateId }
        assertEquals(setOf(a, b), shared.recommendedBy.map { it.id }.toSet())
        assertEquals(1, out.count { it.template.id == sharedTemplateId })
    }
}
