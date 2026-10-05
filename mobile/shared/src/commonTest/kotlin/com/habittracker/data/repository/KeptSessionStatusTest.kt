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
        assertEquals(SessionReadiness.LIVE, MutableStateFlow<SessionStatus>(loaded).awaitReadiness(loadSession = {}))
    }

    @Test
    fun `an offline refresh is a refresh failure`() = runTest {
        assertEquals(SessionReadiness.REFRESH_FAILED, MutableStateFlow<SessionStatus>(offline).awaitReadiness(loadSession = {}))
    }

    @Test
    fun `after a resume the readiness waits for the refresh`() = runTest {
        // supabase-kt sets Initializing when the app stops, and refreshes the token at the next start.
        val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        var readiness: SessionReadiness? = null
        launch { readiness = status.awaitReadiness(loadSession = {}) }
        advanceTimeBy(2_000)
        assertNull(readiness)
        status.value = loaded
        advanceTimeBy(1)
        assertEquals(SessionReadiness.LIVE, readiness)
    }

    @Test
    fun `a session that stays initializing is loaded from storage`() = runTest {
        // In the background, supabase-kt does not load the session again. A widget sync must load it.
        val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        var loads = 0
        val readiness = status.awaitReadiness(loadSession = { loads++; status.value = loaded })
        assertEquals(SessionReadiness.LIVE, readiness)
        assertEquals(1, loads)
        assertEquals(10_000, testScheduler.currentTime)
    }

    @Test
    fun `a session that settles in time is not loaded again`() = runTest {
        val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        var loads = 0
        launch { advanceTimeBy(3_000); status.value = loaded }
        assertEquals(SessionReadiness.LIVE, status.awaitReadiness(loadSession = { loads++ }))
        assertEquals(0, loads)
    }

    @Test
    fun `a session that does not load from storage either is unavailable`() = runTest {
        val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        assertEquals(SessionReadiness.UNAVAILABLE, status.awaitReadiness(loadSession = {}))
        assertEquals(20_000, testScheduler.currentTime)
    }

    private fun offlineAgain() =
        SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(Exception("still offline")))

    @Test
    fun `after a reconnect a background sync waits for the next refresh`() = runTest {
        // supabase-kt retries a failed refresh every 10 s. A job that starts on reconnect is first.
        val status = MutableStateFlow<SessionStatus>(offline)
        launch { advanceTimeBy(4_000); status.value = loaded }
        assertEquals(SessionReadiness.LIVE, status.awaitReadiness(loadSession = {}, waitForRefresh = true))
    }

    @Test
    fun `a background sync fails when the next refresh fails too`() = runTest {
        val status = MutableStateFlow<SessionStatus>(offline)
        launch { advanceTimeBy(10_000); status.value = offlineAgain() }
        assertEquals(SessionReadiness.REFRESH_FAILED, status.awaitReadiness(loadSession = {}, waitForRefresh = true))
        assertEquals(10_000, testScheduler.currentTime)
    }

    @Test
    fun `a background sync waits at most 15 s for the next refresh`() = runTest {
        val status = MutableStateFlow<SessionStatus>(offline)
        assertEquals(SessionReadiness.REFRESH_FAILED, status.awaitReadiness(loadSession = {}, waitForRefresh = true))
        assertEquals(15_000, testScheduler.currentTime)
    }

    @Test
    fun `a manual sync does not wait for the next refresh`() = runTest {
        // Pull to refresh offline must show "Server unreachable" at once.
        val status = MutableStateFlow<SessionStatus>(offline)
        assertEquals(SessionReadiness.REFRESH_FAILED, status.awaitReadiness(loadSession = {}, waitForRefresh = false))
        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun `no session is unavailable`() = runTest {
        assertEquals(SessionReadiness.UNAVAILABLE, MutableStateFlow<SessionStatus>(noSession).awaitReadiness(loadSession = {}))
    }
}
