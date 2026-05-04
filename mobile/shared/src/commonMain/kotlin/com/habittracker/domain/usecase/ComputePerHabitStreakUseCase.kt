package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.PerHabitDayState
import com.habittracker.domain.model.PerHabitStreakResult
import com.habittracker.domain.model.StreakDayState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class ComputePerHabitStreakUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val habitRepo: HabitRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun observe(userId: String, habitId: String): Flow<PerHabitStreakResult> =
        habitLogRepo.observeAllActiveLogsForUser(userId).map { allLogs ->
            val habit = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
                ?: return@map PerHabitStreakResult.emptyFor(habitId, todayLocal())
            compute(habit, allLogs.filter { it.habitId == habitId })
        }

    suspend fun computeNow(userId: String, habitId: String): PerHabitStreakResult {
        val habit = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
            ?: return PerHabitStreakResult.emptyFor(habitId, todayLocal())
        val logs = habitLogRepo.observeAllActiveLogsForUser(userId).first()
            .filter { it.habitId == habitId }
        return compute(habit, logs)
    }

    private fun compute(habit: Habit, logs: List<HabitLog>): PerHabitStreakResult {
        val today = todayLocal()
        val totalLogs = logs.size
        val pointsEarned = logs.sumOf {
            PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint)
        }
        val firstLogDate = logs.minByOrNull { it.loggedAt }?.loggedAt?.toLocalDate()
        if (firstLogDate == null) {
            return PerHabitStreakResult(
                habitId = habit.id,
                totalLogs = 0,
                pointsEarned = 0,
                currentStreak = 0,
                longestStreak = 0,
                firstLogDate = null,
                last30Days = thirtyDayWindow(today, emptyMap(), emptyMap(), habit.dailyTarget),
            )
        }

        val loggedDays: Set<LocalDate> = logs.map { it.loggedAt.toLocalDate() }.toSet()
        // Per-day capped points: cap at dailyTarget so heat bucket maxes out at full.
        val pointsByDay: Map<LocalDate, Int> = logs.groupBy { it.loggedAt.toLocalDate() }
            .mapValues { (_, dayLogs) ->
                dayLogs.sumOf { PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint) }
                    .coerceAtMost(habit.dailyTarget)
            }

        val perDay = mutableMapOf<LocalDate, StreakDayState>()
        var prev: StreakDayState? = null
        var run = 0
        var longest = 0
        var cursor: LocalDate = firstLogDate
        while (cursor <= today) {
            val dayStart = cursor.atStartOfDayIn(timeZone)
            val active = habitActiveOn(habit, dayStart)
            val state = when {
                !active -> StreakDayState.EMPTY
                cursor in loggedDays -> StreakDayState.COMPLETE
                cursor == today -> StreakDayState.TODAY_PENDING
                prev == StreakDayState.COMPLETE -> StreakDayState.FROZEN
                prev == StreakDayState.FROZEN -> StreakDayState.BROKEN
                prev == StreakDayState.BROKEN -> StreakDayState.BROKEN
                prev == StreakDayState.TODAY_PENDING -> StreakDayState.FROZEN
                else -> StreakDayState.EMPTY
            }
            perDay[cursor] = state
            when (state) {
                StreakDayState.COMPLETE -> {
                    run += 1
                    if (run > longest) longest = run
                }
                StreakDayState.FROZEN -> Unit
                StreakDayState.BROKEN -> { run = 0 }
                StreakDayState.TODAY_PENDING, StreakDayState.EMPTY, StreakDayState.FUTURE -> Unit
            }
            prev = state
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }

        return PerHabitStreakResult(
            habitId = habit.id,
            totalLogs = totalLogs,
            pointsEarned = pointsEarned,
            currentStreak = run,
            longestStreak = longest,
            firstLogDate = firstLogDate,
            last30Days = thirtyDayWindow(today, perDay, pointsByDay, habit.dailyTarget),
        )
    }

    private fun thirtyDayWindow(
        today: LocalDate,
        perDay: Map<LocalDate, StreakDayState>,
        pointsByDay: Map<LocalDate, Int>,
        dailyTarget: Int,
    ): List<PerHabitDayState> {
        val start = today.minus(29, DateTimeUnit.DAY)
        return (0 until 30).map { offset ->
            val d = start.plus(offset, DateTimeUnit.DAY)
            val state = perDay[d] ?: StreakDayState.EMPTY
            val level = if (state == StreakDayState.COMPLETE) {
                bucketFor(pointsByDay[d] ?: 0, bareMin = 1, full = dailyTarget.coerceAtLeast(1))
            } else 0
            PerHabitDayState(d, state, level)
        }
    }

    /**
     * Heat bucket — mirrors ComputeIdentityStatsUseCase.bucketFor but scoped to a
     * single habit (bareMin = 1 log, full = dailyTarget).
     */
    private fun bucketFor(pointsCapped: Int, bareMin: Int, full: Int): Int {
        if (pointsCapped <= 0) return 0
        val span = (full - bareMin).coerceAtLeast(0)
        val third = span / 3
        val mid1 = bareMin + third
        val mid2 = bareMin + 2 * third
        return when {
            pointsCapped < mid1 -> 1
            pointsCapped < mid2 -> 2
            pointsCapped < full -> 3
            else -> 4
        }
    }

    private fun habitActiveOn(habit: Habit, dayStart: Instant): Boolean {
        // Per-habit uses date-overlap (effectiveFrom < dayEnd), unlike user-level
        // and identity engines which use the 5c-2 instant grace (effectiveFrom <=
        // dayStart). Reason: per-habit grid must show today's log as COMPLETE for
        // a habit created today. The grace only applies to multi-habit aggregates
        // — a single habit logged today is COMPLETE for itself by definition.
        val dayEnd = dayStart.plus(1, DateTimeUnit.DAY, timeZone)
        return (habit.effectiveFrom?.let { it < dayEnd } ?: true) &&
            (habit.effectiveTo?.let { it > dayStart } ?: true)
    }

    private fun Instant.toLocalDate(): LocalDate = toLocalDateTime(timeZone).date

    private fun todayLocal(): LocalDate = clock.now().toLocalDateTime(timeZone).date
}
