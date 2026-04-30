package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import com.habittracker.data.repository.IdentityRepository

class LinkOnboardingHabitsToIdentitiesUseCase(
    private val identityRepository: IdentityRepository,
) {
    /**
     * Production entry: uses real SeedData.identityHabitMap to look up which identities
     * recommend each template.
     */
    suspend fun execute(
        userId: String,
        templateIdToHabitId: Map<String, String>,
        selectedIdentityIds: Set<String>,
    ): Result<Unit> = executeWithMap(
        userId = userId,
        templateIdToHabitId = templateIdToHabitId,
        selectedIdentityIds = selectedIdentityIds,
        templateRecommendedBy = buildRecommendationMap(templateIdToHabitId.keys),
    )

    /**
     * Test entry: lets tests inject the recommendation map directly.
     */
    suspend fun executeWithMap(
        userId: String,
        templateIdToHabitId: Map<String, String>,
        selectedIdentityIds: Set<String>,
        templateRecommendedBy: Map<String, Set<String>>,
    ): Result<Unit> = runCatching {
        @Suppress("UNUSED_PARAMETER") val _u = userId
        templateIdToHabitId.forEach { (templateId, habitId) ->
            val recommenders = templateRecommendedBy[templateId].orEmpty()
            val applied = recommenders.intersect(selectedIdentityIds)
            if (applied.isNotEmpty()) {
                identityRepository.linkHabitToIdentities(habitId, applied)
            }
        }
    }

    private fun buildRecommendationMap(templateIds: Set<String>): Map<String, Set<String>> {
        val out = mutableMapOf<String, MutableSet<String>>()
        SeedData.identityHabitMap.forEach { (identityId, recommendedTemplates) ->
            recommendedTemplates.forEach { templateId ->
                if (templateId in templateIds) {
                    out.getOrPut(templateId) { mutableSetOf() }.add(identityId)
                }
            }
        }
        return out
    }
}
