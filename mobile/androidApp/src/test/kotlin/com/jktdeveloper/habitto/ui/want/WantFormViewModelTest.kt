package com.jktdeveloper.habitto.ui.want

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.repository.LocalWantActivityRepository
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

    private fun newRepo(): LocalWantActivityRepository {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val driver = AndroidSqliteDriver(HabitTrackerDatabase.Schema, context, name = null)
        val db = HabitTrackerDatabase(driver)
        return LocalWantActivityRepository(db)
    }

    @Test
    fun `new mode saves a custom activity`() = runTest {
        val wantRepo = newRepo()
        val vm = WantFormViewModel.forTest(FormMode.New, wantRepo, { userId }, fixedClock)
        vm.onName("Bingewatch")
        vm.onUnit("episode")
        vm.onUnitsInput("5")
        vm.onIconKey("local_movies")
        var done = false
        vm.save { done = true }
        assertTrue(done)
        val saved = wantRepo.getAllWantActivitiesForUser(userId).single()
        assertEquals("Bingewatch", saved.name)
        assertTrue(saved.isCustom)
        assertEquals("local_movies", saved.iconKey)
        assertEquals(5, saved.unitsPerPoint)
    }

    @Test
    fun `edit mode loads existing fields`() = runTest {
        val wantRepo = newRepo()
        wantRepo.saveWantActivity(
            WantActivity(id = "a", name = "TikTok", unit = "minutes",
                         unitsPerPoint = 1, iconKey = "play_circle"),
            userId,
        )
        val vm = WantFormViewModel.forTest(FormMode.Edit("a"), wantRepo, { userId }, fixedClock)
        val loaded = vm.state.first { it.name.isNotEmpty() }
        assertEquals("TikTok", loaded.name)
        assertEquals("play_circle", loaded.iconKey)
        assertEquals("1", loaded.unitsInput)
    }

    @Test
    fun `unitsInput updates state without warning state`() = runTest {
        val wantRepo = newRepo()
        val vm = WantFormViewModel.forTest(FormMode.New, wantRepo, { userId }, fixedClock)
        vm.onUnitsInput("7")
        assertEquals("7", vm.state.first().unitsInput)
    }

    @Test
    fun `validation rejects empty name and units below 1`() = runTest {
        val wantRepo = newRepo()
        val vm = WantFormViewModel.forTest(FormMode.New, wantRepo, { userId }, fixedClock)

        // blank name + valid units -> fails on name
        vm.onName("")
        vm.onUnitsInput("1")
        var done = false
        vm.save { done = true }
        assertFalse(done)
        assertNotNull(vm.state.first().validationError)

        // valid name + units = 0 -> fails
        vm.onName("X")
        vm.onUnitsInput("0")
        vm.save { done = true }
        assertFalse(done)

        // valid name + non-numeric units -> fails (toIntOrNull null)
        vm.onUnitsInput("abc")
        vm.save { done = true }
        assertFalse(done)
    }

    @Test
    fun `delete softHides the activity`() = runTest {
        val wantRepo = newRepo()
        wantRepo.saveWantActivity(
            WantActivity(id = "a", name = "TikTok", unit = "minutes",
                         unitsPerPoint = 1, isCustom = true),
            userId,
        )
        val vm = WantFormViewModel.forTest(FormMode.Edit("a"), wantRepo, { userId }, fixedClock)
        vm.state.first { it.name == "TikTok" }
        var done = false
        vm.delete { done = true }
        assertTrue(done)
        val all = wantRepo.getAllWantActivitiesForUser(userId).single()
        assertNotNull(all.hiddenAt)
    }
}
