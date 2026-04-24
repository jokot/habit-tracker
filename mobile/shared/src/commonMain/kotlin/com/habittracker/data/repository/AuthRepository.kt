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
    fun currentUserId(): String?
    fun isLoggedIn(): Boolean

    /** Suspends until the auth client has finished loading any persisted session from storage. */
    suspend fun awaitSessionRestored()
}
