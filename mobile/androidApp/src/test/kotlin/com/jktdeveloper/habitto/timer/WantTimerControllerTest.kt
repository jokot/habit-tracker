package com.jktdeveloper.habitto.timer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.repository.LocalWantTimerRepository
import com.habittracker.domain.model.WantTimerState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class WantTimerControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val driver = AndroidSqliteDriver(HabitTrackerDatabase.Schema, context, "test-want-timer.db")
    private val db = HabitTrackerDatabase(driver)
    private val repo = LocalWantTimerRepository(db)
    private val wantActivityRepo = com.habittracker.data.repository.LocalWantActivityRepository(db)
    private val wantLogRepo = com.habittracker.data.repository.LocalWantLogRepository(db)
    private val habitLogRepo = com.habittracker.data.repository.LocalHabitLogRepository(db)
    private val habitRepo = com.habittracker.data.repository.LocalHabitRepository(db)
    private val streakUseCase = com.habittracker.domain.usecase.ComputeStreakUseCase(habitLogRepo, habitRepo)
    private val getStreakOnDay = com.habittracker.domain.usecase.GetUserStreakOnDayUseCase(streakUseCase)
    private val getBalance = com.habittracker.domain.usecase.GetPointBalanceUseCase(
        habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo,
        getUserStreakOnDayUseCase = getStreakOnDay,
    )
    private val logWantUseCase = com.habittracker.domain.usecase.LogWantUseCase(
        wantLogRepository = wantLogRepo,
        wantActivityRepository = wantActivityRepo,
        getPointBalanceUseCase = getBalance,
        getUserStreakOnDayUseCase = getStreakOnDay,
    )
    private val controller = WantTimerController(context, repo, wantActivityRepo, logWantUseCase)

    @Test fun `start creates a RUNNING timer row`() = runTest {
        controller.start(userId = "u1", activityId = "a1", durationSec = 300)
        val active = repo.getActive("u1")
        assertEquals("a1", active?.activityId)
        assertEquals(WantTimerState.RUNNING, active?.state)
        assertEquals(300, active?.durationSec)
    }

    @Test fun `cancelWithPartialLog flips active to CANCELLED when no want activity row exists`() = runTest {
        controller.start(userId = "u1", activityId = "a1", durationSec = 300)
        controller.cancelWithPartialLog("u1")
        assertNull(repo.getActive("u1"))
    }

    @Test fun `cancelWithPartialLog logs floor(elapsed) for min-unit and returns Logged`() = runTest {
        val now = kotlinx.datetime.Clock.System.now()
        val fiveMinAgo = now - kotlin.time.Duration.parse("5m")
        val activityId = "test-min"
        // Seed a habit + habit log so the user has a positive point balance
        // (LogWantUseCase requires available points; without this seeding it would
        // throw InsufficientPointsException → swallowed → Discarded path).
        db.habitTrackerDatabaseQueries.upsertHabit(
            id = "h1", userId = "u1", templateId = null,
            name = "Test habit", unit = "x",
            thresholdPerPoint = 1.0, dailyTarget = 10,
            createdAt = now.toEpochMilliseconds(),
            updatedAt = now.toEpochMilliseconds(),
            effectiveFrom = null, effectiveTo = null,
        )
        db.habitTrackerDatabaseQueries.insertHabitLog(
            id = "hl1", userId = "u1", habitId = "h1",
            quantity = 100.0, loggedAt = now.toEpochMilliseconds(),
            deletedAt = null, syncedAt = null,
        )
        db.habitTrackerDatabaseQueries.upsertWantActivity(
            id = activityId, userId = "u1", name = "TikTok", unit = "min",
            unitsPerPoint = 1, isCustom = 0, updatedAt = now.toEpochMilliseconds(),
            iconKey = null, hiddenAt = null,
        )
        db.habitTrackerDatabaseQueries.insertWantTimer(
            id = "t-elapsed",
            userId = "u1",
            activityId = activityId,
            durationSec = 900,
            startedAt = fiveMinAgo.toEpochMilliseconds(),
            endsAt = (fiveMinAgo + kotlin.time.Duration.parse("15m")).toEpochMilliseconds(),
            state = "RUNNING",
        )

        val result = controller.cancelWithPartialLog("u1")

        assertTrue("expected Logged, got $result", result is CancelResult.Logged)
        val logged = result as CancelResult.Logged
        assertEquals(5, logged.minutes)
        assertEquals(WantTimerState.CANCELLED, repo.getById("t-elapsed")?.state)
    }

    @Test fun `cancelWithPartialLog returns Discarded for elapsed lt 1 minute`() = runTest {
        val now = kotlinx.datetime.Clock.System.now()
        db.habitTrackerDatabaseQueries.upsertWantActivity(
            id = "a-min", userId = "u1", name = "TikTok", unit = "min",
            unitsPerPoint = 1, isCustom = 0, updatedAt = now.epochSeconds * 1000,
            iconKey = null, hiddenAt = null,
        )
        db.habitTrackerDatabaseQueries.insertWantTimer(
            id = "t-fresh", userId = "u1", activityId = "a-min",
            durationSec = 600,
            startedAt = now.epochSeconds * 1000,
            endsAt = (now + kotlin.time.Duration.parse("10m")).epochSeconds * 1000,
            state = "RUNNING",
        )
        val result = controller.cancelWithPartialLog("u1")
        assertEquals(CancelResult.Discarded, result)
        assertEquals(WantTimerState.CANCELLED, repo.getById("t-fresh")?.state)
    }

    @Test fun `cancelWithPartialLog returns Discarded for non-min unit`() = runTest {
        val now = kotlinx.datetime.Clock.System.now()
        val tenMinAgo = now - kotlin.time.Duration.parse("10m")
        db.habitTrackerDatabaseQueries.upsertWantActivity(
            id = "a-cup", userId = "u1", name = "Coffee", unit = "cup",
            unitsPerPoint = 1, isCustom = 0, updatedAt = now.epochSeconds * 1000,
            iconKey = null, hiddenAt = null,
        )
        db.habitTrackerDatabaseQueries.insertWantTimer(
            id = "t-cup", userId = "u1", activityId = "a-cup",
            durationSec = 900,
            startedAt = tenMinAgo.epochSeconds * 1000,
            endsAt = (tenMinAgo + kotlin.time.Duration.parse("15m")).epochSeconds * 1000,
            state = "RUNNING",
        )
        val result = controller.cancelWithPartialLog("u1")
        assertEquals(CancelResult.Discarded, result)
    }

    @Test fun `cancelWithPartialLog returns NoActiveTimer when none running`() = runTest {
        val result = controller.cancelWithPartialLog("u1")
        assertEquals(CancelResult.NoActiveTimer, result)
    }
}
