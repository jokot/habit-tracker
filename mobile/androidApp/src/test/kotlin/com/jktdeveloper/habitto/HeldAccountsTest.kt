package com.jktdeveloper.habitto

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class HeldAccountsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = HeldAccountStore(context)
    private val deleted = mutableListOf<String>()
    private var unsynced: Int = 0
    private var countFails = false
    private var deleteFails = false

    private val held = HeldAccounts(
        store = store,
        countUnsynced = { if (countFails) error("db") else unsynced },
        deleteUserRows = { userId -> if (deleteFails) error("db") else deleted += userId },
    )

    @Before fun clean() = store.clear()

    @Test fun `expiry with no unsynced changes deletes the rows and holds nothing`() = runTest {
        unsynced = 0
        assertEquals(0, held.onSessionExpired("auth-1", "a@b.com"))
        assertEquals(listOf("auth-1"), deleted)
        assertNull(store.get())
    }

    @Test fun `expiry with unsynced changes keeps the rows and holds the user`() = runTest {
        unsynced = 3
        assertEquals(3, held.onSessionExpired("auth-1", "a@b.com"))
        assertEquals(emptyList<String>(), deleted)
        assertEquals(HeldAccount("auth-1", "a@b.com"), store.get())
    }

    @Test fun `expiry where the count fails keeps the rows and holds the user`() = runTest {
        countFails = true
        assertNull(held.onSessionExpired("auth-1", null))
        assertEquals(emptyList<String>(), deleted)
        assertEquals(HeldAccount("auth-1", null), store.get())
    }

    @Test fun `expiry where the delete fails still returns 0`() = runTest {
        unsynced = 0
        deleteFails = true
        assertEquals(0, held.onSessionExpired("auth-1", "a@b.com"))
        assertNull(store.get())
    }

    @Test fun `sign-in as the held user keeps the rows and clears the hold`() = runTest {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        held.onSignedIn("auth-1")
        assertEquals(emptyList<String>(), deleted)
        assertNull(store.get())
    }

    @Test fun `sign-in as another user deletes only the held rows and clears the hold`() = runTest {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        held.onSignedIn("auth-2")
        assertEquals(listOf("auth-1"), deleted)
        assertNull(store.get())
    }

    @Test fun `sign-in where the delete fails keeps the hold`() = runTest {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        deleteFails = true
        held.onSignedIn("auth-2")
        assertEquals(HeldAccount("auth-1", "a@b.com"), store.get())
    }

    @Test fun `sign-in with no hold deletes nothing`() = runTest {
        held.onSignedIn("auth-2")
        assertEquals(emptyList<String>(), deleted)
    }

    // The caller must not move guest rows onto the kept rows: LocalUserIdentity has a
    // composite key, so the "new user" migration would fail on an identity clash.
    @Test fun `sign-in as the held user reports that it kept rows`() = runTest {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        assertTrue(held.onSignedIn("auth-1"))
    }

    @Test fun `sign-in as another user or with no hold reports no kept rows`() = runTest {
        assertFalse(held.onSignedIn("auth-2"))
        store.hold(HeldAccount("auth-1", "a@b.com"))
        assertFalse(held.onSignedIn("auth-2"))
    }

    @Test fun `sign-in where the delete fails reports no kept rows`() = runTest {
        store.hold(HeldAccount("auth-1", "a@b.com"))
        deleteFails = true
        assertFalse(held.onSignedIn("auth-2"))
    }

    @Test fun `summary gives the held email and the current count`() = runTest {
        assertNull(held.summary())
        store.hold(HeldAccount("auth-1", "a@b.com"))
        unsynced = 5
        assertEquals(HeldAccountSummary("a@b.com", 5), held.summary())
        countFails = true
        assertEquals(HeldAccountSummary("a@b.com", null), held.summary())
    }
}
