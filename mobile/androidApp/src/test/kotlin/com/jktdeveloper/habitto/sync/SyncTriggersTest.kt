package com.jktdeveloper.habitto.sync

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.habittracker.data.sync.SyncReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class SyncTriggersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun initWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private fun jobs(reason: SyncReason): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncTriggers.uniqueName(reason)).get()

    @Test
    fun `background jobs wait for the network`() {
        // Offline, a job that runs only fails and waits for its backoff (#36).
        for (reason in listOf(SyncReason.POST_LOG, SyncReason.APP_FOREGROUND, SyncReason.WIDGET_WRITE)) {
            SyncTriggers.enqueue(context, reason)
            assertEquals(reason.name, NetworkType.CONNECTED, jobs(reason).single().constraints.requiredNetworkType)
        }
    }

    @Test
    fun `a manual sync runs offline, so the user sees the error`() {
        SyncTriggers.enqueue(context, SyncReason.MANUAL)
        assertEquals(NetworkType.NOT_REQUIRED, jobs(SyncReason.MANUAL).single().constraints.requiredNetworkType)
    }

    @Test
    fun `a signed-in widget log starts a sync`() {
        syncAfterWidgetLog(context, logged = true, signedIn = true)
        assertEquals(1, jobs(SyncReason.WIDGET_WRITE).size)
    }

    @Test
    fun `a guest widget log or a failed log starts no sync`() {
        syncAfterWidgetLog(context, logged = true, signedIn = false)
        syncAfterWidgetLog(context, logged = false, signedIn = true)
        assertTrue(jobs(SyncReason.WIDGET_WRITE).isEmpty())
    }
}
