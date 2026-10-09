package com.jktdeveloper.habitto.ui.identity

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habittracker.data.local.SeedData
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.usecase.AddIdentityWithHabitsUseCase
import com.habittracker.domain.usecase.GetHabitTemplatesForIdentitiesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class AddIdentityViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val reader = SeedData.identities.first { it.name == "Reader" }
    private val identityRepo = TestIdentityRepository(seed = SeedData.identities)
    private val habitRepo = TestHabitRepository()

    @Before fun setup() { Dispatchers.setMain(testDispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private fun makeVm(): AddIdentityViewModel {
        val templates = GetHabitTemplatesForIdentitiesUseCase()
        return AddIdentityViewModel(
            identityRepo = identityRepo,
            habitRepo = habitRepo,
            templates = templates,
            addUseCase = AddIdentityWithHabitsUseCase(habitRepo, identityRepo, templates),
            userIdProvider = { "u1" },
        )
    }

    @Test fun commit_addsTheIdentity_andOpensNoHabitForm() = runTest(testDispatcher) {
        val vm = makeVm()
        val done = mutableListOf<AddIdentityDone>()
        backgroundScope.launch { vm.commitSuccess.collect { done += it } }
        vm.selectIdentity(reader)
        vm.advanceToStep2()
        advanceUntilIdle()

        vm.commit()
        advanceUntilIdle()

        assertEquals(listOf(AddIdentityDone(reader.id, openHabitForm = false)), done)
        assertEquals(listOf(reader.id), identityRepo.observeUserIdentities("u1").first().map { it.id })
    }

    @Test fun defineCustomHabit_savesTheIdentityAndCheckedHabits_thenOpensTheHabitForm() = runTest(testDispatcher) {
        // The habit form links a habit only to an identity that the user has. So the identity is
        // saved first, and the form opens with it.
        val vm = makeVm()
        val done = mutableListOf<AddIdentityDone>()
        backgroundScope.launch { vm.commitSuccess.collect { done += it } }
        vm.selectIdentity(reader)
        vm.advanceToStep2()
        advanceUntilIdle()
        val checked = vm.state.value.recommendedHabits.count { it.checked }
        assertTrue("Reader has no recommended habits in the seed", checked > 0)

        vm.defineCustomHabit()
        advanceUntilIdle()

        assertEquals(listOf(AddIdentityDone(reader.id, openHabitForm = true)), done)
        assertEquals(listOf(reader.id), identityRepo.observeUserIdentities("u1").first().map { it.id })
        assertEquals(checked, habitRepo.habits.value.size)
    }
}

private class TestHabitRepository : HabitRepository {
    val habits = MutableStateFlow<List<Habit>>(emptyList())

    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> =
        habits.map { list -> list.filter { it.userId == userId } }
    override suspend fun getHabitsForUser(userId: String): List<Habit> = habits.value.filter { it.userId == userId }
    override suspend fun saveHabit(habit: Habit) {
        habits.value = habits.value.filterNot { it.id == habit.id } + habit
    }
    override suspend fun deleteHabit(habitId: String, userId: String) = Unit
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = Unit
    override suspend fun clearForUser(userId: String) = Unit
    override suspend fun getUnsyncedFor(userId: String): List<Habit> = emptyList()
    override suspend fun markSynced(id: String, syncedAt: Instant) = Unit
    override suspend fun getByIdsForUser(userId: String, ids: List<String>): List<Habit> = emptyList()
    override suspend fun mergePulled(row: Habit) = Unit
    override suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant) = Unit
}
