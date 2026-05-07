package com.jktdeveloper.habitto.ui.exchange

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habittracker.domain.model.StreakSummary
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class ExchangeRateViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `streak 0 → tier 1 rate 1_0 daysToNext 7`() = runTest {
        val vm = ExchangeRateViewModel(
            userIdProvider = { "u1" },
            streakFlow = { flowOf(streak(0)) },
            wantActivitiesProvider = { listOf(makeActivity("a1", 5.0)) },
        )
        val state = vm.state.first { !it.isLoading }
        assertEquals(0, state.currentStreak)
        assertEquals(1.0, state.currentRate, 0.0)
        assertEquals(1, state.currentTier.level)
        assertEquals(7, state.daysToNext)
        assertEquals(1, state.comparison.size)
        assertEquals(5.0, state.comparison.first().baseCostPerUnit, 0.0)
        assertEquals(5.0, state.comparison.first().currentCostPerUnit, 0.0)
    }

    @Test
    fun `streak 22 → tier 4 rate 1_3 daysToNext 8`() = runTest {
        val vm = ExchangeRateViewModel(
            userIdProvider = { "u1" },
            streakFlow = { flowOf(streak(22)) },
            wantActivitiesProvider = { listOf(makeActivity("a1", 5.0)) },
        )
        val state = vm.state.first { !it.isLoading }
        assertEquals(4, state.currentTier.level)
        assertEquals(1.3, state.currentRate, 0.0)
        assertEquals(8, state.daysToNext)
        assertEquals(6.5, state.comparison.first().currentCostPerUnit, 0.001)
    }

    @Test
    fun `streak 100 → tier 5 rate 1_4 daysToNext null`() = runTest {
        val vm = ExchangeRateViewModel(
            userIdProvider = { "u1" },
            streakFlow = { flowOf(streak(100)) },
            wantActivitiesProvider = { emptyList() },
        )
        val state = vm.state.first { !it.isLoading }
        assertEquals(5, state.currentTier.level)
        assertEquals(1.4, state.currentRate, 0.0)
        assertNull(state.daysToNext)
        assertEquals(0, state.comparison.size)
    }

    private fun streak(current: Int) = StreakSummary(
        currentStreak = current,
        longestStreak = current,
        totalDaysComplete = current,
        firstLogDate = null,
    )

    private fun makeActivity(id: String, costPerUnit: Double) = WantActivity(
        id = id, name = id, unit = "u", costPerUnit = costPerUnit,
    )
}
