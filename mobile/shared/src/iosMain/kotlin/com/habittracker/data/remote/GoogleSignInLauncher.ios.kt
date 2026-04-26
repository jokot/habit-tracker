package com.habittracker.data.remote

actual class GoogleSignInLauncher {
    actual suspend fun requestIdToken(): Result<String> =
        Result.failure(UnsupportedOperationException("Google sign-in on iOS lands in Phase 6"))
}
