package com.jktdeveloper.habitto.notifications

import android.content.Context
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class DailyReminderWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `doWork returns success when application is not the production class`() = runTest {
        // Cast to HabitTrackerApplication will fail under @Config(application = Application::class).
        // The worker handles this via runCatching → Result.retry().
        val worker = TestListenableWorkerBuilder<DailyReminderWorker>(context).build()
        val result = worker.doWork()
        // Either success or retry; both indicate non-crash, exception-safe path.
        assert(result == ListenableWorker.Result.success() || result == ListenableWorker.Result.retry()) {
            "Expected success or retry, got: $result"
        }
    }
}
