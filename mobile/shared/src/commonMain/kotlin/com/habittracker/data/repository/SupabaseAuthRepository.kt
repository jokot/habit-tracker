package com.habittracker.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

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

    override suspend fun signOut(): Result<Unit> = runCatching {
        client.auth.signOut()
    }

    override fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id

    override fun isLoggedIn(): Boolean =
        client.auth.currentSessionOrNull() != null
}
