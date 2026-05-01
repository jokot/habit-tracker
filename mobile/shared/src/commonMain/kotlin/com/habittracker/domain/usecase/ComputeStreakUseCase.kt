package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.habittracker.domain.model.StreakRangeResult
import com.habittracker.domain.model.StreakSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class ComputeStreakUseCase(
    private val habitLogRepository: HabitLogRepository,
    private val habitRepository: HabitRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun observeRange(userId: String, range: DateRange): Flow<StreakRangeResult> =
        habitLogRepository.observeActiveLogsBetween(
            userId = userId,
            startInclusive = range.start.atStartOfDayIn(timeZone),
            endExclusive = range.endExclusive.atStartOfDayIn(timeZone),
        ).map { logs -> buildRangeResult(userId, range, logs, firstLogDateFor(userId)) }

    fun observeCurrent(userId: String): Flow<StreakSummary> =
        habitLogRepository.observeAllActiveLogsForUser(userId).map { logs ->
            // Re-derive firstLog from the live log stream rather than a snapshot at flow
            // creation. Previous impl exited early when the user had 0 logs, so the first
            // habit log never triggered a re-emit and the streak counter stayed at 0 even
            // after the heatmap turned green.
            val first = logs.minByOrNull { it.loggedAt }?.loggedAt?.toLocalDate()
            if (first == null) {
                StreakSummary(0, 0, 0, null)
            } else {
                val today = todayLocal()
                val habits = habitRepository.getHabitsForUser(userId)
                summarize(first, today, logs, habits)
            }
        }

    suspend fun computeNow(userId: String, range: DateRange): StreakRangeResult {
        val firstLog = firstLogDateFor(userId)
        val logs = habitLogRepository.observeActiveLogsBetween(
            userId = userId,
            startInclusive = range.start.atStartOfDayIn(timeZone),
            endExclusive = range.endExclusive.atStartOfDayIn(timeZone),
        ).first()
        return buildRangeResult(userId, range, logs, firstLog)
    }

    suspend fun computeSummaryNow(userId: String): StreakSummary {
        val first = habitLogRepository.firstActiveLogAt(userId)?.toLocalDate()
            ?: return StreakSummary(0, 0, 0, null)
        val today = todayLocal()
        val logs = habitLogRepository.observeActiveLogsBetween(
            userId = userId,
            startInclusive = first.atStartOfDayIn(timeZone),
            endExclusive = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone),
        ).first()
        val habits = habitRepository.getHabitsForUser(userId)
        return summarize(first, today, logs, habits)
    }

    // ----- internals -----

    private suspend fun firstLogDateFor(userId: String): LocalDate? =
        habitLogRepository.firstActiveLogAt(userId)?.toLocalDate()

    private suspend fun buildRangeResult(
        userId: String,
        range: DateRange,
        logs: List<HabitLog>,
        firstLogDate: LocalDate?,
    ): StreakRangeResult {
        val today = todayLocal()
        val habits = habitRepository.getHabitsForUser(userId)
        val habitCount = habits.size
        val pastLogs = logs.filter { it.loggedAt <= now() } // ignore future-dated
        // Strict streak (option A): a day counts as COMPLETE only when ALL habits met
        // their dailyTarget that day. Partial-log days do NOT continue the streak.
        val completeDays: Set<LocalDate> = if (habitCount == 0) emptySet() else
            pastLogs.map { it.loggedAt.toLocalDate() }.toSet()
                .filter { allHabitsMetTargetOnDay(pastLogs, habits, it) }
                .toSet()
        // Walk from streak anchor (firstLogDate, or range.start if no anchor) up through today
        // to know the carrying state when range.start is reached. We then emit only days inside the range.
        val anchor = firstLogDate ?: range.start
        val walkStart = minOf(anchor, range.start)
        val walkEnd = maxOf(today, range.endExclusive.minusOneDay())
        val perDay = mutableMapOf<LocalDate, StreakDayState>()
        var prev: StreakDayState? = null
        var cursor = walkStart
        while (cursor <= walkEnd) {
            val state = when {
                cursor < anchor -> StreakDayState.EMPTY
                cursor > today -> StreakDayState.FUTURE // future days inside requested range render as FUTURE
                cursor in completeDays -> StreakDayState.COMPLETE
                cursor == today -> StreakDayState.TODAY_PENDING
                prev == StreakDayState.COMPLETE -> StreakDayState.FROZEN
                prev == StreakDayState.FROZEN -> StreakDayState.BROKEN
                prev == StreakDayState.BROKEN -> StreakDayState.BROKEN
                prev == StreakDayState.TODAY_PENDING -> StreakDayState.FROZEN
                else -> StreakDayState.EMPTY
            }
            perDay[cursor] = state
            prev = state
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        // Pre-compute target sum + per-(day,habit) points for heat bucketing.
        // Uses CURRENT habit set as denominator. Stale-heat when habits change is deferred
        // until Habit CRUD ships (per-log target snapshot column or habit-history rows).
        val targetSum = habits.sumOf { it.dailyTarget }.coerceAtLeast(1)
        val habitsById = habits.associateBy { it.id }
        val rawByDayHabit = mutableMapOf<Pair<LocalDate, String>, Int>()
        pastLogs.forEach { log ->
            val date = log.loggedAt.toLocalDate()
            val habit = habitsById[log.habitId] ?: return@forEach
            val pts = PointCalculator.pointsEarned(log.quantity, habit.thresholdPerPoint)
            val key = date to log.habitId
            rawByDayHabit[key] = (rawByDayHabit[key] ?: 0) + pts
        }
        val pointsByDay = mutableMapOf<LocalDate, Int>()
        rawByDayHabit.forEach { (key, pts) ->
            val (date, habitId) = key
            val target = habitsById[habitId]?.dailyTarget ?: return@forEach
            pointsByDay[date] = (pointsByDay[date] ?: 0) + pts.coerceAtMost(target)
        }

        val days = mutableListOf<StreakDay>()
        var d = range.start
        while (d < range.endExclusive) {
            val state = perDay[d] ?: StreakDayState.EMPTY
            val allLogged = d in completeDays
            val heat = if (state == StreakDayState.FUTURE || habitCount == 0) 0
                       else bucketFor(pointsByDay[d] ?: 0, allLogged, habitCount, targetSum)
            days += StreakDay(d, state, heat)
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return StreakRangeResult(days = days, firstLogDate = firstLogDate)
    }

    /**
     * Heat bucket anchored at domain concepts:
     * - 0: no logs OR partial day (not all habits earned ≥1 point)
     * - 1: bare minimum (all habits earned ≥1, low effort beyond)
     * - 2-3: mid range (thirds between bare min and full target)
     * - 4: full target met (every habit reached its dailyTarget)
     *
     * Degenerate case (all habits dailyTarget=1, so bareMin == full): only bucket 0 or 4.
     */
    private fun bucketFor(pointsCapped: Int, allLogged: Boolean, bareMin: Int, full: Int): Int {
        if (pointsCapped == 0 || !allLogged) return 0
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

    /** Strict streak rule: every habit must have at least one log on [date].
     *  (Quantity / dailyTarget irrelevant — just presence per habit.) */
    private fun allHabitsMetTargetOnDay(
        logs: List<HabitLog>,
        habits: List<Habit>,
        date: LocalDate,
    ): Boolean {
        if (habits.isEmpty()) return false
        val loggedHabitIds = logs
            .filter { sameLocalDate(date, it.loggedAt) }
            .map { it.habitId }
            .toSet()
        return habits.all { it.id in loggedHabitIds }
    }

    private fun sameLocalDate(date: LocalDate, instant: Instant): Boolean =
        instant.toLocalDate() == date

    private fun summarize(
        firstLog: LocalDate,
        today: LocalDate,
        logs: List<HabitLog>,
        habits: List<Habit>,
    ): StreakSummary {
        val pastLogs = logs.filter { it.loggedAt <= now() }
        // Strict streak: only days where ALL habits met dailyTarget count as COMPLETE.
        val completeDays: Set<LocalDate> = if (habits.isEmpty()) emptySet() else
            pastLogs.map { it.loggedAt.toLocalDate() }.toSet()
                .filter { allHabitsMetTargetOnDay(pastLogs, habits, it) }
                .toSet()
        var longest = 0
        var run = 0
        var totalComplete = 0
        var prev: StreakDayState? = null
        var cursor = firstLog
        while (cursor <= today) {
            val state = when {
                cursor in completeDays -> StreakDayState.COMPLETE
                cursor == today -> StreakDayState.TODAY_PENDING
                prev == StreakDayState.COMPLETE -> StreakDayState.FROZEN
                prev == StreakDayState.FROZEN -> StreakDayState.BROKEN
                prev == StreakDayState.BROKEN -> StreakDayState.BROKEN
                else -> StreakDayState.EMPTY
            }
            when (state) {
                StreakDayState.COMPLETE -> {
                    run += 1
                    totalComplete += 1
                    if (run > longest) longest = run
                }
                StreakDayState.FROZEN -> {
                    // streak still alive — do NOT increment, do NOT reset
                }
                StreakDayState.BROKEN -> {
                    run = 0
                }
                StreakDayState.TODAY_PENDING -> {
                    // do nothing — yesterday's streak remains
                }
                StreakDayState.EMPTY -> Unit
                StreakDayState.FUTURE -> Unit // never reached in summarize (cursor <= today)
            }
            prev = state
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        // currentStreak = number of consecutive COMPLETE days ending at today (or yesterday if today pending).
        val current = run
        return StreakSummary(
            currentStreak = current,
            longestStreak = longest,
            totalDaysComplete = totalComplete,
            firstLogDate = firstLog,
        )
    }

    private fun now(): Instant = clock.now()

    private fun todayLocal(): LocalDate = clock.now().toLocalDateTime(timeZone).date

    private fun Instant.toLocalDate(): LocalDate = toLocalDateTime(timeZone).date

    private fun LocalDate.minusOneDay(): LocalDate = this.plus(-1, DateTimeUnit.DAY)

}
