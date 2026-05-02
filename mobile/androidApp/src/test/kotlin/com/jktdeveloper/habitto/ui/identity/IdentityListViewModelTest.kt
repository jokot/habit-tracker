package com.jktdeveloper.habitto.ui.identity

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.ComputeIdentityStatsUseCase
import com.habittracker.domain.usecase.ObserveUserIdentitiesWithStatsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class IdentityListViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(testDispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun emitsLoadedListAfterCollect() = runTest(testDispatcher) {
        val repo = LocalFakeIdentityRepository(seed = listOf(Identity("a", "A", "", "")))
        repo.setUserIdentities("u1", setOf("a"))
        val statsUc = ComputeIdentityStatsUseCase(EmptyLogRepoForListVm(), repo)
        val aggregate = ObserveUserIdentitiesWithStatsUseCase(repo, statsUc)
        val vm = IdentityListViewModel.forTest(aggregate, identityRepo = repo, userIdProvider = { "u1" })
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(1, (state as? IdentityListState.Loaded)?.items?.size)
    }
}

private class LocalFakeIdentityRepository(
    private val seed: List<Identity> = emptyList(),
) : IdentityRepository {
    private val seedFlow = MutableStateFlow(seed)
    private val userIdentities = MutableStateFlow<List<UserIdentityRow>>(emptyList())
    private val habitIdentities = MutableStateFlow<List<HabitIdentityRow>>(emptyList())

    override suspend fun getAllIdentities(): List<Identity> = seedFlow.value
    override suspend fun upsertIdentities(identities: List<Identity>) {
        seedFlow.value = (seedFlow.value.associateBy { it.id } + identities.associateBy { it.id }).values.toList()
    }
    override fun observeUserIdentities(userId: String): Flow<List<Identity>> =
        combine(userIdentities, seedFlow) { rows, seeds ->
            val map = seeds.associateBy { it.id }
            rows.filter { it.userId == userId }.sortedBy { it.addedAt }.mapNotNull { map[it.identityId] }
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
    override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) = error("unused")
    override suspend fun clearHabitIdentitiesForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedHabitIdentitiesFor(userId: String) = error("unused")
    override suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) = error("unused")
    override fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>> = flowOf(emptyList())
    override suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean) = Unit
    override suspend fun clearPinForUser(userId: String) = Unit
    override suspend fun updateWhyText(userId: String, identityId: String, whyText: String?) = Unit
    override suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant) = Unit
    override suspend fun setPinAtomically(userId: String, identityId: String) = Unit
    override suspend fun getPinnedIdentityIdForUser(userId: String): String? = null
    override suspend fun getUserIdentityRow(userId: String, identityId: String): UserIdentityRow? = null
}

private class EmptyLogRepoForListVm : HabitLogRepository {
    override fun observeActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Flow<List<HabitLog>> = flowOf(emptyList())
    override suspend fun countActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant) = 0
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
