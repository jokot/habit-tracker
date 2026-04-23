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

enum class LogHabitStatus {
    /** Quantity met threshold and awarded 1+ points (respecting daily cap). */
    EARNED,

    /** Quantity fell below the per-point threshold — 0 pts awarded. */
    BELOW_THRESHOLD,

    /** Daily target already reached for this habit — log recorded, 0 pts. */
    DAILY_TARGET_MET,
}

data class LogHabitResult(
    val log: HabitLog,
    val pointsEarned: Int,
    val status: LogHabitStatus,
)

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
            val rawThisLog = PointCalculator.pointsEarned(quantity, habit.thresholdPerPoint)

            val id = Uuid.random().toString()
            val log = habitLogRepository.insertLog(id, userId, habitId, quantity, now)

            val cappedAfter = (rawBefore + rawThisLog).coerceAtMost(habit.dailyTarget)
            val delta = cappedAfter - cappedBefore

            val status = when {
                delta > 0 -> LogHabitStatus.EARNED
                rawThisLog == 0 -> LogHabitStatus.BELOW_THRESHOLD
                else -> LogHabitStatus.DAILY_TARGET_MET
            }
            LogHabitResult(log, delta, status)
        }
}
