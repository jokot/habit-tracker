package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.PointBalance
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class GetPointBalanceUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val wantLogRepo: WantLogRepository,
    private val habitRepo: HabitRepository,
    private val wantActivityRepo: WantActivityRepository,
) {
    suspend fun execute(userId: String): Result<PointBalance> = runCatching {
        val weekStart = currentWeekStart()
        val habits = habitRepo.getHabitsForUser(userId).associateBy { it.id }
        val activities = wantActivityRepo.getWantActivities(userId).associateBy { it.id }

        val earned = habitLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= weekStart }
            .groupBy { log -> log.habitId to log.loggedAt.toLocalDateTime(TimeZone.UTC).date }
            .entries.sumOf { (key, dayLogs) ->
                val habit = habits[key.first] ?: return@sumOf 0
                dayLogs.sumOf { PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint) }
                    .coerceAtMost(habit.dailyTarget)
            }

        val spent = wantLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= weekStart }
            .sumOf { log ->
                activities[log.activityId]?.let {
                    PointCalculator.pointsSpent(log.quantity, it.costPerUnit)
                } ?: 0
            }

        PointBalance(earned, spent)
    }

    internal fun currentWeekStart(): Instant {
        val now = Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.UTC).date
        val daysFromMonday = localDate.dayOfWeek.ordinal
        val monday = localDate.minus(daysFromMonday, DateTimeUnit.DAY)
        return monday.atStartOfDayIn(TimeZone.UTC)
    }
}
