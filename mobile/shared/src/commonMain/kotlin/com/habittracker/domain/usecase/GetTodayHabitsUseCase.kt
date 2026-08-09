package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.HabitWithProgress
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
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
    /** Self-fetching entry point: queries the repos itself. Used by the widget. */
    suspend fun execute(userId: String): List<HabitWithProgress> {
        val today = clock.now().toLocalDateTime(timeZone).date
        val dayStart = today.atStartOfDayIn(timeZone)
        val dayEnd = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        val habits = habitRepo.getHabitsForUser(userId)
        val logs = habitLogRepo.getAllActiveLogsForUser(userId)
        return execute(habits, logs, dayStart, dayEnd)
    }

    /** Pure entry point: reuses data/day-bounds a caller already has (e.g. HomeViewModel's combine()). */
    fun execute(
        habits: List<Habit>,
        logs: List<HabitLog>,
        dayStart: Instant,
        dayEnd: Instant,
    ): List<HabitWithProgress> {
        return habits.filter { it.effectiveTo == null }.map { habit ->
            val pointsToday = logs
                .filter { it.habitId == habit.id && it.loggedAt >= dayStart && it.loggedAt < dayEnd }
                .sumOf { PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint) }
            HabitWithProgress(habit, pointsToday)
        }
    }
}
