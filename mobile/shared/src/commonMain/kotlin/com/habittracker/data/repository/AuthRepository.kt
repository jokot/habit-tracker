package com.habittracker.data.repository

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
    fun isLoggedIn(): Boolean

    /** Suspends until the auth client has finished loading any persisted session from storage. */
    suspend fun awaitSessionRestored()
}
