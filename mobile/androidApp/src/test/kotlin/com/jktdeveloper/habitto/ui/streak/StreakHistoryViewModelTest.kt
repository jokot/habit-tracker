package com.jktdeveloper.habitto.ui.streak

import android.app.Application
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.sync.SyncState
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.TodaySection
import com.habittracker.domain.usecase.ComputeStreakUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val dayPoints = makeDayPointsUseCase(logs)
        val vm = viewModel(uc, dayPoints)
        advanceUntilIdle()
        val months = vm.months.value
        assertEquals(1, months.size)
        assertEquals(2026, months.first().year)
        assertEquals(4, months.first().month)
        assertEquals(1, vm.summary.value?.currentStreak)
    }

    @Test fun `loadOlderMonth prepends previous month`() = runTest(testDispatcher) {
        val logs = listOf(makeLog(LocalDate(2026, 3, 15)), makeLog(today))
        val uc = ComputeStreakUseCase(InMemoryRepo(logs), EmptyHabitRepo(), tz, FixedClock(today))
        val dayPoints = makeDayPointsUseCase(logs)
        val vm = viewModel(uc, dayPoints)
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
        val dayPoints = makeDayPointsUseCase(logs)
        val vm = viewModel(uc, dayPoints)
        advanceUntilIdle()
        vm.loadOlderMonth()
        advanceUntilIdle()
        val sizeAfterMarch = vm.months.value.size
        vm.loadOlderMonth() // attempts February — blocked
        advanceUntilIdle()
        assertEquals(sizeAfterMarch, vm.months.value.size)
    }

    @Test fun `a new log updates the summary and the month without a reload`() = runTest(testDispatcher) {
        val logs = MutableStateFlow(listOf(makeLog(LocalDate(2026, 4, 21))))
        val uc = ComputeStreakUseCase(InMemoryRepo(logs), EmptyHabitRepo(), tz, FixedClock(today))
        val vm = viewModel(uc, makeDayPointsUseCase(logs.value))
        advanceUntilIdle()
        val todayBefore = vm.months.value.first().days.first { it.date == today }.state
        assertEquals(1, vm.summary.value?.currentStreak)

        logs.value = logs.value + makeLog(today)
        advanceUntilIdle()

        assertEquals(2, vm.summary.value?.currentStreak)
        assertNotEquals(todayBefore, vm.months.value.first().days.first { it.date == today }.state)
    }

    @Test fun `a habit edit updates the summary without a reload`() = runTest(testDispatcher) {
        val logs = listOf(makeLog(today))
        val habits = EmptyHabitRepo()
        val uc = ComputeStreakUseCase(InMemoryRepo(logs), habits, tz, FixedClock(today))
        val vm = viewModel(uc, makeDayPointsUseCase(logs))
        advanceUntilIdle()
        assertEquals(1, vm.summary.value?.currentStreak)

        // A new habit with no log today makes today incomplete.
        habits.habits.value = habits.habits.value + habits.habits.value.first().copy(id = "h2", name = "H2")
        advanceUntilIdle()

        assertEquals(0, vm.summary.value?.currentStreak)
    }

    @Test fun `history is not ready until the habit logs are pulled`() = runTest(testDispatcher) {
        val logs = listOf(makeLog(today))
        val uc = ComputeStreakUseCase(InMemoryRepo(logs), EmptyHabitRepo(), tz, FixedClock(today))
        val ready = MutableStateFlow(TodaySection.entries.toSet() - TodaySection.STREAK)
        val sync = MutableStateFlow<SyncState>(SyncState.Idle)
        val vm = viewModel(uc, makeDayPointsUseCase(logs), ready, sync)
        advanceUntilIdle()
        assertFalse(vm.historyReady.value)
        assertFalse(vm.loadFailed.value)

        sync.value = SyncState.Error("offline", Instant.fromEpochSeconds(0))
        advanceUntilIdle()
        assertTrue(vm.loadFailed.value)

        ready.value = TodaySection.entries.toSet()
        advanceUntilIdle()
        assertTrue(vm.historyReady.value)
        assertFalse(vm.loadFailed.value)
    }

    @Test fun `loadOlderMonth waits for the first summary`() = runTest(testDispatcher) {
        val logs = listOf(makeLog(LocalDate(2026, 3, 15)), makeLog(today))
        val uc = ComputeStreakUseCase(InMemoryRepo(logs), EmptyHabitRepo(), tz, FixedClock(today))
        // No day yet, so nothing is computed.
        val vm = viewModel(uc, makeDayPointsUseCase(logs), days = emptyFlow())
        advanceUntilIdle()
        assertNull(vm.summary.value)
        vm.loadOlderMonth()
        advanceUntilIdle()
        assertEquals(1, vm.months.value.size)
    }

    private fun viewModel(
        uc: ComputeStreakUseCase,
        dayPoints: com.habittracker.domain.usecase.GetDayPointsUseCase,
        readySections: MutableStateFlow<Set<TodaySection>> = MutableStateFlow(TodaySection.entries.toSet()),
        syncState: MutableStateFlow<SyncState> = MutableStateFlow(SyncState.Idle),
        days: Flow<LocalDate> = flowOf(today),
    ) = StreakHistoryViewModel(
        uc, dayPoints, { userId }, tz, FixedClock(today),
        readySections = readySections,
        syncState = syncState,
        days = days,
        computeDispatcher = testDispatcher,
    )

    private fun makeDayPointsUseCase(logs: List<HabitLog>) =
        com.habittracker.domain.usecase.GetDayPointsUseCase(
            habitLogRepo = AllLogsHabitLogRepo(logs),
            wantLogRepo = EmptyWantLogRepo(),
            habitRepo = EmptyHabitRepo(),
            wantActivityRepo = EmptyWantActivityRepo(),
            timeZone = tz,
        )

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

