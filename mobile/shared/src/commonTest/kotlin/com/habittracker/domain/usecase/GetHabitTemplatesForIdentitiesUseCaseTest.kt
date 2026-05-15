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

    @Test
    fun alsoForTemplateDedupesWhenBothPrimaryAndAlsoForIdentitiesPicked() {
        // "Read article" (template ...0003) is primary for Reader (identity ...0001)
        // and alsoFor Learner (identity ...0005). When both are picked, the template
        // must appear exactly once in the result and credit both identities.
        val readerId = "00000000-0000-0000-0000-000000000001"
        val learnerId = "00000000-0000-0000-0000-000000000005"
        val readArticleTemplateId = "10000000-0000-0000-0000-000000000003"

        val out = sut.execute(setOf(readerId, learnerId))

        val readArticleRows = out.filter { it.template.id == readArticleTemplateId }
        assertEquals(1, readArticleRows.size, "Read article should appear exactly once across primary+alsoFor buckets")
        val readArticle = readArticleRows.single()
        assertEquals("Read article", readArticle.template.name)
        assertEquals(setOf(readerId, learnerId), readArticle.recommendedBy.map { it.id }.toSet())
    }

    @Test
    fun alsoForTemplateSurfacesForAlsoForOwnerAlone() {
        // Walk (template ...0019) is primary for Athlete (...0003) and alsoFor Health-Conscious (...0008).
        // Picking only Health-Conscious must still surface Walk via the alsoFor bucket.
        val healthId = "00000000-0000-0000-0000-000000000008"
        val walkTemplateId = "10000000-0000-0000-0000-000000000019"

        val out = sut.execute(setOf(healthId))

        val walk = out.singleOrNull { it.template.id == walkTemplateId }
        assertTrue(walk != null, "Walk template should surface for Health-Conscious via alsoFor mapping")
        assertEquals(setOf(healthId), walk!!.recommendedBy.map { it.id }.toSet())
    }
}
