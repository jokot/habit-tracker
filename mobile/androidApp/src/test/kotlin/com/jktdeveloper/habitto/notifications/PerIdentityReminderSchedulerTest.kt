package com.jktdeveloper.habitto.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class PerIdentityReminderSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @Test fun `reconcile enqueues one work per identity id and cancels removed`() = runTest {
        val scheduler = PerIdentityReminderScheduler(context)
        scheduler.reconcile(setOf("id-a", "id-b"), minutesOfDay = 17 * 60 + 30)
        val wm = WorkManager.getInstance(context)
        val aInfos = wm.getWorkInfosForUniqueWork(PerIdentityReminderScheduler.workName("id-a")).get()
        val bInfos = wm.getWorkInfosForUniqueWork(PerIdentityReminderScheduler.workName("id-b")).get()
        assertEquals(1, aInfos.size)
        assertEquals(1, bInfos.size)

        scheduler.reconcile(setOf("id-b"), minutesOfDay = 17 * 60 + 30, previousIdentityIds = setOf("id-a", "id-b"))
        val aAfter = wm.getWorkInfosForUniqueWork(PerIdentityReminderScheduler.workName("id-a")).get()
        assertTrue(aAfter.all { it.state.isFinished })
    }

    @Test fun `cancelAll cancels all tagged work`() = runTest {
        val scheduler = PerIdentityReminderScheduler(context)
        scheduler.reconcile(setOf("id-a"), minutesOfDay = 600)
        scheduler.cancelAll()
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(PerIdentityReminderScheduler.workName("id-a")).get()
        assertTrue(infos.all { it.state.isFinished })
    }
}
