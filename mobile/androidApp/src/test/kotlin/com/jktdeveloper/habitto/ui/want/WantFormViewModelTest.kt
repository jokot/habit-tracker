package com.jktdeveloper.habitto.ui.want

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.repository.LocalWantActivityRepository
import com.habittracker.data.repository.LocalWantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class WantFormViewModelTest {
    private val userId = "u1"
    private val fixedClock = object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(1_000_000) }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newRepos(): Pair<LocalWantActivityRepository, LocalWantLogRepository> {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val driver = AndroidSqliteDriver(HabitTrackerDatabase.Schema, context, name = null)
        val db = HabitTrackerDatabase(driver)
        return LocalWantActivityRepository(db) to LocalWantLogRepository(db)
    }

    @Test
    fun `new mode saves a custom activity`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        val vm = WantFormViewModel.forTest(FormMode.New, wantRepo, logRepo, { userId }, fixedClock)
        vm.onName("Bingewatch")
        vm.onUnit("episode")
        vm.onCostInput("0.5")
        vm.onIconKey("local_movies")
        var done = false
        vm.save { done = true }
        assertTrue(done)
        val saved = wantRepo.getAllWantActivitiesForUser(userId).single()
        assertEquals("Bingewatch", saved.name)
        assertTrue(saved.isCustom)
        assertEquals("local_movies", saved.iconKey)
        assertEquals(0.5, saved.costPerUnit, 0.0)
    }

    @Test
    fun `edit mode loads existing fields`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        wantRepo.saveWantActivity(
            WantActivity(id = "a", name = "TikTok", unit = "minutes",
                         costPerUnit = 1.0, iconKey = "play_circle"),
            userId,
        )
        val vm = WantFormViewModel.forTest(FormMode.Edit("a"), wantRepo, logRepo, { userId }, fixedClock)
        val loaded = vm.state.first { it.name.isNotEmpty() }
        assertEquals("TikTok", loaded.name)
        assertEquals("play_circle", loaded.iconKey)
        assertEquals("1.0", loaded.costInput)
    }

    @Test
    fun `cost change triggers warning when past logs exist`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        wantRepo.saveWantActivity(
            WantActivity(id = "a", name = "TikTok", unit = "minutes", costPerUnit = 1.0),
            userId,
        )
        logRepo.insertLog(
            id = "l1", userId = userId, activityId = "a",
            quantity = 1.0, deviceMode = DeviceMode.OTHER,
            loggedAt = Instant.fromEpochMilliseconds(900_000),
        )
        val vm = WantFormViewModel.forTest(FormMode.Edit("a"), wantRepo, logRepo, { userId }, fixedClock)
        vm.state.first { it.hasPastLogs }
        vm.onCostInput("2.0")
        val s = vm.state.first { it.showCostEditWarning }
        assertTrue(s.showCostEditWarning)
    }

    @Test
    fun `cost change doesn't warn when no past logs`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        wantRepo.saveWantActivity(
            WantActivity(id = "a", name = "TikTok", unit = "minutes", costPerUnit = 1.0),
            userId,
        )
        val vm = WantFormViewModel.forTest(FormMode.Edit("a"), wantRepo, logRepo, { userId }, fixedClock)
        vm.state.first { !it.hasPastLogs && it.name.isNotEmpty() }
        vm.onCostInput("2.0")
        assertFalse(vm.state.first().showCostEditWarning)
    }

    @Test
    fun `validation rejects empty name and negative cost`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        val vm = WantFormViewModel.forTest(FormMode.New, wantRepo, logRepo, { userId }, fixedClock)
        vm.onName("")
        vm.onCostInput("1.0")
        var done = false
        vm.save { done = true }
        assertFalse(done)
        assertNotNull(vm.state.first().validationError)

        vm.onName("X")
        vm.onCostInput("-1")
        vm.save { done = true }
        assertFalse(done)
    }

    @Test
    fun `delete softHides the activity`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        wantRepo.saveWantActivity(
            WantActivity(id = "a", name = "TikTok", unit = "minutes",
                         costPerUnit = 1.0, isCustom = true),
            userId,
        )
        val vm = WantFormViewModel.forTest(FormMode.Edit("a"), wantRepo, logRepo, { userId }, fixedClock)
        vm.state.first { it.name == "TikTok" }
        var done = false
        vm.delete { done = true }
        assertTrue(done)
        val all = wantRepo.getAllWantActivitiesForUser(userId).single()
        assertNotNull(all.hiddenAt)
    }
}
