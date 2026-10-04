package com.jktdeveloper.habitto

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSessionEndTest {
    private var remembered: String? = null
    private val guard = ServerSessionEnd(rememberedUserId = { remembered })

    @Test fun `no session for a guest is not a server end`() {
        remembered = null
        assertFalse(guard.endedByServer())
    }

    @Test fun `no session for a remembered user is a server end`() {
        remembered = "auth-1"
        assertTrue(guard.endedByServer())
    }

    @Test fun `no session during a sign-out of the app is not a server end`() = runTest {
        remembered = "auth-1"
        guard.ownSignOut { assertFalse(guard.endedByServer()) }
    }

    @Test fun `the guard stops after the sign-out of the app`() = runTest {
        remembered = "auth-1"
        guard.ownSignOut { }
        assertTrue(guard.endedByServer())
    }

    @Test fun `the guard stops after a failed sign-out of the app`() = runTest {
        remembered = "auth-1"
        runCatching { guard.ownSignOut<Unit> { error("network") } }
        assertTrue(guard.endedByServer())
    }
}
