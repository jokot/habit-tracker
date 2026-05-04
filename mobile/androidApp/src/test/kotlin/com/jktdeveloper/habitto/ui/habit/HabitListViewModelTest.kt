package com.jktdeveloper.habitto.ui.habit

import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val userId = "u1"
    private val habitRepo = FakeHabitRepoForList()
    private val identityRepo = FakeIdentityRepoForList(
        seed = listOf(
            Identity(id = "athlete", name = "Athlete", description = "", icon = ""),
            Identity(id = "reader", name = "Reader", description = "", icon = ""),
        ),
    )

    @Before fun setUp() { kotlinx.coroutines.Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { kotlinx.coroutines.Dispatchers.resetMain() }

    @Test
    fun `loads habits sorted alphabetically with identity names`() = runTest {
        val now = Clock.System.now()
        habitRepo.saveHabit(Habit(
            id = "h2", userId = userId, templateId = "t", name = "Run",
            unit = "min", thresholdPerPoint = 1.0, dailyTarget = 1,
            createdAt = now, updatedAt = now, effectiveFrom = now,
        ))
        habitRepo.saveHabit(Habit(
            id = "h1", userId = userId, templateId = "t", name = "Read",
            unit = "p", thresholdPerPoint = 1.0, dailyTarget = 1,
            createdAt = now, updatedAt = now, effectiveFrom = now,
        ))
        identityRepo.linkHabitToIdentities("h2", setOf("athlete"))
        identityRepo.linkHabitToIdentities("h1", setOf("reader"))
        identityRepo.seedUserIdentity(userId, "athlete")
        identityRepo.seedUserIdentity(userId, "reader")
        // Seed habits into identity repo so getHabitIdentityLinksForUser can resolve by userId
        identityRepo.seedHabit(habitRepo.habits.first { it.id == "h2" })
        identityRepo.seedHabit(habitRepo.habits.first { it.id == "h1" })

        val vm = HabitListViewModel.forTest(habitRepo, identityRepo) { userId }
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("Expected Loaded but was $state", state is HabitListState.Loaded)
        val loaded = state as HabitListState.Loaded
        assertEquals(2, loaded.habits.size)
        assertEquals("Read", loaded.habits[0].habit.name)
        assertEquals(listOf("Reader"), loaded.habits[0].identityNames)
        assertEquals("Run", loaded.habits[1].habit.name)
        assertEquals(listOf("Athlete"), loaded.habits[1].identityNames)
    }

    @Test
    fun `filters out habits with effectiveTo set`() = runTest {
        val now = Clock.System.now()
        habitRepo.saveHabit(Habit(
            id = "h1", userId = userId, templateId = "t", name = "Active",
            unit = "p", thresholdPerPoint = 1.0, dailyTarget = 1,
            createdAt = now, updatedAt = now, effectiveFrom = now, effectiveTo = null,
        ))
        habitRepo.saveHabit(Habit(
            id = "h2", userId = userId, templateId = "t", name = "Deleted",
            unit = "p", thresholdPerPoint = 1.0, dailyTarget = 1,
            createdAt = now, updatedAt = now, effectiveFrom = now, effectiveTo = now,
        ))

        val vm = HabitListViewModel.forTest(habitRepo, identityRepo) { userId }
        advanceUntilIdle()

        val loaded = vm.state.value as HabitListState.Loaded
        assertEquals(1, loaded.habits.size)
        assertEquals("Active", loaded.habits.first().habit.name)
    }

    @Test
    fun `empty user produces Loaded with empty list`() = runTest {
        val vm = HabitListViewModel.forTest(habitRepo, identityRepo) { userId }
        advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state is HabitListState.Loaded && state.habits.isEmpty())
    }
}

// ─── Local fakes (commonTest Fakes are not on androidApp test classpath) ─────

private class FakeHabitRepoForList : HabitRepository {
    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: List<Habit> get() = _habits.value

    override suspend fun getHabitsForUser(userId: String) = _habits.value.filter { it.userId == userId }
    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> =
        _habits.map { list -> list.filter { it.userId == userId } }
    override suspend fun saveHabit(habit: Habit) {
        _habits.value = _habits.value.filterNot { it.id == habit.id } + habit
    }
    override suspend fun deleteHabit(habitId: String, userId: String) {
        _habits.value = _habits.value.filterNot { it.id == habitId && it.userId == userId }
    }
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) { _habits.value = _habits.value.filterNot { it.userId == userId } }
    override suspend fun getUnsyncedFor(userId: String) = _habits.value.filter { it.userId == userId && it.syncedAt == null }
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun getByIdsForUser(userId: String, ids: List<String>) = _habits.value.filter { it.userId == userId && it.id in ids }
    override suspend fun mergePulled(row: Habit) { _habits.value = _habits.value.filterNot { it.id == row.id } + row }
    override suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant) = error("unused")
}

