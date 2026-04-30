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
        val days = mutableListOf<StreakDay>()
        var d = range.start
        while (d < range.endExclusive) {
            val state = perDay[d] ?: StreakDayState.EMPTY
            val distinctCount = distinctOnDay(pastLogs, d)
            // Heatmap is a separate "activity" signal — colored by distinct habits
            // logged on the day, regardless of whether the day qualifies as strict-COMPLETE.
            // distinctCount=0 naturally yields heatLevel=0, so days with zero logs are still cold.
            val heat = if (state == StreakDayState.FUTURE) 0
                       else heatBucket(distinctCount, habitCount)
            days += StreakDay(d, state, heat)
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return StreakRangeResult(days = days, firstLogDate = firstLogDate)
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

    private fun heatBucket(distinct: Int, totalHabits: Int): Int {
        if (totalHabits == 0) return 0
        val ratio = distinct.toDouble() / totalHabits
        return when {
            ratio <= 0.0 -> 0
            ratio <= 0.25 -> 1
            ratio <= 0.5 -> 2
            ratio <= 0.75 -> 3
            else -> 4
        }
    }

    private fun distinctOnDay(logs: List<HabitLog>, date: LocalDate): Int =
        logs.filter { sameLocalDate(date, it.loggedAt) }.distinctBy { it.habitId }.size

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
