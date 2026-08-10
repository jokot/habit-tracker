package com.jktdeveloper.habitto.timer

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.repository.LocalHabitLogRepository
import com.habittracker.data.repository.LocalHabitRepository
import com.habittracker.data.repository.LocalWantActivityRepository
import com.habittracker.data.repository.LocalWantLogRepository
import com.habittracker.data.repository.LocalWantTimerRepository
import com.habittracker.domain.model.WantTimerState
import com.habittracker.domain.usecase.ComputeStreakUseCase
import com.habittracker.domain.usecase.GetPointBalanceUseCase
import com.habittracker.domain.usecase.GetUserStreakOnDayUseCase
import com.habittracker.domain.usecase.LogWantUseCase
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.ui.want.WantDetailUi
import com.jktdeveloper.habitto.ui.want.WantDetailViewModel
import com.jktdeveloper.habitto.ui.want.WantTimerUi
import com.jktdeveloper.habitto.ui.want.WantTimerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class TimerIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: HabitTrackerDatabase
    private lateinit var wantActivityRepo: LocalWantActivityRepository
    private lateinit var timerRepo: LocalWantTimerRepository
    private lateinit var controller: WantTimerController

    private val userId = "u1"

    // Shared scheduler: Main dispatcher and runTest must advance the SAME virtual clock,
    // otherwise viewModelScope's `while(true){...;delay(1000)}` loops park on an orphan
    // scheduler that nothing ever drives -> busy-spin/hang instead of a clean suspend.
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val driver = AndroidSqliteDriver(HabitTrackerDatabase.Schema, context, "test-integration.db")
        db = HabitTrackerDatabase(driver)
        wantActivityRepo = LocalWantActivityRepository(db)
        timerRepo = LocalWantTimerRepository(db)

        val wantLogRepo = LocalWantLogRepository(db)
        val habitLogRepo = LocalHabitLogRepository(db)
        val habitRepo = LocalHabitRepository(db)
        val streakUseCase = ComputeStreakUseCase(habitLogRepo, habitRepo)
        val getStreakOnDay = GetUserStreakOnDayUseCase(streakUseCase)
        val getBalance = GetPointBalanceUseCase(
            habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo,
            getUserStreakOnDayUseCase = getStreakOnDay,
        )
        val logWantUseCase = LogWantUseCase(
            wantLogRepository = wantLogRepo,
            wantActivityRepository = wantActivityRepo,
            getPointBalanceUseCase = getBalance,
            getUserStreakOnDayUseCase = getStreakOnDay,
        )
        controller = WantTimerController(context, timerRepo, wantActivityRepo, logWantUseCase, getBalance)

        // Seed some data so we have a positive balance
        val now = Clock.System.now().toEpochMilliseconds()
        db.habitTrackerDatabaseQueries.upsertHabit(
            id = "h1", userId = userId, templateId = null,
            name = "Test habit", unit = "x",
            thresholdPerPoint = 1.0, dailyTarget = 10,
            createdAt = now, updatedAt = now,
            effectiveFrom = null, effectiveTo = null,
        )
        db.habitTrackerDatabaseQueries.insertHabitLog(
            id = "hl1", userId = userId, habitId = "h1",
            quantity = 100.0, loggedAt = now,
            deletedAt = null, syncedAt = null,
        )
        db.habitTrackerDatabaseQueries.upsertWantActivity(
            id = "w1", userId = userId, name = "TikTok", unit = "min",
            unitsPerPoint = 1, isCustom = 0, updatedAt = now,
            iconKey = null, hiddenAt = null,
        )
        db.habitTrackerDatabaseQueries.upsertWantActivity(
            id = "w2", userId = userId, name = "YouTube", unit = "min",
            unitsPerPoint = 1, isCustom = 0, updatedAt = now,
            iconKey = null, hiddenAt = null,
        )
    }

    @org.junit.After
    fun teardown() {
        createdVms.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    // Tracks every VM created so teardown can cancel their viewModelScope even if an
    // assertion throws mid-test — otherwise their uncancelled `while(true){...;delay(1000)}`
    // pollers spin forever under runTest's end-of-test advanceUntilIdle() (real SQLite I/O
    // every "tick", no wall-clock delay to bound it).
    private val createdVms = mutableListOf<ViewModel>()

    // Some use-case chains (ComputeStreakUseCase.computeNow -> SQLDelight Flow.mapToList(
    // Dispatchers.Default)) hop onto a REAL background thread outside the test scheduler.
    // runCurrent()/advanceUntilIdle() can't force that thread to run. Poll with a tiny real
    // sleep between attempts, bounded so a genuine regression fails fast instead of hanging.
    private suspend fun TestScope.awaitCondition(timeoutMs: Long = 2000, block: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            runCurrent()
            if (block()) return
            Thread.sleep(10)
        }
        runCurrent()
        check(block()) { "awaitCondition timed out after ${timeoutMs}ms" }
    }

    // A lightweight wrapper to instantiate VMs safely in tests without real AppContainer
    private fun createDetailVM(activityId: String) = WantDetailViewModel(
        activityId = activityId,
        wantActivityRepo = wantActivityRepo,
        wantLogRepo = LocalWantLogRepository(db),
        timerController = controller,
        timerRepo = timerRepo,
        userIdProvider = { userId },
    ).also { createdVms += it }

    private fun createTimerVM() = WantTimerViewModel(
        timerController = controller,
        timerRepo = timerRepo,
        wantActivityRepo = wantActivityRepo,
        userIdProvider = { userId },
    ).also { createdVms += it }

    @Test
    fun `full flow - start, overlap prompt, replace, cancel, orphan`() = runTest(testDispatcher.scheduler) {
      try {
        // 1. Initial state: Want A detail
        val detailVMA = createDetailVM("w1")
        val stateA1 = detailVMA.state.first()
        assertNull("No timer should be running", stateA1.activeTimer)

        // 2. Start timer A
        detailVMA.requestStartTimer(300) // 5 mins
        // start() reads the notification prefs off DataStore's own dispatcher before it
        // touches the repo, so the nav flag lands after this scheduler has drained.
        awaitCondition { detailVMA.state.value.navigateToTimerActivityId != null }
        val stateA2 = detailVMA.state.first()
        assertEquals("Should auto-nav to timer screen", "w1", stateA2.navigateToTimerActivityId)

        // Timer screen shows Running
        val timerVM = createTimerVM()
        val timerState1 = timerVM.state.first()
        assertEquals(WantTimerUi.ScreenState.Running, timerState1.state)
        assertEquals("w1", timerState1.want?.id)

        // 3. Overlap check: open Want B while A runs
        val detailVMB = createDetailVM("w2")
        detailVMB.requestStartTimer(600) // 10 mins

        val stateB1 = detailVMB.state.first()
        assertNotNull("Should prompt for overlap", stateB1.pendingOverlap)
        assertEquals("TikTok", stateB1.pendingOverlap?.otherWantName)

        // Fast-forward time to simulate elapsed duration
        val past = Clock.System.now() - 2.minutes
        val activeBeforeReplace = timerRepo.getActive(userId)!!
        // We bypass the strict repo to simulate time passing for the partial log
        // The repo enforces cancelling the old row, so we just update the startedAt
        db.habitTrackerDatabaseQueries.updateWantTimerState("CANCELLED", activeBeforeReplace.id)
        db.habitTrackerDatabaseQueries.insertWantTimer(
            id = "t-mocked-past",
            userId = userId,
            activityId = activeBeforeReplace.activityId,
            durationSec = 300,
            startedAt = past.toEpochMilliseconds(),
            endsAt = (past + 5.minutes).toEpochMilliseconds(),
            state = "RUNNING"
        )

        // 4. Confirm replace
        detailVMB.confirmReplace()
        // cancelWithPartialLog -> LogWantUseCase -> ComputeStreakUseCase.computeNow() reads a
        // SQLDelight Flow built with mapToList(Dispatchers.Default) — a REAL background thread,
        // outside the test scheduler. runCurrent() can't force that thread to run; give it real
        // wall-clock time to post its result back onto our test dispatcher, then drain.
        // Nav is the last thing confirmReplace sets, so it's the condition that covers
        // both the real-thread use-case work and start()'s DataStore read.
        awaitCondition { detailVMB.state.value.navigateToTimerActivityId != null }

        val stateB2 = detailVMB.state.first()
        assertNull("Overlap prompt dismissed", stateB2.pendingOverlap)
        assertEquals("Should nav to B's timer", "w2", stateB2.navigateToTimerActivityId)

        // Old timer should be cancelled
        val activeAfterReplace = timerRepo.getActive(userId)
        assertEquals("New timer should be for B", "w2", activeAfterReplace?.activityId)
        val oldTimerState = db.habitTrackerDatabaseQueries.getWantTimerById(activeBeforeReplace.id).executeAsOne()
        assertEquals("CANCELLED", oldTimerState.state)

        // Verify partial log happened
        val logs = LocalWantLogRepository(db).getAllActiveLogsForUser(userId)
        assertEquals("Should have logged 2 minutes", 1, logs.size)
        assertEquals(2.0, logs[0].quantity, 0.0)

        // 5. Cancel current timer
        timerVM.cancel()
        awaitCondition { timerRepo.getActive(userId) == null }
        assertNull("Timer should be cancelled", timerRepo.getActive(userId))

        // 6. Orphan state
        val timerVM2 = createTimerVM()
        val timerStateOrphan = timerVM2.state.first()
        assertEquals("Should show orphan screen", WantTimerUi.ScreenState.Orphan, timerStateOrphan.state)
      } finally {
        // Must cancel before runTest's internal advanceUntilIdle() drains the scheduler —
        // @After runs too late (after runTest already returned/hung), and any assertion
        // failure above would otherwise skip straight past cleanup.
        createdVms.forEach { it.viewModelScope.cancel() }
      }
    }
}
