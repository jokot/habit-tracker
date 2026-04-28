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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class SettingsViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val cfg = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, cfg)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setDailyReminderEnabled persists to DataStore`() = runBlocking {
        val prefs = NotificationPreferences(context)
        prefs.setDailyReminderEnabled(true)
        val scheduler = NotificationScheduler(context, prefs)
        val vm = SettingsViewModel(prefs, scheduler)

        vm.setDailyReminderEnabled(false)

        // viewModelScope.launch is fire-and-forget; poll until DataStore reflects the change.
        withTimeout(2_000) {
            while (prefs.flow.first().dailyReminderEnabled) delay(20)
        }
        assertEquals(false, prefs.flow.first().dailyReminderEnabled)
    }

    @Test
    fun `setDailyReminderMinutes clamps and persists`() = runBlocking {
        val prefs = NotificationPreferences(context)
        val scheduler = NotificationScheduler(context, prefs)
        val vm = SettingsViewModel(prefs, scheduler)

        vm.setDailyReminderMinutes(2000)

        withTimeout(2_000) {
            while (prefs.flow.first().dailyReminderMinutes != 1439) delay(20)
        }
        assertEquals(1439, prefs.flow.first().dailyReminderMinutes)
    }
}
