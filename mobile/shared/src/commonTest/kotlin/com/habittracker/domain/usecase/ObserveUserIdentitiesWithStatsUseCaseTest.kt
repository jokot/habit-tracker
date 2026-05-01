package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

class ObserveUserIdentitiesWithStatsUseCaseTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 5, 1)
    private val userId = "u1"

    @Test
    fun emptyUserIdentitiesEmitsEmpty() = runTest {
        val repo = FakeIdentityRepository(seed = listOf(Identity("a", "A", "", "")))
        val stats = ComputeIdentityStatsUseCase(EmptyLogRepo(), repo, tz, ObserveAggregateFixedClock(today))
        val sut = ObserveUserIdentitiesWithStatsUseCase(repo, stats)
        assertEquals(emptyList(), sut.execute(userId).first())
    }

    @Test
    fun emitsOneEntryPerUserIdentityWithStats() = runTest {
        val seedA = Identity("a", "A", "", "")
        val seedB = Identity("b", "B", "", "")
        val repo = FakeIdentityRepository(seed = listOf(seedA, seedB))
        repo.setUserIdentities(userId, setOf("a", "b"))
        val stats = ComputeIdentityStatsUseCase(EmptyLogRepo(), repo, tz, ObserveAggregateFixedClock(today))
        val sut = ObserveUserIdentitiesWithStatsUseCase(repo, stats)
        val out = sut.execute(userId).first()
        assertEquals(2, out.size)
        assertEquals(setOf("a", "b"), out.map { it.identity.id }.toSet())
    }

    private class ObserveAggregateFixedClock(private val date: LocalDate) : Clock {
        override fun now(): Instant =
            LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.UTC)
    }

    private class EmptyLogRepo : HabitLogRepository {
        override fun observeActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Flow<List<HabitLog>> = flowOf(emptyList())
        override suspend fun countActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Int = 0
        override suspend fun firstActiveLogAt(userId: String): Instant? = null
        override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant) = error("unused")
        override suspend fun softDelete(logId: String, userId: String) = error("unused")
        override fun observeActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
        override suspend fun getActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
        override fun observeAllActiveLogsForUser(userId: String): Flow<List<HabitLog>> = flowOf(emptyList())
        override suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog> = emptyList()
        override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
        override suspend fun clearForUser(userId: String) = error("unused")
        override suspend fun getUnsyncedFor(userId: String) = error("unused")
        override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
        override suspend fun mergePulled(row: HabitLog) = error("unused")
    }
}
