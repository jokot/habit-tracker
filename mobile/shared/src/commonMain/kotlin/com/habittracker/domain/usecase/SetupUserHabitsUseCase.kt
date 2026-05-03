package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitTemplate
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SetupUserHabitsUseCase(
    private val habitRepository: HabitRepository,
    private val clock: Clock = Clock.System,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(userId: String, templates: List<HabitTemplate>): Result<Map<String, String>> =
        runCatching {
            val now = clock.now()
            val mapping = mutableMapOf<String, String>()
            templates.forEach { template ->
                val habitId = Uuid.random().toString()
                habitRepository.saveHabit(
                    Habit(
                        id = habitId,
                        userId = userId,
                        templateId = template.id,
                        name = template.name,
                        unit = template.unit,
                        thresholdPerPoint = template.defaultThreshold,
                        dailyTarget = template.defaultDailyTarget,
                        createdAt = now,
                        updatedAt = now,
                        effectiveFrom = now,
                    )
                )
                mapping[template.id] = habitId
            }
            mapping.toMap()
        }
}
