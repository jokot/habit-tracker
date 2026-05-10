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
class WantListViewModelTest {
    private val userId = "u1"

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun makeRepo(): LocalWantActivityRepository {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val driver = AndroidSqliteDriver(HabitTrackerDatabase.Schema, context, name = null)
        return LocalWantActivityRepository(HabitTrackerDatabase(driver))
    }

    private fun seed(id: String, custom: Boolean = false, hiddenAt: Instant? = null) =
        WantActivity(
            id = id, name = "n-$id", unit = "minutes", costPerUnit = 1.0,
            isCustom = custom, hiddenAt = hiddenAt,
        )

    @Test
    fun `partition splits seeded vs custom`() = runTest {
        val repo = makeRepo()
        repo.saveWantActivity(seed("s"), userId)
        repo.saveWantActivity(seed("c", custom = true), userId)

        val vm = WantListViewModel.forTest(repo, { userId })
        val state = vm.state.first { it.seeded.isNotEmpty() || it.custom.isNotEmpty() }
        assertEquals(listOf("s"), state.seeded.map { it.id })
        assertEquals(listOf("c"), state.custom.map { it.id })
    }

    @Test
    fun `hide moves seeded want from visible to hidden bucket`() = runTest {
        val repo = makeRepo()
        repo.saveWantActivity(seed("s"), userId)
        val vm = WantListViewModel.forTest(repo, { userId })
        vm.state.first { it.seeded.isNotEmpty() }

        vm.hide("s", "n-s")
        val after = vm.state.first { it.hidden.isNotEmpty() }
        assertEquals(emptyList<String>(), after.seeded.map { it.id })
        assertEquals(listOf("s"), after.hidden.map { it.id })
        assertTrue(after.toast?.contains("hidden") == true)
    }

    @Test
    fun `unhide moves seeded want back to visible bucket`() = runTest {
        val repo = makeRepo()
        repo.saveWantActivity(seed("s", hiddenAt = Instant.fromEpochMilliseconds(2_000)), userId)
        val vm = WantListViewModel.forTest(repo, { userId })
        vm.state.first { it.hidden.isNotEmpty() }

        vm.unhide("s")
        val after = vm.state.first { it.seeded.isNotEmpty() }
        assertEquals(listOf("s"), after.seeded.map { it.id })
    }

    @Test
    fun `sorts seeded alphabetically`() = runTest {
        val repo = makeRepo()
        repo.saveWantActivity(seed("s2").copy(name = "Bravo"), userId)
        repo.saveWantActivity(seed("s1").copy(name = "Alpha"), userId)
        val vm = WantListViewModel.forTest(repo, { userId })
        val state = vm.state.first { it.seeded.size == 2 }
        assertEquals(listOf("Alpha", "Bravo"), state.seeded.map { it.name })
    }
}
