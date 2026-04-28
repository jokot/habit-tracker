package com.jktdeveloper.habitto.ui.streak

import android.app.Application
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.usecase.ComputeStreakUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class StreakHistoryViewModelTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 4, 22)
    private val userId = "u1"
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(testDispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `initial load seeds current month + summary`() = runTest(testDispatcher) {
        val logs = listOf(makeLog(today))
        val uc = ComputeStreakUseCase(InMemoryRepo(logs), EmptyHabitRepo(), tz, FixedClock(today))
        val vm = StreakHistoryViewModel(uc, { userId }, tz, FixedClock(today))
        advanceUntilIdle()
        val months = vm.months.value
        assertEquals(1, months.size)
        assertEquals(2026, months.first().year)
        assertEquals(4, months.first().month)
        assertEquals(1, vm.summary.value.currentStreak)
    }

    @Test fun `loadOlderMonth prepends previous month`() = runTest(testDispatcher) {
        val logs = listOf(makeLog(LocalDate(2026, 3, 15)), makeLog(today))
        val uc = ComputeStreakUseCase(InMemoryRepo(logs), EmptyHabitRepo(), tz, FixedClock(today))
        val vm = StreakHistoryViewModel(uc, { userId }, tz, FixedClock(today))
        advanceUntilIdle()
        vm.loadOlderMonth()
        advanceUntilIdle()
        val months = vm.months.value
        assertEquals(2, months.size)
        val older = months.last()
        assertEquals(2026, older.year)
        assertEquals(3, older.month)
    }

    @Test fun `loadOlderMonth stops at firstLogDate`() = runTest(testDispatcher) {
        val logs = listOf(makeLog(LocalDate(2026, 3, 15)), makeLog(today))
        val uc = ComputeStreakUseCase(InMemoryRepo(logs), EmptyHabitRepo(), tz, FixedClock(today))
        val vm = StreakHistoryViewModel(uc, { userId }, tz, FixedClock(today))
        advanceUntilIdle()
        vm.loadOlderMonth()
        advanceUntilIdle()
        val sizeAfterMarch = vm.months.value.size
        vm.loadOlderMonth() // attempts February — blocked
        advanceUntilIdle()
        assertEquals(sizeAfterMarch, vm.months.value.size)
    }

    private fun makeLog(date: LocalDate, habitId: String = "h1"): HabitLog = HabitLog(
        id = "log-$date-$habitId",
        userId = userId,
        habitId = habitId,
        quantity = 1.0,
        loggedAt = LocalDateTime(date, LocalTime(12, 0)).toInstant(tz),
        deletedAt = null,
        syncedAt = null,
    )
}

private class FixedClock(private val date: LocalDate) : Clock {
    override fun now(): Instant = LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.UTC)
}

/** Minimal HabitRepository that returns 0 habits — sufficient for streak-shape tests. */
private class EmptyHabitRepo : HabitRepository {
    override suspend fun getHabitsForUser(userId: String): List<Habit> = emptyList()
    override fun observeHabitsForUser(userId: String) = error("unused")
    override suspend fun saveHabit(habit: Habit) = error("unused")
    override suspend fun deleteHabit(habitId: String, userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun getByIdsForUser(userId: String, ids: List<String>) = error("unused")
    override suspend fun mergePulled(row: Habit) = error("unused")
}

private class InMemoryRepo(private val logs: List<HabitLog>) : HabitLogRepository {
    override fun observeActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Flow<List<HabitLog>> = flowOf(
        logs.filter { it.userId == userId && it.deletedAt == null && it.loggedAt >= startInclusive && it.loggedAt < endExclusive }
    )
    override suspend fun countActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Int =
        logs.count { it.userId == userId && it.deletedAt == null && it.loggedAt >= startInclusive && it.loggedAt < endExclusive }
    override suspend fun firstActiveLogAt(userId: String): Instant? =
        logs.filter { it.userId == userId && it.deletedAt == null }.minByOrNull { it.loggedAt }?.loggedAt
    override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant) = error("unused")
    override suspend fun softDelete(logId: String, userId: String) = error("unused")
    override fun observeActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
    override suspend fun getActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
    override fun observeAllActiveLogsForUser(userId: String): Flow<List<HabitLog>> = flowOf(logs.filter { it.userId == userId && it.deletedAt == null })
    override suspend fun getAllActiveLogsForUser(userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulled(row: HabitLog) = error("unused")
}
