package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.HabitWithProgress
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Today's habits + points logged today, for the widget and Home screen alike. */
class GetTodayHabitsUseCase(
    private val habitRepo: HabitRepository,
    private val habitLogRepo: HabitLogRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    suspend fun execute(userId: String): List<HabitWithProgress> {
        val today = clock.now().toLocalDateTime(timeZone).date
        val dayStart = today.atStartOfDayIn(timeZone)
        val dayEnd = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)

        val habits = habitRepo.getHabitsForUser(userId).filter { it.effectiveTo == null }
        val logs = habitLogRepo.getAllActiveLogsForUser(userId)

        return habits.map { habit ->
            val pointsToday = logs
                .filter { it.habitId == habit.id && it.loggedAt >= dayStart && it.loggedAt < dayEnd }
                .sumOf { PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint) }
            HabitWithProgress(habit, pointsToday)
        }
    }
}
