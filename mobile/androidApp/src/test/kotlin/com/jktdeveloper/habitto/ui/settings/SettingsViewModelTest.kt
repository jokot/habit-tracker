package com.jktdeveloper.habitto.ui.settings

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.jktdeveloper.habitto.notifications.NotificationPreferences
import com.jktdeveloper.habitto.notifications.NotificationScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class SettingsViewModelTest {
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
    fun `setDailyReminderEnabled persists to DataStore`() = runTest {
        val prefs = NotificationPreferences(context)
        prefs.setDailyReminderEnabled(true)
        val scheduler = NotificationScheduler(context, prefs)
        val vm = SettingsViewModel(prefs, scheduler)

        vm.setDailyReminderEnabled(false)
        // Allow viewModelScope coroutine to complete the DataStore write.
        kotlinx.coroutines.delay(50)

        assertEquals(false, prefs.flow.first().dailyReminderEnabled)
    }

    @Test
    fun `setDailyReminderMinutes clamps and persists`() = runTest {
        val prefs = NotificationPreferences(context)
        val scheduler = NotificationScheduler(context, prefs)
        val vm = SettingsViewModel(prefs, scheduler)

        vm.setDailyReminderMinutes(2000) // > 1439, should clamp
        kotlinx.coroutines.delay(50)

        assertEquals(1439, prefs.flow.first().dailyReminderMinutes)
    }
}
