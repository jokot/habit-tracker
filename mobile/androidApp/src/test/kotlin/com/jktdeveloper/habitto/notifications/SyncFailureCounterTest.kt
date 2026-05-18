package com.jktdeveloper.habitto.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class SyncFailureCounterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `incrementAndShouldFire returns false until 3 strikes`() = runTest {
        val counter = SyncFailureCounter(context)
        counter.reset()
        assertFalse(counter.incrementAndShouldFire())
        assertFalse(counter.incrementAndShouldFire())
        assertTrue(counter.incrementAndShouldFire())
    }

    @Test fun `reset clears strikes`() = runTest {
        val counter = SyncFailureCounter(context)
        counter.incrementAndShouldFire()
        counter.incrementAndShouldFire()
        counter.reset()
        assertEquals(0, counter.current())
    }
}
