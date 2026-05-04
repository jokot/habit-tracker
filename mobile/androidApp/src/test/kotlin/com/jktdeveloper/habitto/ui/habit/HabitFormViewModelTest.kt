package com.jktdeveloper.habitto.ui.habit

import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.domain.usecase.SaveHabitUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitFormViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `create mode starts with empty fields and identityId pre-fill if provided`() = runTest {
        val (vm, _, _, _) = makeVm(habitId = null, prefillIdentityId = "ix")
        val state = vm.state.first()
        assertEquals("", state.name)
        assertEquals(setOf("ix"), state.selectedIdentityIds)
        assertFalse(state.canSave) // empty name etc.
        assertEquals(HabitFormMode.Create, state.mode)
    }

    @Test
    fun `edit mode loads existing habit fields`() = runTest {
        val identityRow = makeIdentity("ix")
        val habits = StubHabitRepo(
            initial = listOf(
                Habit(
                    id = "h1", userId = "u1", templateId = "tpl", name = "Read",
                    unit = "min", thresholdPerPoint = 10.0, dailyTarget = 2,
                    createdAt = Instant.fromEpochSeconds(0),
                    updatedAt = Instant.fromEpochSeconds(0),
                )
            )
        )
        val identities = StubIdentityRepo(
            links = listOf(makeLink("h1", "ix", effectiveTo = null)),
            userIdentities = listOf(identityRow),
        )
        val (vm, _, _, _) = makeVm(habitId = "h1", habits = habits, identities = identities)
        val state = vm.state.first { it.mode == HabitFormMode.Edit }
        assertEquals("Read", state.name)
        assertEquals("min", state.unit)
        assertEquals(10.0, state.threshold, 0.0)
        assertEquals(2, state.target)
        assertEquals(setOf("ix"), state.selectedIdentityIds)
        assertTrue(state.canSave)
    }

    @Test
    fun `canSave false when name empty`() = runTest {
        val (vm, _, _, _) = makeVm(habitId = null)
        vm.onNameChange("  ")
        vm.onUnitChange("min")
        vm.onThresholdChange(1.0)
        vm.onTargetChange(1)
        vm.onIdentitiesChange(setOf("ix"))
        assertFalse(vm.state.first().canSave)
    }

    @Test
    fun `canSave false when no identities selected`() = runTest {
        val (vm, _, _, _) = makeVm(habitId = null)
        vm.onNameChange("Walk")
        vm.onUnitChange("min")
        vm.onThresholdChange(1.0)
        vm.onTargetChange(1)
        assertFalse(vm.state.first().canSave)
    }

    @Test
    fun `save in create mode dispatches create`() = runTest {
        val (vm, _, _, recorder) = makeVm(habitId = null)
        vm.onNameChange("Walk")
        vm.onUnitChange("min")
        vm.onThresholdChange(15.0)
        vm.onTargetChange(2)
        vm.onIdentitiesChange(setOf("ix"))
        vm.save()
        assertEquals("create", recorder.last)
    }

    @Test
    fun `delete dispatches DeleteHabitUseCase`() = runTest {
        val habits = StubHabitRepo(
            initial = listOf(
                Habit(
                    id = "h1", userId = "u1", templateId = null, name = "n",
                    unit = "u", thresholdPerPoint = 1.0, dailyTarget = 1,
                    createdAt = Instant.fromEpochSeconds(0),
                    updatedAt = Instant.fromEpochSeconds(0),
                )
            )
        )
        val (vm, _, _, recorder) = makeVm(habitId = "h1", habits = habits)
        vm.delete()
        assertEquals("delete", recorder.last)
    }

    private data class Bundle(
        val vm: HabitFormViewModel,
        val habits: StubHabitRepo,
        val identities: StubIdentityRepo,
        val recorder: Recorder,
    )

    private fun makeVm(
        habitId: String?,
        prefillIdentityId: String? = null,
        habits: StubHabitRepo = StubHabitRepo(),
        identities: StubIdentityRepo = StubIdentityRepo(),
    ): Bundle {
        val recorder = Recorder()
        val save = object : SaveHabitUseCase(habits, identities, fixedClock) {
            override suspend fun create(
                userId: String, name: String, unit: String, threshold: Double,
                target: Int, identityIds: Set<String>, templateId: String?,
            ): String { recorder.last = "create"; return "newId" }
            override suspend fun update(
                userId: String, habitId: String, name: String, unit: String,
                threshold: Double, target: Int, newIdentityIds: Set<String>,
            ) { recorder.last = "update" }
        }
        val delete = object : DeleteHabitUseCase(habits, fixedClock) {
            override suspend fun execute(userId: String, habitId: String) { recorder.last = "delete" }
        }
        val vm = HabitFormViewModel(
            habitId = habitId,
            prefillIdentityId = prefillIdentityId,
            userIdProvider = { "u1" },
            saveUseCase = save,
            deleteUseCase = delete,
            habitRepo = habits,
            identityRepo = identities,
            triggerSync = {},
        )
        return Bundle(vm, habits, identities, recorder)
    }

    private val fixedClock = object : Clock { override fun now(): Instant = Instant.fromEpochSeconds(1000) }

    private class Recorder { var last: String? = null }
}

