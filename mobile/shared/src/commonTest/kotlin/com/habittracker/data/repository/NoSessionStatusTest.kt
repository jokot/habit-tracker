package com.habittracker.data.repository

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession as SupabaseSession
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoSessionStatusTest {

    @Test
    fun `no stored session at start means no session`() {
        assertTrue(SessionStatus.NotAuthenticated(isSignOut = false).meansNoSession())
    }

    @Test
    fun `a rejected refresh or a sign-out means no session`() {
        assertTrue(SessionStatus.NotAuthenticated(isSignOut = true).meansNoSession())
    }

    @Test
    fun `an app in the background still has its session`() {
        // supabase-kt sets Initializing when the app stops, and loads the session again at start.
        assertFalse(SessionStatus.Initializing.meansNoSession())
    }

    @Test
    fun `an offline refresh keeps the session`() {
        val offline = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(Exception("offline")))
        assertFalse(offline.meansNoSession())
    }

    @Test
    fun `a loaded session is a session`() {
        val session = SupabaseSession(accessToken = "a", refreshToken = "r", expiresIn = 3600, tokenType = "bearer")
        assertFalse(SessionStatus.Authenticated(session, SessionSource.Storage).meansNoSession())
    }
}
