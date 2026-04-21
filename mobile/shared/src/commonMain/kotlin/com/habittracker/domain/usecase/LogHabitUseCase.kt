package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.HabitLog
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class LogHabitResult(val log: HabitLog, val pointsEarned: Int)

class LogHabitUseCase(
    private val habitLogRepository: HabitLogRepository,
    private val habitRepository: HabitRepository,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(userId: String, habitId: String, quantity: Double): Result<LogHabitResult> =
        runCatching {
            val habit = habitRepository.getHabitsForUser(userId)
                .firstOrNull { it.id == habitId }
                ?: error("Habit $habitId not found for user $userId")
            val now = Clock.System.now()
            val id = Uuid.random().toString()
            val log = habitLogRepository.insertLog(id, userId, habitId, quantity, now)
            val points = PointCalculator.pointsEarned(quantity, habit.thresholdPerPoint)
            LogHabitResult(log, points)
        }
}
