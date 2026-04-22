package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.HabitLog
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
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
            val todayDate = now.toLocalDateTime(TimeZone.UTC).date
            val dayStart = todayDate.atStartOfDayIn(TimeZone.UTC)
            val dayEnd = dayStart.plus(1, DateTimeUnit.DAY, TimeZone.UTC)
            val existing = habitLogRepository.getActiveLogsForHabitOnDay(userId, habitId, dayStart, dayEnd)
            val rawBefore = existing.sumOf {
                PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint)
            }
            val cappedBefore = rawBefore.coerceAtMost(habit.dailyTarget)

            val id = Uuid.random().toString()
            val log = habitLogRepository.insertLog(id, userId, habitId, quantity, now)

            val rawAfter = rawBefore + PointCalculator.pointsEarned(quantity, habit.thresholdPerPoint)
            val cappedAfter = rawAfter.coerceAtMost(habit.dailyTarget)
            LogHabitResult(log, cappedAfter - cappedBefore)
        }
}
