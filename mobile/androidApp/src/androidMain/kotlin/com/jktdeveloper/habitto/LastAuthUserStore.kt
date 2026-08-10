package com.jktdeveloper.habitto

import android.content.Context

/**
 * Which user id the local rows belong to, surviving process death.
 *
 * supabase-kt loads the persisted session asynchronously, and once the access token has
 * expired — every cold start after an hour — it has to reach the network before it will
 * admit to having a session at all. A widget update, a reminder worker or a widget tap
 * runs long before that settles, and offline it never settles: the status goes to
 * RefreshFailure and currentSessionOrNull() stays null. Falling straight through to the
 * guest UUID then queries an id that owns no rows, because signing in migrated them all
 * onto the auth id — which is the empty widget. The last id we actually saw is the right
 * answer until a sign-out says otherwise.
 *
 * Known limit: if the refresh token is revoked server-side, the remembered id goes stale
 * until sync surfaces "Session expired" and clearAuthenticatedUserData wipes it. Showing
 * the user their own data in the meantime beats silently swapping them to a guest with
 * nothing in it.
 */
class LastAuthUserStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * [sessionUserId] is the live session's id, or null when no session is visible — which
     * means either "not loaded yet" or "guest", and nothing here can tell those apart, so
     * a remembered id wins. [guestId] is a lambda because it mints a UUID on first call: it
     * must not run for someone who has ever signed in.
     */
    fun resolve(sessionUserId: String?, guestId: () -> String): String {
        if (sessionUserId != null) {
            if (prefs.getString(KEY_LAST_AUTH_USER_ID, null) != sessionUserId) {
                prefs.edit().putString(KEY_LAST_AUTH_USER_ID, sessionUserId).apply()
            }
            return sessionUserId
        }
        return prefs.getString(KEY_LAST_AUTH_USER_ID, null) ?: guestId()
    }

    fun clear() {
        prefs.edit().remove(KEY_LAST_AUTH_USER_ID).apply()
    }

    private companion object {
        const val PREFS_NAME = "habit_tracker_auth"
        const val KEY_LAST_AUTH_USER_ID = "last_auth_user_id"
    }
}
