package com.habittracker.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

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
        client.auth.signOut()
    }

    override suspend fun tryRefreshSession(): Result<Unit> = runCatching {
        client.auth.refreshCurrentSession()
    }

    override fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id

    override fun currentEmail(): String? =
        client.auth.currentSessionOrNull()?.user?.email

    override fun isLoggedIn(): Boolean =
        client.auth.currentSessionOrNull() != null

    override val noSession: Flow<Unit> =
        client.auth.sessionStatus.filter { it.meansNoSession() }.map { }

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
