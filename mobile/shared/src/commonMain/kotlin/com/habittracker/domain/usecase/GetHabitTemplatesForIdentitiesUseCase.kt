package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.TemplateWithIdentities

class GetHabitTemplatesForIdentitiesUseCase {
    fun execute(identityIds: Set<String>): List<TemplateWithIdentities> {
        if (identityIds.isEmpty()) return emptyList()
        val identityMap = SeedData.identities.associateBy { it.id }
        val templateToIdentities = mutableMapOf<String, MutableSet<Identity>>()
        identityIds.forEach { idId ->
            val identity = identityMap[idId] ?: return@forEach
            SeedData.identityHabitMap[idId].orEmpty().forEach { templateId ->
                templateToIdentities.getOrPut(templateId) { mutableSetOf() }.add(identity)
            }
        }
        return templateToIdentities.mapNotNull { (templateId, identities) ->
            val template = SeedData.habitTemplates[templateId] ?: return@mapNotNull null
            TemplateWithIdentities(template = template, recommendedBy = identities.toSet())
        }
    }
}
