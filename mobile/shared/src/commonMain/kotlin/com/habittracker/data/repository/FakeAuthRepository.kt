package com.habittracker.data.repository

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FakeAuthRepository(
    private val shouldFailAuth: Boolean = false,
) : AuthRepository {

    private var session: UserSession? = null

    override suspend fun signUp(email: String, password: String): Result<UserSession> {
        if (shouldFailAuth) return Result.failure(Exception("Auth failed"))
        val s = UserSession(userId = Uuid.random().toString(), email = email)
        session = s
        return Result.success(s)
    }

    override suspend fun signIn(email: String, password: String): Result<UserSession> {
        if (shouldFailAuth) return Result.failure(Exception("Auth failed"))
        val s = UserSession(userId = Uuid.random().toString(), email = email)
        session = s
        return Result.success(s)
    }

    override suspend fun signOut(): Result<Unit> {
        session = null
        return Result.success(Unit)
    }

    override fun currentUserId(): String? = session?.userId

    override fun isLoggedIn(): Boolean = session != null
}
