package com.habittracker.data.remote

actual class GoogleSignInLauncher {
    actual suspend fun requestIdToken(): Result<String> =
        Result.failure(UnsupportedOperationException("Google sign-in not available on JVM"))
}
