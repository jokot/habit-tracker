package com.habittracker.data.repository

data class UserSession(
    val userId: String,
    val email: String,
)

interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<SignUpResult>
    suspend fun signIn(email: String, password: String): Result<UserSession>
    suspend fun signOut(): Result<Unit>
    fun currentUserId(): String?
    fun isLoggedIn(): Boolean
}
