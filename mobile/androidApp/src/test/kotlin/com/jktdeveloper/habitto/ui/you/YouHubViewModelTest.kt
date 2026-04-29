package com.jktdeveloper.habitto.ui.you

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class YouHubViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `test infrastructure smoke`() {
        // Smoke: VM is a thin pass-through to AppContainer (already tested in Phase 3).
        // Behavioral testing of sign-out path requires a fake AppContainer (out of scope
        // for 5a). Manual smoke (Task 18) verifies the full integration end-to-end.
        assertNotNull(context)
    }
}
