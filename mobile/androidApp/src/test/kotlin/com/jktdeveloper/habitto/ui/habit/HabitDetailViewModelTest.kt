package com.jktdeveloper.habitto.ui.habit

import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.ComputePerHabitStreakUseCase
import com.habittracker.data.repository.HabitLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val userId = "u1"

    @Before fun setUp() { kotlinx.coroutines.Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { kotlinx.coroutines.Dispatchers.resetMain() }

    @Test
    fun `unknown habitId emits NotFound`() = runTest {
        val habitRepo = FakeHabitRepoForDetail(emptyList())
        val identityRepo = FakeIdentityRepoForDetail(seed = emptyList())
        val logRepo = FakeHabitLogRepoForDetail(emptyList())
        val useCase = ComputePerHabitStreakUseCase(logRepo, habitRepo, TimeZone.UTC, Clock.System)

        val vm = HabitDetailViewModel.forTest(habitRepo, identityRepo, useCase, { userId }, "unknown")
        advanceUntilIdle()
        assertTrue(vm.state.value is HabitDetailState.NotFound)
    }

    @Test
    fun `Loaded combines habit + identityNames + streak`() = runTest {
        val now = Clock.System.now()
        val habit = Habit(
            id = "h1", userId = userId, templateId = "t", name = "Run",
            unit = "min", thresholdPerPoint = 1.0, dailyTarget = 1,
            createdAt = now, updatedAt = now, effectiveFrom = now,
        )
        val athlete = Identity(id = "athlete", name = "Athlete", description = "", icon = "")
        val link = HabitIdentityRow(habitId = "h1", identityId = "athlete", addedAt = now, syncedAt = null, effectiveFrom = now)
        val habitRepo = FakeHabitRepoForDetail(listOf(habit))
        val identityRepo = FakeIdentityRepoForDetail(seed = listOf(athlete), userIdentities = listOf(athlete.id), links = listOf(link))
        val logRepo = FakeHabitLogRepoForDetail(emptyList())
        val useCase = ComputePerHabitStreakUseCase(logRepo, habitRepo, TimeZone.UTC, Clock.System)

        val vm = HabitDetailViewModel.forTest(habitRepo, identityRepo, useCase, { userId }, "h1")
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("Expected Loaded, got $state", state is HabitDetailState.Loaded)
        val loaded = state as HabitDetailState.Loaded
        assertEquals("Run", loaded.habit.name)
        assertEquals(listOf("Athlete"), loaded.identityNames)
        assertEquals("h1", loaded.streak.habitId)
        assertEquals(0, loaded.streak.totalLogs)
    }
}

// ── Inline fakes (commonTest fakes aren't on androidApp classpath) ────

private class FakeHabitRepoForDetail(initial: List<Habit>) : HabitRepository {
    private val _habits = MutableStateFlow(initial)
    override suspend fun getHabitsForUser(userId: String): List<Habit> =
        _habits.value.filter { it.userId == userId }
    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> =
        _habits.map { list -> list.filter { it.userId == userId } }
    override suspend fun saveHabit(habit: Habit) {
        _habits.value = _habits.value.filterNot { it.id == habit.id } + habit
    }
    override suspend fun deleteHabit(habitId: String, userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String): List<Habit> = emptyList()
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun getByIdsForUser(userId: String, ids: List<String>): List<Habit> = error("unused")
    override suspend fun mergePulled(row: Habit) = error("unused")
    override suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant) = error("unused")
}

private class FakeHabitLogRepoForDetail(initial: List<HabitLog>) : HabitLogRepository {
    private val _logs = MutableStateFlow(initial)
    override fun observeAllActiveLogsForUser(userId: String): Flow<List<HabitLog>> =
        _logs.map { list -> list.filter { it.userId == userId } }
    override fun observeActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Flow<List<HabitLog>> = flowOf(emptyList())
    override suspend fun countActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Int = 0
    override suspend fun firstActiveLogAt(userId: String): Instant? = null
    override suspend fun getActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant): List<HabitLog> = emptyList()
    override fun observeActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant): Flow<List<HabitLog>> = flowOf(emptyList())
    override suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog> = _logs.value.filter { it.userId == userId }
    override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant): HabitLog = error("unused")
    override suspend fun softDelete(logId: String, userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String): List<HabitLog> = emptyList()
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulled(row: HabitLog) = error("unused")
}

private class FakeIdentityRepoForDetail(
    private val seed: List<Identity>,
    private val userIdentities: List<String> = emptyList(),
    private val links: List<HabitIdentityRow> = emptyList(),
) : IdentityRepository {
    override suspend fun getAllIdentities(): List<Identity> = seed
    override suspend fun upsertIdentities(identities: List<Identity>) = error("unused")
    override fun observeUserIdentities(userId: String): Flow<List<Identity>> {
        val active = seed.filter { it.id in userIdentities }
        return flowOf(active)
    }
    override suspend fun setUserIdentities(userId: String, identityIds: Set<String>) = error("unused")
    override suspend fun clearUserIdentitiesForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedUserIdentitiesFor(userId: String): List<UserIdentityRow> = emptyList()
    override suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulledUserIdentity(row: UserIdentityRow) = error("unused")
    override suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean) = error("unused")
    override suspend fun clearPinForUser(userId: String) = error("unused")
    override suspend fun updateWhyText(userId: String, identityId: String, whyText: String?) = error("unused")
    override suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant) = error("unused")
    override suspend fun setPinAtomically(userId: String, identityId: String) = error("unused")
    override suspend fun getPinnedIdentityIdForUser(userId: String): String? = null
    override suspend fun getUserIdentityRow(userId: String, identityId: String): UserIdentityRow? = null
    override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) = error("unused")
    override suspend fun clearHabitIdentitiesForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedHabitIdentitiesFor(userId: String): List<HabitIdentityRow> = emptyList()
    override suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) = error("unused")
    override fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>> = flowOf(emptyList())
    override suspend fun getHabitIdentityLinksForUser(userId: String): List<HabitIdentityRow> = links
}
