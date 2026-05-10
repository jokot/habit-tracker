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
            wantActivitiesProvider = { listOf(makeActivity("a1", 10)) },
        )
        val state = vm.state.first { !it.isLoading }
        assertEquals(0, state.currentStreak)
        assertEquals(1.0, state.currentRate, 0.0)
        assertEquals(1, state.currentTier.level)
        assertEquals(7, state.daysToNext)
        assertEquals(1, state.comparison.size)
        val row = state.comparison.first()
        assertEquals(5, row.tiers.size)
        // YouTube spec: unitsPerPoint = 10 → tier 1 eff = 10
        assertEquals(10, row.tiers.first { it.tierLevel == 1 }.unitsPerPoint)
        // tier 5 eff = 10 / 2.0 = 5
        assertEquals(5, row.tiers.first { it.tierLevel == 5 }.unitsPerPoint)
    }

    @Test
    fun `streak 22 → tier 4 rate 1_6 daysToNext 8`() = runTest {
        val vm = ExchangeRateViewModel(
            userIdProvider = { "u1" },
            streakFlow = { flowOf(streak(22)) },
            wantActivitiesProvider = { listOf(makeActivity("a1", 10)) },
        )
        val state = vm.state.first { !it.isLoading }
        // Phase 7 rate ladder: tier 4 → ×1.6.
        assertEquals(4, state.currentTier.level)
        assertEquals(1.6, state.currentRate, 0.0)
        assertEquals(8, state.daysToNext)
        // tier 4 eff = 10 / 1.6 = 6 (truncated)
        val row = state.comparison.first()
        assertEquals(6, row.tiers.first { it.tierLevel == 4 }.unitsPerPoint)
    }

    @Test
    fun `streak 100 → tier 5 rate 2_0 daysToNext null`() = runTest {
        val vm = ExchangeRateViewModel(
            userIdProvider = { "u1" },
            streakFlow = { flowOf(streak(100)) },
            wantActivitiesProvider = { emptyList() },
        )
        val state = vm.state.first { !it.isLoading }
        // Phase 7 rate ladder: tier 5 → ×2.0.
        assertEquals(5, state.currentTier.level)
        assertEquals(2.0, state.currentRate, 0.0)
        assertNull(state.daysToNext)
        assertEquals(0, state.comparison.size)
    }

    private fun streak(current: Int) = StreakSummary(
        currentStreak = current,
        longestStreak = current,
        totalDaysComplete = current,
        firstLogDate = null,
    )

    private fun makeActivity(id: String, unitsPerPoint: Int) = WantActivity(
        id = id, name = id, unit = "u", unitsPerPoint = unitsPerPoint,
    )
}
