package com.habittracker.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {

    @Test
    fun isLoggedIn_whenNoSession_returnsFalse() {
        val repo = FakeAuthRepository()
        assertFalse(repo.isLoggedIn())
    }

    @Test
    fun currentUserId_whenNoSession_returnsNull() {
        val repo = FakeAuthRepository()
        assertNull(repo.currentUserId())
    }

    @Test
    fun signIn_success_returnsSession() = runTest {
        val repo = FakeAuthRepository()
        val result = repo.signIn("user@example.com", "password123")
        assertTrue(result.isSuccess)
        assertEquals("user@example.com", result.getOrNull()?.email)
    }

    @Test
    fun isLoggedIn_afterSignIn_returnsTrue() = runTest {
        val repo = FakeAuthRepository()
        repo.signIn("user@example.com", "password123")
        assertTrue(repo.isLoggedIn())
    }

    @Test
    fun currentUserId_afterSignIn_returnsId() = runTest {
        val repo = FakeAuthRepository()
        repo.signIn("user@example.com", "password123")
        assertTrue(repo.currentUserId()?.isNotEmpty() == true)
    }

    @Test
    fun signOut_clearsSession() = runTest {
        val repo = FakeAuthRepository()
        repo.signIn("user@example.com", "password123")
        repo.signOut()
        assertFalse(repo.isLoggedIn())
        assertNull(repo.currentUserId())
    }

    @Test
    fun signIn_failure_returnsError() = runTest {
        val repo = FakeAuthRepository(shouldFailAuth = true)
        val result = repo.signIn("user@example.com", "wrongpassword")
        assertTrue(result.isFailure)
    }
}
