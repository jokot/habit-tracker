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
    private val controller = WantTimerController(context, repo)

    @Test fun `start creates a RUNNING timer row`() = runTest {
        controller.start(userId = "u1", activityId = "a1", durationSec = 300)
        val active = repo.getActive("u1")
        assertEquals("a1", active?.activityId)
        assertEquals(WantTimerState.RUNNING, active?.state)
        assertEquals(300, active?.durationSec)
    }

    @Test fun `cancel marks the active timer CANCELLED`() = runTest {
        controller.start(userId = "u1", activityId = "a1", durationSec = 300)
        controller.cancel(userId = "u1")
        assertNull(repo.getActive("u1"))
    }
}
