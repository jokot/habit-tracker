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
class HeldAccountStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = HeldAccountStore(context)

    @Before fun clean() = store.clear()

    @Test fun `empty store holds no account`() {
        assertNull(store.get())
    }

    @Test fun `a new store instance reads the held account`() {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        assertEquals(HeldAccount("auth-1", "a@b.com"), HeldAccountStore(context).get())
    }

    @Test fun `a held account can have no email`() {
        store.hold(HeldAccount("auth-1", null))
        assertEquals(HeldAccount("auth-1", null), store.get())
    }

    @Test fun `clear removes the held account`() {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        store.clear()
        assertNull(store.get())
    }
}
