package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

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
