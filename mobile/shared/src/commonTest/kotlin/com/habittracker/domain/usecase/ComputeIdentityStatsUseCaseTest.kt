package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.habittracker.domain.model.StreakDayState
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ComputeIdentityStatsUseCaseTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 5, 1)
    private val userId = "u1"
    private val identityId = "ident-1"
    private val seedIdentity = Identity(identityId, "Reader", "", "")

    @Test
    fun zeroHabitsReturnsEmpty() = runTest {
        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(emptyList()),
            identityRepo = repo,
            timeZone = tz,
            clock = IdentityStatsFixedClock(today),
        )
        val out = sut.computeNow(userId, identityId)
        assertEquals(0, out.habitCount)
        assertEquals(0, out.currentStreak)
        assertEquals(0, out.daysActive)
        assertEquals(List(14) { 0 }, out.last14Heat)
        assertEquals(List(90) { 0 }, out.last90Heat)
    }

    @Test
    fun singleHabitLoggedTodayYieldsStreakOne() = runTest {
        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        val habit = makeHabit("h1")
        repo.seedHabit(habit)
        repo.linkHabitToIdentities(habit.id, setOf(identityId))
        val logs = listOf(makeLog("h1", today))
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(logs),
            identityRepo = repo,
            timeZone = tz,
            clock = IdentityStatsFixedClock(today),
        )
        val out = sut.computeNow(userId, identityId)
        assertEquals(1, out.habitCount)
        assertEquals(1, out.currentStreak)
        assertEquals(1, out.daysActive)
        assertEquals(4, out.last14Heat.last())
    }

    @Test
    fun twoHabitsPartialDayDoesNotIncrementStreak() = runTest {
        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        val h1 = makeHabit("h1")
        val h2 = makeHabit("h2")
        repo.seedHabit(h1); repo.seedHabit(h2)
        repo.linkHabitToIdentities(h1.id, setOf(identityId))
        repo.linkHabitToIdentities(h2.id, setOf(identityId))
        val logs = listOf(makeLog("h1", today))
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(logs),
            identityRepo = repo,
            timeZone = tz,
            clock = IdentityStatsFixedClock(today),
        )
        val out = sut.computeNow(userId, identityId)
        assertEquals(2, out.habitCount)
        assertEquals(0, out.currentStreak)
        assertEquals(1, out.daysActive)
    }

    @Test
    fun heatBucketBoundaries() = runTest {
        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        val habits = (1..4).map { makeHabit("h$it") }
        habits.forEach {
            repo.seedHabit(it)
            repo.linkHabitToIdentities(it.id, setOf(identityId))
        }
        // Partial day (only 2 of 4 habits logged) → bucket 0 (below streak floor)
        val logs = listOf(makeLog("h1", today), makeLog("h2", today))
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(logs),
            identityRepo = repo,
            timeZone = tz,
            clock = IdentityStatsFixedClock(today),
        )
        val out = sut.computeNow(userId, identityId)
        assertEquals(0, out.last14Heat.last())
    }

    @Test
    fun allHabitsLoggedYieldsBucketFourWhenAllTargetsOne() = runTest {
        // 4 habits, each dailyTarget=1. bareMin == full == 4.
        // All logged once → pointsCapped = 4, pointsCapped >= full → bucket 4.
        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        val habits = (1..4).map { makeHabit("h$it") }
        habits.forEach {
            repo.seedHabit(it)
            repo.linkHabitToIdentities(it.id, setOf(identityId))
        }
        val logs = habits.map { makeLog(it.id, today) }
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(logs),
            identityRepo = repo,
            timeZone = tz,
            clock = IdentityStatsFixedClock(today),
        )
        val out = sut.computeNow(userId, identityId)
        assertEquals(4, out.last14Heat.last())
    }

    @Test
    fun last14And90LengthsExact() = runTest {
        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(emptyList()),
            identityRepo = repo,
            timeZone = tz,
            clock = IdentityStatsFixedClock(today),
        )
        val out = sut.computeNow(userId, identityId)
        assertEquals(14, out.last14Heat.size)
        assertEquals(90, out.last90Heat.size)
    }

    @Test
    fun `habit added mid-day is not required for that day's complete check`() = runTest {
        // today's dayStart = 2026-05-01T00:00Z; newHabit.effectiveFrom = 2026-05-01T14:00Z
        // so newHabit is NOT active at dayStart → only oldHabit is required today.
        val todayDate = LocalDate(2026, 5, 1)
        val testTz = TimeZone.UTC
        val todayDayStart = todayDate.atStartOfDayIn(testTz)
        val newHabitEffectiveFrom = todayDayStart.plus(14, DateTimeUnit.HOUR, testTz)
        val fiveDaysAgoInstant = todayDate.minus(5, DateTimeUnit.DAY).atStartOfDayIn(testTz)

        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        val oldHabit = makeHabit("h1").copy(effectiveFrom = fiveDaysAgoInstant)
        val newHabit = makeHabit("h2").copy(effectiveFrom = newHabitEffectiveFrom)
        repo.seedHabit(oldHabit)
        repo.seedHabit(newHabit)
        repo.linkHabitToIdentities(oldHabit.id, setOf(identityId))
        repo.linkHabitToIdentities(newHabit.id, setOf(identityId))

        // Only oldHabit is logged today
        val logs = listOf(makeLog("h1", todayDate))
        val fakeClock = object : Clock {
            override fun now(): Instant = newHabitEffectiveFrom // clock at 14:00 today
        }
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(logs),
            identityRepo = repo,
            timeZone = testTz,
            clock = fakeClock,
        )
        val stats = sut.computeNow(userId, identityId)

        // today's last14States entry should NOT be BROKEN — newHabit isn't active at dayStart
        // so today is COMPLETE (oldHabit logged) rather than TODAY_PENDING/BROKEN.
        assertNotEquals(StreakDayState.BROKEN, stats.last14States.last())
        assertNotEquals(StreakDayState.BROKEN, stats.last90States.last())
        // More precisely: today should be COMPLETE since the only active-at-dayStart habit was logged
        assertEquals(StreakDayState.COMPLETE, stats.last14States.last())
    }

    private fun makeHabit(id: String, dailyTarget: Int = 1, threshold: Double = 1.0) =
        Habit(
            id = id, userId = userId, templateId = "t-$id", name = id, unit = "x",
            thresholdPerPoint = threshold, dailyTarget = dailyTarget,
            createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
        )

    private fun makeLog(habitId: String, date: LocalDate, qty: Double = 1.0) = HabitLog(
        id = "log-$habitId-$date",
        userId = userId,
        habitId = habitId,
        quantity = qty,
        loggedAt = LocalDateTime(date, LocalTime(12, 0)).toInstant(tz),
        deletedAt = null,
        syncedAt = null,
    )
}

private class IdentityStatsFixedClock(private val date: LocalDate) : Clock {
    override fun now(): Instant =
        LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.UTC)
}

private class AllLogsRepo(private val logs: List<HabitLog>) : HabitLogRepository {
    override fun observeActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Flow<List<HabitLog>> = flowOf(emptyList())
    override suspend fun countActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Int = 0
    override suspend fun firstActiveLogAt(userId: String): Instant? = null
    override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant) = error("unused")
    override suspend fun softDelete(logId: String, userId: String) = error("unused")
    override fun observeActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
    override suspend fun getActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
    override fun observeAllActiveLogsForUser(userId: String): Flow<List<HabitLog>> = flowOf(logs.filter { it.userId == userId && it.deletedAt == null })
    override suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog> = logs.filter { it.userId == userId && it.deletedAt == null }
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulled(row: HabitLog) = error("unused")
}