private class FakeIdentityRepoForList(
    private val seed: List<Identity> = emptyList(),
) : IdentityRepository {
    private val seedFlow = MutableStateFlow(seed)
    private val userIdentities = MutableStateFlow<List<UserIdentityRow>>(emptyList())
    private val habitIdentities = MutableStateFlow<List<HabitIdentityRow>>(emptyList())
    private val habits = MutableStateFlow<List<Habit>>(emptyList())

    fun seedHabit(habit: Habit) {
        habits.value = habits.value.filterNot { it.id == habit.id } + habit
    }

    fun seedUserIdentity(
        userId: String,
        identityId: String,
        isPinned: Boolean = false,
        whyText: String? = null,
        removedAt: Instant? = null,
    ) {
        val row = UserIdentityRow(
            userId = userId,
            identityId = identityId,
            addedAt = Clock.System.now(),
            syncedAt = null,
            isPinned = isPinned,
            whyText = whyText,
            removedAt = removedAt,
        )
        userIdentities.value = userIdentities.value
            .filterNot { it.userId == userId && it.identityId == identityId } + row
    }

    override suspend fun getAllIdentities(): List<Identity> = seedFlow.value
    override suspend fun upsertIdentities(identities: List<Identity>) {
        seedFlow.value = (seedFlow.value.associateBy { it.id } + identities.associateBy { it.id }).values.toList()
    }
    override fun observeUserIdentities(userId: String): Flow<List<Identity>> =
        combine(userIdentities, seedFlow) { rows, seeds ->
            val map = seeds.associateBy { it.id }
            rows.filter { it.userId == userId && it.removedAt == null }
                .sortedWith(compareByDescending<UserIdentityRow> { it.isPinned }.thenBy { it.addedAt })
                .mapNotNull { map[it.identityId] }
        }
    override suspend fun setUserIdentities(userId: String, identityIds: Set<String>) {
        val now = Clock.System.now()
        val existing = userIdentities.value.filter { it.userId == userId }
        val keep = existing.filter { it.identityId in identityIds }
        val add = (identityIds - keep.map { it.identityId }.toSet()).map {
            UserIdentityRow(userId = userId, identityId = it, addedAt = now, syncedAt = null)
        }
        val others = userIdentities.value.filter { it.userId != userId }
        userIdentities.value = others + keep + add
    }
    override suspend fun clearUserIdentitiesForUser(userId: String) {
        userIdentities.value = userIdentities.value.filter { it.userId != userId }
    }
    override suspend fun getUnsyncedUserIdentitiesFor(userId: String) =
        userIdentities.value.filter { it.userId == userId && it.syncedAt == null }
    override suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant) {
        userIdentities.value = userIdentities.value.map {
            if (it.userId == userId && it.identityId == identityId) it.copy(syncedAt = syncedAt) else it
        }
    }
    override suspend fun mergePulledUserIdentity(row: UserIdentityRow) {
        userIdentities.value = userIdentities.value.filterNot { it.userId == row.userId && it.identityId == row.identityId } + row
    }
    override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {
        val now = Clock.System.now()
        val keep = habitIdentities.value.filterNot { it.habitId == habitId && it.identityId !in identityIds }
        val existingIds = keep.filter { it.habitId == habitId }.map { it.identityId }.toSet()
        val add = (identityIds - existingIds).map {
            HabitIdentityRow(habitId = habitId, identityId = it, addedAt = now, syncedAt = null, effectiveFrom = now)
        }
        habitIdentities.value = keep + add
    }
    override suspend fun clearHabitIdentitiesForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedHabitIdentitiesFor(userId: String) = error("unused")
    override suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) = error("unused")
    override fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>> =
        combine(habits, habitIdentities) { hs, his ->
            val habitIds = his.filter { it.identityId == identityId }.map { it.habitId }.toSet()
            hs.filter { it.userId == userId && it.id in habitIds }
        }
    override suspend fun getHabitIdentityLinksForUser(userId: String): List<HabitIdentityRow> {
        val userHabitIds = habits.value.filter { it.userId == userId }.map { it.id }.toSet()
        return habitIdentities.value.filter { it.habitId in userHabitIds }
    }
    override suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean) = Unit
    override suspend fun clearPinForUser(userId: String) = Unit
    override suspend fun updateWhyText(userId: String, identityId: String, whyText: String?) = Unit
    override suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant) = Unit
    override suspend fun setPinAtomically(userId: String, identityId: String) = Unit
    override suspend fun getPinnedIdentityIdForUser(userId: String): String? = null
    override suspend fun getUserIdentityRow(userId: String, identityId: String): UserIdentityRow? = null
    override suspend fun markHabitIdentityRemoved(habitId: String, identityId: String, effectiveTo: Instant) {
        habitIdentities.value = habitIdentities.value.map {
            if (it.habitId == habitId && it.identityId == identityId) it.copy(effectiveTo = effectiveTo) else it
        }
    }
}
