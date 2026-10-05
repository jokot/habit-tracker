package com.habittracker.data.repository

import kotlinx.coroutines.flow.Flow

data class UserSession(
    val userId: String,
    val email: String,
)

interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<SignUpResult>
    suspend fun signIn(email: String, password: String): Result<UserSession>
    suspend fun signInWithGoogle(idToken: String): Result<UserSession>
    suspend fun signOut(): Result<Unit>

    /**
     * Attempts to refresh the current session via the underlying auth client.
     * Returns success when refresh completed (a fresh JWT is now active).
     * Returns failure if there is no session, the refresh token is invalid,
     * or the network call failed. Callers must treat failure as expired.
     */
    suspend fun tryRefreshSession(): Result<Unit>

    fun currentUserId(): String?
    fun currentEmail(): String?
    /** True while a session exists. An offline refresh keeps the session. */
    fun isLoggedIn(): Boolean

    /**
     * True when the session has a token that a request can send. False after an offline
     * refresh failed, although [isLoggedIn] is still true.
     */
    fun hasLiveSession(): Boolean

    /** Emits when a refresh succeeds after an offline refresh failed. */
    val sessionRecovered: Flow<Unit>

    /**
     * Waits at most 10 s for the auth client to load the session, then tells if a request
     * can use it. After a resume, supabase-kt refreshes an expired token before it is ready.
     */
    suspend fun awaitSessionReadiness(): SessionReadiness

    /** Suspends until the auth client has finished loading any persisted session from storage. */
    suspend fun awaitSessionRestored()

    /**
     * Emits when the auth client has no session: no stored session at start, a refresh
     * that the server rejected, or a sign-out. It does not emit while the app is in the
     * background, or when a refresh fails because the phone is offline.
     */
    val noSession: Flow<Unit>

    /** True while the auth client has no session, with the same rules as [noSession]. */
    fun hasNoSession(): Boolean
}

/** What the session can give a request to the server. */
enum class SessionReadiness {
    /** A valid token. */
    LIVE,

    /** The session exists, but the auth client could not refresh the token. */
    REFRESH_FAILED,

    /** No session, or the auth client did not load it. The app is in the background then. */
    UNAVAILABLE,
}
