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
                last30Days = thirtyDayWindow(today, emptyMap()),
            )
        }

        val loggedDays: Set<LocalDate> = logs.map { it.loggedAt.toLocalDate() }.toSet()

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
            last30Days = thirtyDayWindow(today, perDay),
        )
    }

    private fun thirtyDayWindow(
        today: LocalDate,
        perDay: Map<LocalDate, StreakDayState>,
    ): List<PerHabitDayState> {
        val start = today.minus(29, DateTimeUnit.DAY)
        return (0 until 30).map { offset ->
            val d = start.plus(offset, DateTimeUnit.DAY)
            PerHabitDayState(d, perDay[d] ?: StreakDayState.EMPTY)
        }
    }

    private fun habitActiveOn(habit: Habit, dayStart: Instant): Boolean =
        (habit.effectiveFrom?.let { it <= dayStart } ?: true) &&
            (habit.effectiveTo?.let { it > dayStart } ?: true)

    private fun Instant.toLocalDate(): LocalDate = toLocalDateTime(timeZone).date

    private fun todayLocal(): LocalDate = clock.now().toLocalDateTime(timeZone).date
}
