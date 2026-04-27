package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.habittracker.domain.model.StreakRangeResult
import com.habittracker.domain.model.StreakSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun observeRange(userId: String, range: DateRange): Flow<StreakRangeResult> =
        habitLogRepository.observeActiveLogsBetween(
            userId = userId,
            startInclusive = range.start.atStartOfDayIn(timeZone),
            endExclusive = range.endExclusive.atStartOfDayIn(timeZone),
        ).map { logs -> buildRangeResult(range, logs, firstLogDateFor(userId)) }

    fun observeCurrent(userId: String): Flow<StreakSummary> = flow {
        val first = habitLogRepository.firstActiveLogAt(userId)?.toLocalDate()
        if (first == null) {
            emit(StreakSummary(0, 0, 0, null))
            return@flow
        }
        val today = todayLocal()
        habitLogRepository.observeActiveLogsBetween(
            userId = userId,
            startInclusive = first.atStartOfDayIn(timeZone),
            endExclusive = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone),
        ).collect { logs ->
            emit(summarize(first, today, logs))
        }
    }

    suspend fun computeNow(userId: String, range: DateRange): StreakRangeResult {
        val firstLog = firstLogDateFor(userId)
        val logs = firstFromFlow(
            habitLogRepository.observeActiveLogsBetween(
                userId = userId,
                startInclusive = range.start.atStartOfDayIn(timeZone),
                endExclusive = range.endExclusive.atStartOfDayIn(timeZone),
            )
        )
        return buildRangeResult(range, logs, firstLog)
    }

    suspend fun computeSummaryNow(userId: String): StreakSummary {
        val first = habitLogRepository.firstActiveLogAt(userId)?.toLocalDate()
            ?: return StreakSummary(0, 0, 0, null)
        val today = todayLocal()
        val logs = firstFromFlow(
            habitLogRepository.observeActiveLogsBetween(
                userId = userId,
                startInclusive = first.atStartOfDayIn(timeZone),
                endExclusive = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone),
            )
        )
        return summarize(first, today, logs)
    }

    // ----- internals -----

    private suspend fun firstLogDateFor(userId: String): LocalDate? =
        habitLogRepository.firstActiveLogAt(userId)?.toLocalDate()

    private fun buildRangeResult(
        range: DateRange,
        logs: List<HabitLog>,
        firstLogDate: LocalDate?,
    ): StreakRangeResult {
        val today = todayLocal()
        // Set of dates with at least one log inside the range.
        val daysWithLog: Set<LocalDate> = logs
            .filter { it.loggedAt <= now() } // ignore future-dated
            .map { it.loggedAt.toLocalDate() }
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
                cursor > today -> StreakDayState.EMPTY // future days inside requested range render as EMPTY
                cursor in daysWithLog -> StreakDayState.COMPLETE
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
            days += StreakDay(d, perDay[d] ?: StreakDayState.EMPTY)
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return StreakRangeResult(days = days, firstLogDate = firstLogDate)
    }

    private fun summarize(firstLog: LocalDate, today: LocalDate, logs: List<HabitLog>): StreakSummary {
        val daysWithLog = logs
            .filter { it.loggedAt <= now() }
            .map { it.loggedAt.toLocalDate() }
            .toSet()
        var longest = 0
        var run = 0
        var totalComplete = 0
        var prev: StreakDayState? = null
        var cursor = firstLog
        while (cursor <= today) {
            val state = when {
                cursor in daysWithLog -> StreakDayState.COMPLETE
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
                    // streak still alive visually — but reset consecutive run counter
                    run = 0
                }
                StreakDayState.BROKEN -> {
                    run = 0
                }
                StreakDayState.TODAY_PENDING -> {
                    // do nothing — yesterday's streak remains
                }
                StreakDayState.EMPTY -> Unit
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

    /** Drain the first emission of a Flow into a list (for the synchronous compute helpers). */
    private suspend fun firstFromFlow(flow: Flow<List<HabitLog>>): List<HabitLog> {
        var result: List<HabitLog> = emptyList()
        flow.collect { result = it; return@collect }
        return result
    }
}
