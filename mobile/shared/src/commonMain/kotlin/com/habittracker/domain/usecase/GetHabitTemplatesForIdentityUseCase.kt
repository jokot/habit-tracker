package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import com.habittracker.domain.model.HabitTemplate

class GetHabitTemplatesForIdentityUseCase {
    fun execute(identityId: String): List<HabitTemplate> =
        SeedData.identityHabitMap[identityId]
            ?.mapNotNull { SeedData.habitTemplates[it] }
            ?: emptyList()
}
