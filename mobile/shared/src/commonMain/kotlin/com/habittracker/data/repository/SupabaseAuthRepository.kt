package com.habittracker.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SupabaseAuthRepository(
    private val client: SupabaseClient,
    /** False while the app has no visible screen. The sync then has less time to run. */
    private val isAppInForeground: () -> Boolean = { true },
) : AuthRepository {

    override suspend fun signUp(email: String, password: String): Result<SignUpResult> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val session = client.auth.currentSessionOrNull()
        if (session != null) {
            val user = session.user!!
            SignUpResult.SignedIn(UserSession(userId = user.id, email = user.email ?: email))
        } else {
            SignUpResult.ConfirmationRequired(email)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<UserSession> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val user = client.auth.currentSessionOrNull()?.user
            ?: error("Sign in returned no session")
        UserSession(userId = user.id, email = user.email ?: email)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<UserSession> = runCatching {
        client.auth.signInWith(IDToken) {
            provider = Google
            this.idToken = idToken
        }
        val user = client.auth.currentSessionOrNull()?.user
            ?: error("Google sign-in returned no session")
        UserSession(userId = user.id, email = user.email ?: "")
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        try {
            client.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // supabase-kt sends the logout request first, and it clears the session only after a
            // reply. Offline, the request fails and the user stays signed in. So clear it here.
            // The server session stays until its refresh token expires.
            client.auth.clearSession()
        }
    }

    override suspend fun tryRefreshSession(): Result<Unit> = runCatching {
        client.auth.refreshCurrentSession()
    }

    override fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id

    override fun currentEmail(): String? =
        client.auth.currentSessionOrNull()?.user?.email

    override fun isLoggedIn(): Boolean = client.auth.sessionStatus.value.keepsSession()

    override fun hasLiveSession(): Boolean = client.auth.sessionStatus.value.isLive()

    override val sessionRecovered: Flow<Unit> = client.auth.sessionStatus.recoveries()

    override suspend fun awaitSessionReadiness(waitForRefresh: Boolean): SessionReadiness =
        client.auth.sessionStatus.awaitReadiness(
            loadSession = { client.auth.loadFromStorage() },
            waitForRefresh = waitForRefresh,
            inForeground = isAppInForeground(),
        )

    override val noSession: Flow<Unit> =
        client.auth.sessionStatus.filter { it.meansNoSession() }.map { }

    override fun hasNoSession(): Boolean = client.auth.sessionStatus.value.meansNoSession()

    override suspend fun awaitSessionRestored() {
        // Wait for the first non-Initializing session status — session is either
        // Authenticated (loaded from storage) or NotAuthenticated (no session).
        client.auth.awaitInitialization()
    }
}

/**
 * Only NotAuthenticated means that supabase-kt deleted the session. It sets Initializing
 * when the app stops, and RefreshFailure when the phone is offline. Both keep the session.
 */
internal fun SessionStatus.meansNoSession(): Boolean = this is SessionStatus.NotAuthenticated

/**
 * An offline refresh keeps the stored session. supabase-kt then reports RefreshFailure, and
 * currentSessionOrNull() is null. The user is still signed in, but no request can use the token.
 */
internal fun SessionStatus.keepsSession(): Boolean =
    this is SessionStatus.Authenticated || this is SessionStatus.RefreshFailure

/** Only Authenticated gives a token that a request can send. */
internal fun SessionStatus.isLive(): Boolean = this is SessionStatus.Authenticated

/**
 * Emits when a refresh succeeds after an offline refresh failed. Initializing between the two
 * means only that the app stopped. A sign-out or a rejected refresh cancels the failure.
 */
internal fun Flow<SessionStatus>.recoveries(): Flow<Unit> = flow {
    var failed = false
    collect { status ->
        when (status) {
            is SessionStatus.RefreshFailure -> failed = true
            is SessionStatus.Authenticated -> {
                if (failed) emit(Unit)
                failed = false
            }
            is SessionStatus.NotAuthenticated -> failed = false
            SessionStatus.Initializing -> Unit
        }
    }
}

/**
 * supabase-kt sets Initializing while it loads or refreshes the session. It also sets it when
 * the app goes to the background, and it loads the session again only when the app comes back.
 * So a status that stays Initializing means that nothing loads the session: [loadSession] then
 * loads it from storage.
 *
 * In the foreground, a resume loads the session in less than 10 s, so a second load does not
 * occur. In the background, ColorOS freezes the app 5 s after a job starts, so the wait is 1 s
 * and the whole check ends in 4 s. A second load at a cold start refreshes the same token twice
 * at the same time, which the server accepts.
 */
internal suspend fun Flow<SessionStatus>.awaitReadiness(
    loadSession: suspend () -> Unit,
    waitForRefresh: Boolean = false,
    inForeground: Boolean = true,
): SessionReadiness {
    val wait = if (inForeground) FOREGROUND_LOAD_WAIT_MS else BACKGROUND_LOAD_WAIT_MS
    val afterLoad = if (inForeground) FOREGROUND_LOAD_WAIT_MS else BACKGROUND_AFTER_LOAD_MS
    var loaded = false
    val first = settled(wait) ?: coroutineScope {
        loaded = true
        // Offline, loadFromStorage() retries the refresh and does not return. So the load runs
        // beside the wait, and stops when the wait ends.
        val load = launch { loadSession() }
        settled(afterLoad).also { load.cancel() }
    }
    // supabase-kt retries a failed refresh every 10 s, and each failure is a new status. A job
    // that WorkManager starts on reconnect runs before that retry, so it waits for the result.
    // After a load of its own, no retry follows, because the load stopped.
    val settled = if (first is SessionStatus.RefreshFailure && waitForRefresh && !loaded) {
        withTimeoutOrNull(REFRESH_RETRY_WAIT_MS) { first { it != first } } ?: first
    } else {
        first
    }
    return when {
        settled == null -> SessionReadiness.UNAVAILABLE
        settled.isLive() -> SessionReadiness.LIVE
        settled is SessionStatus.RefreshFailure -> SessionReadiness.REFRESH_FAILED
        else -> SessionReadiness.UNAVAILABLE
    }
}

private suspend fun Flow<SessionStatus>.settled(timeoutMs: Long): SessionStatus? =
    withTimeoutOrNull(timeoutMs) { first { it !is SessionStatus.Initializing } }

private const val FOREGROUND_LOAD_WAIT_MS = 10_000L
private const val BACKGROUND_LOAD_WAIT_MS = 1_000L
private const val BACKGROUND_AFTER_LOAD_MS = 3_000L

/** One supabase-kt retry delay (10 s), plus time for the refresh request. */
private const val REFRESH_RETRY_WAIT_MS = 15_000L
