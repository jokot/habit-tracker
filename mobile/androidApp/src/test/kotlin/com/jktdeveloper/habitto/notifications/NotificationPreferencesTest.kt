package com.jktdeveloper.habitto.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class NotificationPreferencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = NotificationPreferences(context)

    @Before fun resetDefaults() = runBlocking {
        val d = NotificationPrefs.DEFAULT
        prefs.setMasterEnabled(d.masterEnabled)
        prefs.setDailyReminderEnabled(d.dailyReminderEnabled)
        prefs.setDailyReminderMinutes(d.dailyReminderMinutes)
        prefs.setStreakRiskEnabled(d.streakRiskEnabled)
        prefs.setStreakRiskMinutes(d.streakRiskMinutes)
        prefs.setStreakFrozenEnabled(d.streakFrozenEnabled)
        prefs.setStreakResetEnabled(d.streakResetEnabled)
    }

    @Test fun `defaults match spec`() = runTest {
        val snap = prefs.flow.first()
        assertEquals(true, snap.masterEnabled)
        assertEquals(true, snap.dailyReminderEnabled)
        assertEquals(9 * 60, snap.dailyReminderMinutes)
        assertEquals(true, snap.streakRiskEnabled)
        assertEquals(21 * 60, snap.streakRiskMinutes)
        assertEquals(true, snap.streakFrozenEnabled)
        assertEquals(true, snap.streakResetEnabled)
    }

    @Test fun `toggle daily reminder persists`() = runTest {
        prefs.setDailyReminderEnabled(false)
        val snap = prefs.flow.first()
        assertEquals(false, snap.dailyReminderEnabled)
    }

    @Test fun `set daily reminder minutes clamps over-range value`() = runTest {
        prefs.setDailyReminderMinutes(2000)  // > 1439, clamps to 1439
        val snap = prefs.flow.first()
        assertEquals(1439, snap.dailyReminderMinutes)
    }

    @Test fun `setMasterEnabled persists`() = runTest {
        val prefs = NotificationPreferences(context)
        prefs.setMasterEnabled(false)
        assertEquals(false, prefs.flow.first().masterEnabled)
    }

    @Test fun `defaults include every NotificationTypeId with the catalog default`() = runTest {
        val snap = prefs.flow.first()
        for (t in NotificationTypeId.values()) {
            assertEquals(t.defaultEnabled, snap.isEnabled(t))
            if (t.hasTime) {
                assertEquals(t.defaultMinutesOfDay, snap.minutesOfDay(t))
            }
        }
    }

    @Test fun `setTypeEnabled persists per-type toggle`() = runTest {
        prefs.setTypeEnabled(NotificationTypeId.MILESTONE_STREAK, false)
        assertEquals(false, prefs.flow.first().isEnabled(NotificationTypeId.MILESTONE_STREAK))
    }

    @Test fun `setTypeMinutesOfDay clamps over-range value`() = runTest {
        prefs.setTypeMinutesOfDay(NotificationTypeId.DAILY_REMINDER, 3000)
        assertEquals(1439, prefs.flow.first().minutesOfDay(NotificationTypeId.DAILY_REMINDER))
    }
}
