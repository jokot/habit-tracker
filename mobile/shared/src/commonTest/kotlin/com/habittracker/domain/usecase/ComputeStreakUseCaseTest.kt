package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.StreakDayState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComputeStreakUseCaseTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 4, 26)
    private val userId = "u1"

    private fun instantAt(date: LocalDate, hour: Int = 12): Instant =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz)

    private fun fakeRepo(logs: List<HabitLog>): HabitLogRepository =
        InMemoryHabitLogRepo(logs, tz)

    private fun makeUseCase(
        logs: List<HabitLog>,
        currentDay: LocalDate = today,
        habitCount: Int = 1,
    ): ComputeStreakUseCase = ComputeStreakUseCase(
        habitLogRepository = fakeRepo(logs),
        habitRepository = FixedCountHabitRepo(habitCount, userId),
        timeZone = tz,
        clock = FixedClock(instantAt(currentDay, hour = 12)),
    )

    private fun log(date: LocalDate, habitId: String = "h1"): HabitLog = HabitLog(
        id = "log-${date}-${habitId}",
        userId = userId,
        habitId = habitId,
        quantity = 1.0,
        loggedAt = instantAt(date),
        deletedAt = null,
        syncedAt = null,
    )

    @Test
    fun `empty logs — all EMPTY, summary zeros, firstLogDate null`() = runTest {
        val uc = makeUseCase(logs = emptyList())
        val range = DateRange(today.minusDays(4), today.plusDays(1))
        val result = uc.computeNow(userId, range)

        assertEquals(5, result.days.size)
        assertTrue(result.days.dropLast(1).all { it.state == StreakDayState.EMPTY })
        assertEquals(StreakDayState.TODAY_PENDING, result.days.last().state)
        assertNull(result.firstLogDate)

        val summary = uc.computeSummaryNow(userId)
        assertEquals(0, summary.currentStreak)
        assertEquals(0, summary.longestStreak)
        assertEquals(0, summary.totalDaysComplete)
        assertNull(summary.firstLogDate)
    }

    @Test
    fun `single log today — streak 1, today COMPLETE, longest 1`() = runTest {
        val uc = makeUseCase(logs = listOf(log(today)))
        val summary = uc.computeSummaryNow(userId)
        assertEquals(1, summary.currentStreak)
        assertEquals(1, summary.longestStreak)
        assertEquals(1, summary.totalDaysComplete)
        assertEquals(today, summary.firstLogDate)
    }

    @Test
    fun `5 consecutive days incl today — streak 5`() = runTest {
        val logs = (0..4).map { log(today.minusDays(it)) }
        val uc = makeUseCase(logs)
        val summary = uc.computeSummaryNow(userId)
        assertEquals(5, summary.currentStreak)
        assertEquals(5, summary.longestStreak)
        assertEquals(5, summary.totalDaysComplete)
    }

    @Test
    fun `1-day gap mid-streak — that day FROZEN, streak continues through`() = runTest {
        // logs: today, today-1, [gap today-2], today-3, today-4
        val logs = listOf(today, today.minusDays(1), today.minusDays(3), today.minusDays(4))
            .map { log(it) }
        val uc = makeUseCase(logs)
        val range = DateRange(today.minusDays(4), today.plusDays(1))
        val result = uc.computeNow(userId, range)

        val byDate = result.days.associateBy { it.date }
        assertEquals(StreakDayState.COMPLETE, byDate[today.minusDays(4)]?.state)
        assertEquals(StreakDayState.COMPLETE, byDate[today.minusDays(3)]?.state)
        assertEquals(StreakDayState.FROZEN, byDate[today.minusDays(2)]?.state)
        assertEquals(StreakDayState.COMPLETE, byDate[today.minusDays(1)]?.state)
        assertEquals(StreakDayState.COMPLETE, byDate[today]?.state)

        val summary = uc.computeSummaryNow(userId)
        assertEquals(4, summary.currentStreak) // -4,-3,-1,today all COMPLETE; -2 FROZEN doesn't break
    }

    @Test
    fun `2-day consecutive gap — 1st FROZEN, 2nd BROKEN, streak resets`() = runTest {
        // logs: today, [gap today-1, today-2], today-3, today-4
        val logs = listOf(today, today.minusDays(3), today.minusDays(4)).map { log(it) }
        val uc = makeUseCase(logs)
        val range = DateRange(today.minusDays(4), today.plusDays(1))
        val result = uc.computeNow(userId, range)
        val byDate = result.days.associateBy { it.date }

        assertEquals(StreakDayState.COMPLETE, byDate[today.minusDays(4)]?.state)
        assertEquals(StreakDayState.COMPLETE, byDate[today.minusDays(3)]?.state)
        assertEquals(StreakDayState.FROZEN, byDate[today.minusDays(2)]?.state)
        assertEquals(StreakDayState.BROKEN, byDate[today.minusDays(1)]?.state)
        assertEquals(StreakDayState.COMPLETE, byDate[today]?.state)

        val summary = uc.computeSummaryNow(userId)
        assertEquals(1, summary.currentStreak) // reset, only today counts
        assertEquals(2, summary.longestStreak) // -3,-4 stretch
    }

    @Test
    fun `chain of 3+ misses — all subsequent days BROKEN`() = runTest {
        val logs = listOf(today.minusDays(5)).map { log(it) }
        val uc = makeUseCase(logs)
        val range = DateRange(today.minusDays(5), today.plusDays(1))
        val result = uc.computeNow(userId, range)
        val states = result.days.map { it.state }
        // -5: COMPLETE, -4: FROZEN, -3..-1: BROKEN, today: TODAY_PENDING
        assertEquals(StreakDayState.COMPLETE, states[0])
        assertEquals(StreakDayState.FROZEN, states[1])
        assertEquals(StreakDayState.BROKEN, states[2])
        assertEquals(StreakDayState.BROKEN, states[3])
        assertEquals(StreakDayState.BROKEN, states[4])
        assertEquals(StreakDayState.TODAY_PENDING, states[5])
    }

    @Test
    fun `today no log + yesterday COMPLETE — today TODAY_PENDING, current streak unchanged`() = runTest {
        val logs = (1..3).map { log(today.minusDays(it)) }
        val uc = makeUseCase(logs)
        val range = DateRange(today.minusDays(3), today.plusDays(1))
        val result = uc.computeNow(userId, range)
        assertEquals(StreakDayState.TODAY_PENDING, result.days.last().state)

        val summary = uc.computeSummaryNow(userId)
        assertEquals(3, summary.currentStreak) // streak survives until tomorrow without log
    }

    @Test
    fun `today no log + yesterday FROZEN — today TODAY_PENDING (don't pre-emptively break)`() = runTest {
        // -3 complete, -2 missed (FROZEN), -1 complete, today empty
        val logs = listOf(today.minusDays(3), today.minusDays(1)).map { log(it) }
        val uc = makeUseCase(logs)
        val range = DateRange(today.minusDays(3), today.plusDays(1))
        val result = uc.computeNow(userId, range)
        assertEquals(StreakDayState.TODAY_PENDING, result.days.last().state)
    }

    @Test
    fun `future-dated log ignored`() = runTest {
        val logs = listOf(log(today), log(today.plusDays(2)))
        val uc = makeUseCase(logs)
        val summary = uc.computeSummaryNow(userId)
        assertEquals(1, summary.totalDaysComplete)
        assertEquals(today, summary.firstLogDate)
    }

    @Test
    fun `multiple logs same day — counted once`() = runTest {
        val logs = listOf(
            log(today, "h1"),
            log(today, "h2"),
            log(today, "h3"),
        )
        val uc = makeUseCase(logs)
        val summary = uc.computeSummaryNow(userId)
        assertEquals(1, summary.totalDaysComplete)
        assertEquals(1, summary.currentStreak)
    }

    @Test
    fun `cross-month boundary — correct`() = runTest {
        val anchor = LocalDate(2026, 3, 30)
        val current = LocalDate(2026, 4, 2) // April 2
        val logs = listOf(anchor, anchor.plusDays(1), anchor.plusDays(2), current)
            .map { log(it) }
        val uc = ComputeStreakUseCase(
            habitLogRepository = fakeRepo(logs),
            habitRepository = FixedCountHabitRepo(1, userId),
            timeZone = tz,
            clock = FixedClock(instantAt(current)),
        )
        val summary = uc.computeSummaryNow(userId)
        assertEquals(4, summary.currentStreak)
    }

    @Test
    fun `cross-year boundary — correct`() = runTest {
        val anchor = LocalDate(2025, 12, 30)
        val current = LocalDate(2026, 1, 2)
        val logs = listOf(anchor, anchor.plusDays(1), anchor.plusDays(2), current)
            .map { log(it) }
        val uc = ComputeStreakUseCase(
            habitLogRepository = fakeRepo(logs),
            habitRepository = FixedCountHabitRepo(1, userId),
            timeZone = tz,
            clock = FixedClock(instantAt(current)),
        )
        val summary = uc.computeSummaryNow(userId)
        assertEquals(4, summary.currentStreak)
    }

    @Test
    fun `longestStreak is at least currentStreak — invariant`() = runTest {
        val logs = (0..6).map { log(today.minusDays(it)) }
        val uc = makeUseCase(logs)
        val summary = uc.computeSummaryNow(userId)
        assertTrue(summary.longestStreak >= summary.currentStreak)
        assertEquals(7, summary.currentStreak)
        assertEquals(7, summary.longestStreak)
    }

    @Test
    fun `broken then resumed — current counts only resumed run`() = runTest {
        // -7,-6,-5 complete, -4,-3 missed (FROZEN, BROKEN), -2,-1, today complete
        val logs = listOf(-7, -6, -5, -2, -1, 0).map { log(today.plusDays(it.toLong())) }
        val uc = makeUseCase(logs)
        val summary = uc.computeSummaryNow(userId)
        assertEquals(3, summary.currentStreak)
        assertEquals(3, summary.longestStreak) // tied
    }

    @Test
    fun `heatLevel 0 when no logs in range`() = runTest {
        val uc = makeUseCase(logs = emptyList(), habitCount = 2)
        val range = DateRange(today.minusDays(2), today.plusDays(1))
        val result = uc.computeNow(userId, range)
        // All past days with no logs should have heatLevel 0
        result.days.dropLast(1).forEach { day ->
            assertEquals(0, day.heatLevel, "Expected heatLevel 0 for ${day.date}")
        }
    }

    @Test
    fun `heatLevel anchored at streak rule — partial days bucket 0, full day bucket 4`() = runTest {
        // 4 habits, each dailyTarget=1, threshold=1. Strict bucket formula:
        //   bareMin = habitCount = 4, full = targetSum = 4, span = 0 → only buckets 0 or 4.
        //   Partial day (some habits logged, not all) → bucket 0 (below streak floor).
        //   Full day (all 4 logged) → bucket 4 (target met).
        val d1 = today.minusDays(4)
        val d2 = today.minusDays(3)
        val d3 = today.minusDays(2)
        val d4 = today.minusDays(1)
        val logs = listOf(
            log(d1, "h1"),
            log(d2, "h1"), log(d2, "h2"),
            log(d3, "h1"), log(d3, "h2"), log(d3, "h3"),
            log(d4, "h1"), log(d4, "h2"), log(d4, "h3"), log(d4, "h4"),
        )
        val uc = makeUseCase(logs = logs, habitCount = 4)
        val range = DateRange(d1, today.plusDays(1))
        val result = uc.computeNow(userId, range)
        val byDate = result.days.associateBy { it.date }
        assertEquals(0, byDate[d1]?.heatLevel) // 1/4 logged → partial → bucket 0
        assertEquals(0, byDate[d2]?.heatLevel) // 2/4 logged → partial → bucket 0
        assertEquals(0, byDate[d3]?.heatLevel) // 3/4 logged → partial → bucket 0
        assertEquals(4, byDate[d4]?.heatLevel) // 4/4 logged → full → bucket 4
    }

    @Test
    fun `FUTURE state for days after today`() = runTest {
        val uc = makeUseCase(logs = listOf(log(today)), habitCount = 1)
        val range = DateRange(today, today.plusDays(3))
        val result = uc.computeNow(userId, range)
        val byDate = result.days.associateBy { it.date }
        assertEquals(StreakDayState.COMPLETE, byDate[today]?.state)
        assertEquals(StreakDayState.FUTURE, byDate[today.plusDays(1)]?.state)
        assertEquals(StreakDayState.FUTURE, byDate[today.plusDays(2)]?.state)
        // FUTURE cells have heatLevel 0
        assertEquals(0, byDate[today.plusDays(1)]?.heatLevel)
        assertEquals(0, byDate[today.plusDays(2)]?.heatLevel)
    }

    @AfterTest
    fun teardown() = Unit
}

