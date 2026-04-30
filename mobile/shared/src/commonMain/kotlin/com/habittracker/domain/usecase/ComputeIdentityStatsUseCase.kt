package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.IdentityStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class ComputeIdentityStatsUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val identityRepo: IdentityRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun observe(userId: String, identityId: String): Flow<IdentityStats> {
        val habitsFlow = identityRepo.observeHabitsForIdentity(userId, identityId)
        val logsFlow = habitLogRepo.observeAllActiveLogsForUser(userId)
        return combine(habitsFlow, logsFlow) { habits, logs -> compute(identityId, habits, logs) }
    }

    suspend fun computeNow(userId: String, identityId: String): IdentityStats {
        val habits = identityRepo.observeHabitsForIdentity(userId, identityId).first()
        val logs = habitLogRepo.getAllActiveLogsForUser(userId)
        return compute(identityId, habits, logs)
    }

    private fun compute(identityId: String, habits: List<Habit>, allLogs: List<HabitLog>): IdentityStats {
        if (habits.isEmpty()) return IdentityStats.emptyFor(identityId)
        val today = clock.now().toLocalDateTime(timeZone).date
        val habitIds = habits.map { it.id }.toSet()
        val habitsById = habits.associateBy { it.id }
        val logsForIdentity = allLogs.filter { it.habitId in habitIds }
        val targetSum = habits.sumOf { it.dailyTarget }.coerceAtLeast(1)

        // Per-(day,habit) point sum (capped per habit at dailyTarget when summed for day).
        val rawByDayHabit = mutableMapOf<Pair<LocalDate, String>, Int>()
        val loggedHabitsByDay = mutableMapOf<LocalDate, MutableSet<String>>()
        logsForIdentity.forEach { log ->
            val date = log.loggedAt.toLocalDateTime(timeZone).date
            val habit = habitsById[log.habitId] ?: return@forEach
            val pts = PointCalculator.pointsEarned(log.quantity, habit.thresholdPerPoint)
            val key = date to log.habitId
            rawByDayHabit[key] = (rawByDayHabit[key] ?: 0) + pts
            loggedHabitsByDay.getOrPut(date) { mutableSetOf() }.add(log.habitId)
        }
        val pointsByDay = mutableMapOf<LocalDate, Int>()
        rawByDayHabit.forEach { (key, pts) ->
            val (date, habitId) = key
            val target = habitsById[habitId]?.dailyTarget ?: return@forEach
            pointsByDay[date] = (pointsByDay[date] ?: 0) + pts.coerceAtMost(target)
        }

        val streak = computeStreak(today, habitIds, loggedHabitsByDay)
        val daysActive = loggedHabitsByDay.keys.count { it <= today }
        val last14 = buildHeatList(today, 14, pointsByDay, targetSum)
        val last90 = buildHeatList(today, 90, pointsByDay, targetSum)

        return IdentityStats(
            identityId = identityId,
            currentStreak = streak,
            daysActive = daysActive,
            habitCount = habits.size,
            last14Heat = last14,
            last90Heat = last90,
        )
    }

    private fun computeStreak(today: LocalDate, habitIds: Set<String>, loggedHabitsByDay: Map<LocalDate, Set<String>>): Int {
        val isComplete: (LocalDate) -> Boolean = { d ->
            val logged = loggedHabitsByDay[d].orEmpty()
            habitIds.all { it in logged }
        }
        var run = 0
        var cursor = today
        if (!isComplete(cursor)) cursor = cursor.minus(1, DateTimeUnit.DAY)
        while (isComplete(cursor)) {
            run++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        return run
    }

    private fun buildHeatList(today: LocalDate, length: Int, pointsByDay: Map<LocalDate, Int>, targetSum: Int): List<Int> {
        val list = ArrayList<Int>(length)
        var cursor = today.minus(length - 1, DateTimeUnit.DAY)
        while (list.size < length) {
            val pts = pointsByDay[cursor] ?: 0
            val ratio = pts.toDouble() / targetSum
            list += heatBucket(ratio)
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return list
    }

    private fun heatBucket(ratio: Double): Int = when {
        ratio <= 0.0 -> 0
        ratio <= 0.25 -> 1
        ratio <= 0.5 -> 2
        ratio <= 0.75 -> 3
        else -> 4
    }
}
