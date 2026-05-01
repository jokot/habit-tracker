package com.habittracker.data.repository

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FakeAuthRepository(
    private val shouldFailAuth: Boolean = false,
    var refreshSucceeds: Boolean = true,
) : AuthRepository {

    private var session: UserSession? = null

    var confirmationRequiredOnNext: Boolean = false

    override suspend fun signUp(email: String, password: String): Result<SignUpResult> {
        if (shouldFailAuth) return Result.failure(Exception("Auth failed"))
        if (confirmationRequiredOnNext) {
            confirmationRequiredOnNext = false
            return Result.success(SignUpResult.ConfirmationRequired(email))
        }
        val s = UserSession(userId = Uuid.random().toString(), email = email)
        session = s
        return Result.success(SignUpResult.SignedIn(s))
    }

    override suspend fun signIn(email: String, password: String): Result<UserSession> {
        if (shouldFailAuth) return Result.failure(Exception("Auth failed"))
        val s = UserSession(userId = Uuid.random().toString(), email = email)
        session = s
        return Result.success(s)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<UserSession> {
        if (shouldFailAuth) return Result.failure(Exception("Auth failed"))
        val s = UserSession(userId = "google-fake-$idToken", email = "google@fake.test")
        session = s
        return Result.success(s)
    }

    override suspend fun signOut(): Result<Unit> {
        session = null
        return Result.success(Unit)
    }

    override suspend fun tryRefreshSession(): Result<Unit> {
        if (session == null) return Result.failure(IllegalStateException("No session"))
        return if (refreshSucceeds) Result.success(Unit)
        else Result.failure(Exception("Refresh failed"))
    }

    override fun currentUserId(): String? = session?.userId

    override fun currentEmail(): String? = session?.email

    override fun isLoggedIn(): Boolean = session != null

    override suspend fun awaitSessionRestored() {
        // No storage; nothing to wait for.
    }
}