// --- helpers below ---

private class FixedClock(private val now: Instant) : Clock {
    override fun now(): Instant = now
}

private class InMemoryHabitLogRepo(
    private val logs: List<HabitLog>,
    private val tz: TimeZone,
) : HabitLogRepository {
    override fun observeActiveLogsBetween(
        userId: String,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<HabitLog>> = flowOf(
        logs.filter {
            it.userId == userId &&
                it.deletedAt == null &&
                it.loggedAt >= startInclusive &&
                it.loggedAt < endExclusive
        }.sortedBy { it.loggedAt }
    )

    override suspend fun countActiveLogsBetween(
        userId: String,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Int = logs.count {
        it.userId == userId &&
            it.deletedAt == null &&
            it.loggedAt >= startInclusive &&
            it.loggedAt < endExclusive
    }

    override suspend fun firstActiveLogAt(userId: String): Instant? = logs
        .filter { it.userId == userId && it.deletedAt == null }
        .minByOrNull { it.loggedAt }
        ?.loggedAt

    // Unused in these tests — throw to make sure they aren't called.
    override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant) =
        error("unused")
    override suspend fun softDelete(logId: String, userId: String) = error("unused")
    override fun observeActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) =
        error("unused")
    override suspend fun getActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) =
        error("unused")
    override fun observeAllActiveLogsForUser(userId: String) = error("unused")
    override suspend fun getAllActiveLogsForUser(userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulled(row: HabitLog) = error("unused")
}