/** Minimal HabitRepository that returns ONE habit (id=h1, dailyTarget=1, threshold=1.0)
 *  so a single log of quantity 1 meets the daily target — strict streak fires. */
private class EmptyHabitRepo : HabitRepository {
    private val instant = Instant.fromEpochSeconds(0)
    val habits = MutableStateFlow(
        listOf(
            Habit(
                id = "h1", userId = "u1", templateId = "t1", name = "H1", unit = "x",
                thresholdPerPoint = 1.0, dailyTarget = 1,
                createdAt = instant, updatedAt = instant,
            ),
        ),
    )
    override suspend fun getHabitsForUser(userId: String): List<Habit> = habits.value
    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> = habits
    override suspend fun saveHabit(habit: Habit) = error("unused")
    override suspend fun deleteHabit(habitId: String, userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun getByIdsForUser(userId: String, ids: List<String>) = error("unused")
    override suspend fun mergePulled(row: Habit) = error("unused")
    override suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant) = error("unused")
}

/** HabitLogRepository fake that ALSO returns logs for getAllActiveLogsForUser
 *  (used by GetDayPointsUseCase). Different from InMemoryRepo which throws. */
private class AllLogsHabitLogRepo(private val logs: List<HabitLog>) : HabitLogRepository {
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

private class EmptyWantLogRepo : com.habittracker.data.repository.WantLogRepository {
    override suspend fun insertLog(id: String, userId: String, activityId: String, quantity: Double, pointsSpent: Int, deviceMode: com.habittracker.domain.model.DeviceMode, loggedAt: Instant) = error("unused")
    override suspend fun softDelete(logId: String, userId: String) = error("unused")
    override fun observeAllActiveLogsForUser(userId: String): Flow<List<com.habittracker.domain.model.WantLog>> = flowOf(emptyList())
    override suspend fun getAllActiveLogsForUser(userId: String): List<com.habittracker.domain.model.WantLog> = emptyList()
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulled(row: com.habittracker.domain.model.WantLog) = error("unused")
}

private class EmptyWantActivityRepo : com.habittracker.data.repository.WantActivityRepository {
    override fun observeWantActivities(userId: String): Flow<List<com.habittracker.domain.model.WantActivity>> = flowOf(emptyList())
    override suspend fun getWantActivities(userId: String): List<com.habittracker.domain.model.WantActivity> = emptyList()
    override suspend fun getAllWantActivitiesForUser(userId: String): List<com.habittracker.domain.model.WantActivity> = emptyList()
    override suspend fun saveWantActivity(activity: com.habittracker.domain.model.WantActivity, userId: String) = error("unused")
    override suspend fun hideWantActivity(id: String, userId: String, hiddenAt: Instant) = error("unused")
    override suspend fun unhideWantActivity(id: String, userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun getByIdsForUser(userId: String, ids: List<String>) = error("unused")
    override suspend fun mergePulled(row: com.habittracker.domain.model.WantActivity) = error("unused")
}

/** Logs live in a [MutableStateFlow], so a test can add one and watch the screen update. */
private class InMemoryRepo(private val flow: MutableStateFlow<List<HabitLog>>) : HabitLogRepository {
    constructor(logs: List<HabitLog>) : this(MutableStateFlow(logs))
    private val logs get() = flow.value
    override fun observeActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Flow<List<HabitLog>> = flow.map { logs ->
        logs.filter { it.userId == userId && it.deletedAt == null && it.loggedAt >= startInclusive && it.loggedAt < endExclusive }
    }
    override suspend fun countActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Int =
        logs.count { it.userId == userId && it.deletedAt == null && it.loggedAt >= startInclusive && it.loggedAt < endExclusive }
    override suspend fun firstActiveLogAt(userId: String): Instant? =
        logs.filter { it.userId == userId && it.deletedAt == null }.minByOrNull { it.loggedAt }?.loggedAt
    override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant) = error("unused")
    override suspend fun softDelete(logId: String, userId: String) = error("unused")
    override fun observeActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
    override suspend fun getActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
    override fun observeAllActiveLogsForUser(userId: String): Flow<List<HabitLog>> = flow.map { logs -> logs.filter { it.userId == userId && it.deletedAt == null } }
    override suspend fun getAllActiveLogsForUser(userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulled(row: HabitLog) = error("unused")
}
