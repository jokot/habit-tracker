package com.habittracker.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession as SupabaseSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupabaseSignOutTest {

    private val client = createSupabaseClient("https://fake.supabase.co", "anon-key") {
        // Every request fails, as on a phone in airplane mode.
        httpEngine = MockEngine { throw RuntimeException("Unable to resolve host") }
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            alwaysAutoRefresh = false
            enableLifecycleCallbacks = false
        }
    }
    private val repo = SupabaseAuthRepository(client)

    @Test
    fun `an offline sign-out still removes the session from the phone`() = runTest {
        val session = SupabaseSession(accessToken = "a", refreshToken = "r", expiresIn = 3600, tokenType = "bearer")
        client.auth.importSession(session, autoRefresh = false)
        assertTrue(repo.isLoggedIn())

        val result = repo.signOut()

        assertTrue(result.isSuccess, "sign-out failed: ${result.exceptionOrNull()}")
        assertTrue(client.auth.sessionStatus.value is SessionStatus.NotAuthenticated)
        assertNull(client.auth.sessionManager.loadSession())
        assertTrue(!repo.isLoggedIn())
    }
}
