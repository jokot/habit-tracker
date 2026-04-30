package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.DayPoints
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

class GetDayPointsUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val wantLogRepo: WantLogRepository,
    private val habitRepo: HabitRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend fun execute(userId: String, day: LocalDate): Result<DayPoints> = runCatching {
        val dayStart = day.atStartOfDayIn(timeZone)
        val nextDayStart = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        val habits = habitRepo.getHabitsForUser(userId).associateBy { it.id }
        val activities = wantActivityRepo.getWantActivities(userId).associateBy { it.id }

        val earned = habitLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= dayStart && it.loggedAt < nextDayStart }
            .groupBy { it.habitId }
            .entries.sumOf { (habitId, logs) ->
                val habit = habits[habitId] ?: return@sumOf 0
                logs.sumOf { PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint) }
                    .coerceAtMost(habit.dailyTarget)
            }

        val spent = wantLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= dayStart && it.loggedAt < nextDayStart }
            .sumOf { log ->
                activities[log.activityId]?.let {
                    PointCalculator.pointsSpent(log.quantity, it.costPerUnit)
                } ?: 0
            }

        DayPoints(earned = earned, spent = spent)
    }
}