/**
 * HabitRepository stub that returns [count] dummy habits for [userId] and
 * throws for everything else (nothing else is called by ComputeStreakUseCase).
 */
private class FixedCountHabitRepo(
    private val count: Int,
    private val ownerId: String,
) : com.habittracker.data.repository.HabitRepository {
    private val dummyInstant = kotlinx.datetime.Instant.fromEpochSeconds(0)
    private fun dummyHabits() = List(count) { i ->
        com.habittracker.domain.model.Habit(
            id = "h${i + 1}",
            userId = ownerId,
            templateId = "t${i + 1}",
            name = "Habit ${i + 1}",
            unit = "times",
            thresholdPerPoint = 1.0,
            dailyTarget = 1,
            createdAt = dummyInstant,
            updatedAt = dummyInstant,
        )
    }

    override suspend fun getHabitsForUser(userId: String): List<com.habittracker.domain.model.Habit> =
        if (userId == ownerId) dummyHabits() else emptyList()

    override fun observeHabitsForUser(userId: String) = error("unused")
    override suspend fun saveHabit(habit: com.habittracker.domain.model.Habit) = error("unused")
    override suspend fun deleteHabit(habitId: String, userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: kotlinx.datetime.Instant) = error("unused")
    override suspend fun getByIdsForUser(userId: String, ids: List<String>) = error("unused")
    override suspend fun mergePulled(row: com.habittracker.domain.model.Habit) = error("unused")
    override suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: kotlinx.datetime.Instant) = error("unused")
}

private fun LocalDate.minusDays(d: Int): LocalDate =
    this.plus(-d, DateTimeUnit.DAY)

private fun LocalDate.minusDays(d: Long): LocalDate =
    this.plus(-d.toInt(), DateTimeUnit.DAY)

private fun LocalDate.plusDays(d: Int): LocalDate =
    this.plus(d, DateTimeUnit.DAY)

private fun LocalDate.plusDays(d: Long): LocalDate =
    this.plus(d.toInt(), DateTimeUnit.DAY)