// Helper factory functions for test data — match real data class signatures.
private fun makeIdentity(id: String): Identity = Identity(
    id = id,
    name = "name-$id",
    description = "desc-$id",
    icon = "icon-$id",
)

private fun makeLink(habitId: String, identityId: String, effectiveTo: Instant?): HabitIdentityRow =
    HabitIdentityRow(
        habitId = habitId,
        identityId = identityId,
        addedAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
        syncedAt = null,
        effectiveFrom = null,
        effectiveTo = effectiveTo,
    )

// Minimal stubs (file-local). Real repos exist in commonTest but aren't on the
// androidApp test classpath; inline stubs keep the test self-contained.
private class StubHabitRepo(initial: List<Habit> = emptyList()) : HabitRepository {
    private val data = initial.toMutableList()
    override suspend fun getHabitsForUser(userId: String) = data.filter { it.userId == userId }
    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> = flowOf(data.filter { it.userId == userId })
    override suspend fun saveHabit(habit: Habit) { data.removeAll { it.id == habit.id }; data.add(habit) }
    override suspend fun deleteHabit(habitId: String, userId: String) { data.removeAll { it.id == habitId } }
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {}
    override suspend fun clearForUser(userId: String) { data.removeAll { it.userId == userId } }
    override suspend fun getUnsyncedFor(userId: String) = emptyList<Habit>()
    override suspend fun markSynced(id: String, syncedAt: Instant) {}
    override suspend fun getByIdsForUser(userId: String, ids: List<String>) = data.filter { it.id in ids }
    override suspend fun mergePulled(row: Habit) {}
    override suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant) {}
}

private class StubIdentityRepo(
    val links: List<HabitIdentityRow> = emptyList(),
    val userIdentities: List<Identity> = emptyList(),
) : IdentityRepository {
    override suspend fun getAllIdentities() = emptyList<Identity>()
    override suspend fun upsertIdentities(identities: List<Identity>) {}
    override fun observeUserIdentities(userId: String) = flowOf(userIdentities)
    override suspend fun setUserIdentities(userId: String, identityIds: Set<String>) {}
    override suspend fun clearUserIdentitiesForUser(userId: String) {}
    override suspend fun getUnsyncedUserIdentitiesFor(userId: String) = emptyList<UserIdentityRow>()
    override suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant) {}
    override suspend fun mergePulledUserIdentity(row: UserIdentityRow) {}
    override suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean) {}
    override suspend fun clearPinForUser(userId: String) {}
    override suspend fun updateWhyText(userId: String, identityId: String, whyText: String?) {}
    override suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant) {}
    override suspend fun setPinAtomically(userId: String, identityId: String) {}
    override suspend fun getPinnedIdentityIdForUser(userId: String) = null
    override suspend fun getUserIdentityRow(userId: String, identityId: String) = null
    override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {}
    override suspend fun clearHabitIdentitiesForUser(userId: String) {}
    override suspend fun getUnsyncedHabitIdentitiesFor(userId: String) = emptyList<HabitIdentityRow>()
    override suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant) {}
    override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) {}
    override fun observeHabitsForIdentity(userId: String, identityId: String) = flowOf(emptyList<Habit>())
    override suspend fun getHabitIdentityLinksForUser(userId: String) = links
    override suspend fun markHabitIdentityRemoved(habitId: String, identityId: String, effectiveTo: Instant) {}
}
