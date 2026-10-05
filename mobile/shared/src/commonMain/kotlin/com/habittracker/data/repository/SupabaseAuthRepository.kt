package com.habittracker.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

class SupabaseAuthRepository(
    private val client: SupabaseClient,
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

    override suspend fun awaitSessionReadiness(): SessionReadiness =
        client.auth.sessionStatus.awaitReadiness()

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

/** supabase-kt sets Initializing while it loads or refreshes the session. */
internal suspend fun Flow<SessionStatus>.awaitReadiness(): SessionReadiness {
    val settled = withTimeoutOrNull(SESSION_LOAD_TIMEOUT_MS) {
        first { it !is SessionStatus.Initializing }
    }
    return when {
        settled == null -> SessionReadiness.UNAVAILABLE
        settled.isLive() -> SessionReadiness.LIVE
        settled is SessionStatus.RefreshFailure -> SessionReadiness.REFRESH_FAILED
        else -> SessionReadiness.UNAVAILABLE
    }
}

private const val SESSION_LOAD_TIMEOUT_MS = 10_000L
