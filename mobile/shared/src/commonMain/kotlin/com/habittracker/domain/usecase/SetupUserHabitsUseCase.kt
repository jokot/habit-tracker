package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitTemplate
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SetupUserHabitsUseCase(private val habitRepository: HabitRepository) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(userId: String, templates: List<HabitTemplate>): Result<Unit> =
        runCatching {
            val now = Clock.System.now()
            templates.forEach { template ->
                habitRepository.saveHabit(
                    Habit(
                        id = Uuid.random().toString(),
                        userId = userId,
                        templateId = template.id,
                        name = template.name,
                        unit = template.unit,
                        thresholdPerPoint = template.defaultThreshold,
                        dailyTarget = template.defaultDailyTarget,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
}
