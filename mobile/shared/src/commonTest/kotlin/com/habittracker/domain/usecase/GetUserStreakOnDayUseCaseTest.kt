package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
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
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetUserStreakOnDayUseCaseTest {
    private val tz = TimeZone.UTC
    private val userId = "u1"

    @Test
    fun `empty logs return 0 for any date`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val sut = makeSut(today, habits = emptyList(), logs = emptyList())
        assertEquals(0, sut.execute(userId, today))
        assertEquals(0, sut.execute(userId, today.minus(10, DateTimeUnit.DAY)))
    }

    @Test
    fun `5 consecutive complete days ending today returns 5 querying today`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val habits = listOf(makeHabit("h1"))
        val logs = (4 downTo 0).map { offset ->
            log("h1", today.minus(offset, DateTimeUnit.DAY))
        }
        val sut = makeSut(today, habits, logs)
        assertEquals(5, sut.execute(userId, today))
    }

    @Test
    fun `5 consecutive complete days ending today returns 4 querying yesterday`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val habits = listOf(makeHabit("h1"))
        val logs = (4 downTo 0).map { offset ->
            log("h1", today.minus(offset, DateTimeUnit.DAY))
        }
        val sut = makeSut(today, habits, logs)
        assertEquals(4, sut.execute(userId, today.minus(1, DateTimeUnit.DAY)))
    }

    @Test
    fun `gap in middle resets streak count`() = runTest {
        // Mon-Wed COMPLETE, Thu missed, Fri-Sat COMPLETE → query Sat → 2; query Wed → 3
        val sat = LocalDate(2026, 5, 9)
        val mon = sat.minus(5, DateTimeUnit.DAY)
        val habits = listOf(makeHabit("h1"))
        val days = listOf(
            mon,
            mon.plus(1, DateTimeUnit.DAY),
            mon.plus(2, DateTimeUnit.DAY),
            mon.plus(4, DateTimeUnit.DAY),
            mon.plus(5, DateTimeUnit.DAY),
        )
        val logs = days.map { log("h1", it) }
        val sut = makeSut(today = sat, habits, logs)
        assertEquals(2, sut.execute(userId, sat))
        assertEquals(3, sut.execute(userId, mon.plus(2, DateTimeUnit.DAY)))
    }

    @Test
    fun `today not complete returns 0 even if yesterday was`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val habits = listOf(makeHabit("h1"))
        val logs = listOf(log("h1", today.minus(1, DateTimeUnit.DAY)))
        val sut = makeSut(today, habits, logs)
        assertEquals(0, sut.execute(userId, today))
        assertEquals(1, sut.execute(userId, today.minus(1, DateTimeUnit.DAY)))
    }

    private suspend fun makeSut(
        today: LocalDate,
        habits: List<Habit>,
        logs: List<HabitLog>,
    ): GetUserStreakOnDayUseCase {
        val now = LocalDateTime(today, LocalTime(12, 0)).toInstant(tz)
        val clock = object : Clock { override fun now(): Instant = now }
        val habitRepo = StreakDayHabitRepoFake()
        habits.forEach { habitRepo.saveHabit(it) }
        val logRepo = StreakDayHabitLogRepoFake(tz)
        logs.forEach { logRepo.insertLog(it.id, it.userId, it.habitId, it.quantity, it.loggedAt) }
        val streak = ComputeStreakUseCase(logRepo, habitRepo, tz, clock)
        return GetUserStreakOnDayUseCase(streak, tz)
    }

    private fun makeHabit(id: String) = Habit(
        id = id,
        userId = userId,
        templateId = null,
        name = id,
        unit = "x",
        thresholdPerPoint = 1.0,
        dailyTarget = 1,
        createdAt = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz),
        updatedAt = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz),
        effectiveFrom = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz),
    )

    private fun log(habitId: String, date: LocalDate) = HabitLog(
        id = "log-$habitId-$date",
        userId = userId,
        habitId = habitId,
        quantity = 1.0,
        loggedAt = LocalDateTime(date, LocalTime(10, 0)).toInstant(tz),
        deletedAt = null,
        syncedAt = null,
    )
}

// File-private fakes mirror those in ComputeStreakUseCaseTest (kept inline because the
// originals are file-private to that test).

private class StreakDayHabitLogRepoFake(
    private val tz: TimeZone,
) : HabitLogRepository {
    private val logs = mutableListOf<HabitLog>()

    override suspend fun insertLog(
        id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant,
    ): HabitLog {
        val log = HabitLog(
            id = id, userId = userId, habitId = habitId,
            quantity = quantity, loggedAt = loggedAt,
            deletedAt = null, syncedAt = null,
        )
        logs += log
        return log
    }

    override fun observeActiveLogsBetween(
        userId: String, startInclusive: Instant, endExclusive: Instant,
    ): Flow<List<HabitLog>> = flowOf(
        logs.filter {
            it.userId == userId && it.deletedAt == null &&
                it.loggedAt >= startInclusive && it.loggedAt < endExclusive
        }.sortedBy { it.loggedAt }
    )

    override suspend fun countActiveLogsBetween(
        userId: String, startInclusive: Instant, endExclusive: Instant,
    ): Int = logs.count {
        it.userId == userId && it.deletedAt == null &&
            it.loggedAt >= startInclusive && it.loggedAt < endExclusive
    }

    override suspend fun firstActiveLogAt(userId: String): Instant? =
        logs.filter { it.userId == userId && it.deletedAt == null }
            .minByOrNull { it.loggedAt }?.loggedAt

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

private class StreakDayHabitRepoFake : HabitRepository {
    private val habits = mutableListOf<Habit>()

    override suspend fun saveHabit(habit: Habit) {
        habits.removeAll { it.id == habit.id }
        habits += habit
    }

    override suspend fun getHabitsForUser(userId: String): List<Habit> =
        habits.filter { it.userId == userId }

    override fun observeHabitsForUser(userId: String) = error("unused")
    override suspend fun deleteHabit(habitId: String, userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun getByIdsForUser(userId: String, ids: List<String>) = error("unused")
    override suspend fun mergePulled(row: Habit) = error("unused")
    override suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant) = error("unused")
}
