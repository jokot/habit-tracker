package com.jktdeveloper.habitto.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class StreakRiskWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `doWork returns success or retry without crashing`() = runTest {
        val worker = TestListenableWorkerBuilder<StreakRiskWorker>(context).build()
        val result = worker.doWork()
        assert(result == ListenableWorker.Result.success() || result == ListenableWorker.Result.retry()) {
            "Expected success or retry, got: $result"
        }
    }
}
