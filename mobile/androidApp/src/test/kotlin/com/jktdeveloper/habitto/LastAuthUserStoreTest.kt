package com.jktdeveloper.habitto

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class LastAuthUserStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = LastAuthUserStore(context)

    @Before fun clean() = store.clear()

    @Test
    fun `cold process without a loaded session keeps the last authenticated id`() {
        assertEquals("auth-1", store.resolve("auth-1") { "guest" })
        // Fresh process: supabase-kt hasn't loaded the session, or couldn't refresh it.
        assertEquals("auth-1", store.resolve(null) { error("guest id must not be minted") })
    }

    @Test
    fun `never signed in falls through to the guest id`() {
        assertEquals("guest", store.resolve(null) { "guest" })
    }

    @Test
    fun `sign-out stops the id from sticking`() {
        store.resolve("auth-1") { "guest" }
        store.clear()
        assertEquals("guest", store.resolve(null) { "guest" })
    }

    @Test
    fun `keeps the email of the last session after the session is gone`() {
        store.resolve("auth-1", "a@b.com") { "guest" }
        // supabase-kt cleared the session after a failed refresh (#33).
        store.resolve(null, null) { "guest" }
        assertEquals("a@b.com", store.lastEmail())
    }

    @Test
    fun `a session of another user does not keep the old email`() {
        store.resolve("auth-1", "a@b.com") { "guest" }
        store.resolve("auth-2", null) { "guest" }
        assertNull(store.lastEmail())
    }

    @Test
    fun `sign-out clears the email`() {
        store.resolve("auth-1", "a@b.com") { "guest" }
        store.clear()
        assertNull(store.lastEmail())
    }

    @Test
    fun `rememberedUserId is the last authenticated id until a clear`() {
        assertNull(store.rememberedUserId())
        store.resolve("auth-1") { "guest" }
        assertEquals("auth-1", store.rememberedUserId())
        store.clear()
        assertNull(store.rememberedUserId())
    }

    @Test
    fun `rememberedUserId is null for a guest`() {
        store.resolve(null) { "guest" }
        assertNull(store.rememberedUserId())
    }
}
