package com.habittracker.data.remote

/** Platform bridge for Google OAuth. Android impl uses Credentials Manager; iOS is stub for Phase 6. */
expect class GoogleSignInLauncher {
    /** Launch the native Google sign-in flow. Returns an ID token on success. */
    suspend fun requestIdToken(): Result<String>
}
