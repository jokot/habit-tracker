package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitTemplate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SetupUserHabitsUseCase(
    private val habitRepository: HabitRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(userId: String, templates: List<HabitTemplate>): Result<Map<String, String>> =
        runCatching {
            val now = clock.now()
            // Onboarding habits anchor at start-of-day so the engines (which use
            // instant-grace `effectiveFrom <= dayStart` for today) see them as
            // active on signup day. Mid-day creations elsewhere use clock.now()
            // to preserve 5c-2 grace.
            val today = now.toLocalDateTime(timeZone).date
            val effectiveFrom = today.atStartOfDayIn(timeZone)
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
                        effectiveFrom = effectiveFrom,
                    )
                )
                mapping[template.id] = habitId
            }
            mapping.toMap()
        }
}
