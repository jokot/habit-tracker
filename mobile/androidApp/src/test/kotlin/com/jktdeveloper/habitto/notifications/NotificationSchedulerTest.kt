package com.jktdeveloper.habitto.notifications

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class NotificationSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        val cfg = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, cfg)
    }

    @Test
    fun `reschedule with all enabled enqueues 3 unique works`() = runTest {
        val prefs = NotificationPreferences(context)
        prefs.setDailyReminderEnabled(true)
        prefs.setStreakRiskEnabled(true)
        prefs.setStreakFrozenEnabled(true)
        prefs.setStreakResetEnabled(true)

        val scheduler = NotificationScheduler(context, prefs)
        scheduler.reschedule()

        val wm = WorkManager.getInstance(context)
        assertEquals(1, wm.getWorkInfosForUniqueWork(NotificationScheduler.WORK_DAILY).get().size)
        assertEquals(1, wm.getWorkInfosForUniqueWork(NotificationScheduler.WORK_RISK).get().size)
        assertEquals(1, wm.getWorkInfosForUniqueWork(NotificationScheduler.WORK_DAY_BOUNDARY).get().size)
    }

    @Test
    fun `cancelAll finishes all unique works`() = runTest {
        val prefs = NotificationPreferences(context)
        prefs.setDailyReminderEnabled(true)
        prefs.setStreakRiskEnabled(true)
        prefs.setStreakFrozenEnabled(true)

        val scheduler = NotificationScheduler(context, prefs)
        scheduler.reschedule()
        scheduler.cancelAll()

        val wm = WorkManager.getInstance(context)
        val daily = wm.getWorkInfosForUniqueWork(NotificationScheduler.WORK_DAILY).get()
        if (daily.isNotEmpty()) {
            assert(daily.all { it.state.isFinished }) {
                "Expected all WORK_DAILY entries to be finished, got: ${daily.map { it.state }}"
            }
        }
    }

    @Test
    fun `disabling daily reminder cancels its work`() = runTest {
        val prefs = NotificationPreferences(context)
        prefs.setDailyReminderEnabled(true)
        prefs.setStreakRiskEnabled(true)
        prefs.setStreakFrozenEnabled(true)

        val scheduler = NotificationScheduler(context, prefs)
        scheduler.reschedule()

        prefs.setDailyReminderEnabled(false)
        scheduler.reschedule()

        val wm = WorkManager.getInstance(context)
        val daily = wm.getWorkInfosForUniqueWork(NotificationScheduler.WORK_DAILY).get()
        assert(daily.all { it.state.isFinished }) {
            "Expected daily-reminder work to be cancelled, was: ${daily.map { it.state }}"
        }
    }
}
