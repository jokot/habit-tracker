package com.jktdeveloper.habitto.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class MilestoneWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `doWork returns success or retry without crashing`() = runTest {
        val worker = TestListenableWorkerBuilder<MilestoneWorker>(context).build()
        val result = worker.doWork()
        assert(result == ListenableWorker.Result.success() || result == ListenableWorker.Result.retry())
    }

    @Test fun `milestoneFor returns matching threshold or null`() {
        assertEquals(7, MilestoneWorker.milestoneFor(7)?.days)
        assertEquals(30, MilestoneWorker.milestoneFor(30)?.days)
        assertEquals(100, MilestoneWorker.milestoneFor(100)?.days)
        assertEquals(365, MilestoneWorker.milestoneFor(365)?.days)
        assertNull(MilestoneWorker.milestoneFor(8))
        assertNull(MilestoneWorker.milestoneFor(0))
    }
}
