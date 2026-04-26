package com.habittracker.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupabaseAuthRepositoryResultTest {

    @Test
    fun `signUp returns SignedIn when session is active`() = runTest {
        val repo = FakeAuthRepository()
        val result = repo.signUp("a@b.com", "password").getOrThrow()
        assertTrue(result is SignUpResult.SignedIn)
        assertEquals("a@b.com", (result as SignUpResult.SignedIn).session.email)
    }

    @Test
    fun `signUp returns ConfirmationRequired when no session`() = runTest {
        val repo = FakeAuthRepository()
        repo.confirmationRequiredOnNext = true
        val result = repo.signUp("a@b.com", "password").getOrThrow()
        assertTrue(result is SignUpResult.ConfirmationRequired)
        assertEquals("a@b.com", (result as SignUpResult.ConfirmationRequired).email)
    }
}
