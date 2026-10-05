package com.habittracker.data.repository

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession as SupabaseSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeptSessionStatusTest {

    private val loaded = SessionStatus.Authenticated(
        SupabaseSession(accessToken = "a", refreshToken = "r", expiresIn = 3600, tokenType = "bearer"),
        SessionSource.Storage,
    )
    private val offline = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(Exception("offline")))
    private val noSession = SessionStatus.NotAuthenticated(isSignOut = false)

    @Test
    fun `a loaded session keeps the user signed in`() {
        assertTrue(loaded.keepsSession())
    }

    @Test
    fun `an offline refresh keeps the user signed in`() {
        // An expired token at an offline cold start gives RefreshFailure, not a sign-out.
        assertTrue(offline.keepsSession())
    }

    @Test
    fun `no session or a session that is not loaded yet does not sign in the user`() {
        assertFalse(noSession.keepsSession())
        assertFalse(SessionStatus.Initializing.keepsSession())
    }

    @Test
    fun `only a loaded session can send requests`() {
        assertTrue(loaded.isLive())
        assertFalse(offline.isLive())
        assertFalse(noSession.isLive())
        assertFalse(SessionStatus.Initializing.isLive())
    }

    @Test
    fun `a successful refresh after an offline refresh is a recovery`() = runTest {
        val statuses = flowOf(SessionStatus.Initializing, offline, loaded)
        assertEquals(1, statuses.recoveries().toList().size)
    }

    @Test
    fun `a recovery also counts when the app stopped between the failure and the refresh`() = runTest {
        val statuses = flowOf(offline, SessionStatus.Initializing, loaded)
        assertEquals(1, statuses.recoveries().toList().size)
    }

    @Test
    fun `a normal start or a token refresh is not a recovery`() = runTest {
        val statuses = flowOf(SessionStatus.Initializing, loaded, SessionStatus.Initializing, loaded)
        assertEquals(0, statuses.recoveries().toList().size)
    }

    @Test
    fun `a sign-in after a rejected refresh is not a recovery`() = runTest {
        val statuses = flowOf(offline, noSession, loaded)
        assertEquals(0, statuses.recoveries().toList().size)
    }

    @Test
    fun `a loaded session is ready at once`() = runTest {
        assertEquals(SessionReadiness.LIVE, MutableStateFlow<SessionStatus>(loaded).awaitReadiness())
    }

    @Test
    fun `an offline refresh is a refresh failure`() = runTest {
        assertEquals(SessionReadiness.REFRESH_FAILED, MutableStateFlow<SessionStatus>(offline).awaitReadiness())
    }

    @Test
    fun `after a resume the readiness waits for the refresh`() = runTest {
        // supabase-kt sets Initializing when the app stops, and refreshes the token at the next start.
        val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        var readiness: SessionReadiness? = null
        launch { readiness = status.awaitReadiness() }
        advanceTimeBy(2_000)
        assertNull(readiness)
        status.value = loaded
        advanceTimeBy(1)
        assertEquals(SessionReadiness.LIVE, readiness)
    }

    @Test
    fun `a session that does not load in 10 s is unavailable`() = runTest {
        // In the background, supabase-kt does not load the session again.
        val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        assertEquals(SessionReadiness.UNAVAILABLE, status.awaitReadiness())
        assertEquals(10_000, testScheduler.currentTime)
    }

    @Test
    fun `no session is unavailable`() = runTest {
        assertEquals(SessionReadiness.UNAVAILABLE, MutableStateFlow<SessionStatus>(noSession).awaitReadiness())
    }
}
