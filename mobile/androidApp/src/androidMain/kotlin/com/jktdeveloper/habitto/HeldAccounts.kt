package com.jktdeveloper.habitto

import android.util.Log

/** What the Auth screen shows about a held account. A null count means the count failed. */
data class HeldAccountSummary(val email: String?, val unsyncedCount: Int?)

/**
 * Decides what happens to the local rows of a user whose session expired. The token
 * refresh failed, so no push is possible. Unsynced changes stay on the phone until the
 * next sign-in (#33). See docs/superpowers/specs/2026-10-04-session-expiry-keeps-unsynced-design.md.
 */
class HeldAccounts(
    private val store: HeldAccountStore,
    private val countUnsynced: suspend (userId: String) -> Int,
    private val deleteUserRows: suspend (userId: String) -> Unit,
) {
    /**
     * Returns the unsynced count of [userId]. With 0 the rows are deleted. Otherwise the
     * rows stay and the user is held. Null means the count failed, and the rows stay.
     */
    suspend fun onSessionExpired(userId: String, email: String?): Int? {
        val count = runCatching { countUnsynced(userId) }
            .onFailure { e -> Log.w(TAG, "Could not count unsynced changes", e) }
            .getOrNull()
        if (count == 0) {
            runCatching { deleteUserRows(userId) }
                .onFailure { e -> Log.w(TAG, "Could not delete the rows of an expired session", e) }
            return 0
        }
        store.hold(HeldAccount(userId, email))
        return count
    }

    /**
     * Resolves the hold at sign-in. The same user keeps the rows, and the next push sends
     * them. Another user cannot push them, so they are deleted. Returns true when this
     * user kept rows: the caller must then treat the user as an existing user.
     */
    suspend fun onSignedIn(userId: String): Boolean {
        val held = store.get() ?: return false
        val kept = held.userId == userId
        if (!kept) {
            val deleted = runCatching { deleteUserRows(held.userId) }
                .onFailure { e -> Log.w(TAG, "Could not delete the rows of a held account", e) }
                .isSuccess
            // Keep the hold, so the next sign-in tries the delete again.
            if (!deleted) return false
        }
        store.clear()
        return kept
    }

    suspend fun summary(): HeldAccountSummary? {
        val held = store.get() ?: return null
        return HeldAccountSummary(held.email, runCatching { countUnsynced(held.userId) }.getOrNull())
    }

    private companion object {
        const val TAG = "HeldAccounts"
    }
}
