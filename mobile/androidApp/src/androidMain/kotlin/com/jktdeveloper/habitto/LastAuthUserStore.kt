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
 * If the server revokes the refresh token, supabase-kt reports NotAuthenticated and
 * AppContainer ends the session, which clears the remembered id (#33). RefreshFailure
 * (offline) keeps it, so the offline widget still shows the user's own data. The user also
 * stays signed in: AuthRepository.isLoggedIn() reads the status, not the session.
 */
class LastAuthUserStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * [sessionUserId] is the live session's id, or null when no session is visible — which
     * means either "not loaded yet" or "guest", and nothing here can tell those apart, so
     * a remembered id wins. [guestId] is a lambda because it mints a UUID on first call: it
     * must not run for someone who has ever signed in. [sessionEmail] is the live session's
     * email, kept with the id for [lastEmail].
     */
    fun resolve(sessionUserId: String?, sessionEmail: String? = null, guestId: () -> String): String {
        if (sessionUserId != null) {
            val sameUser = prefs.getString(KEY_LAST_AUTH_USER_ID, null) == sessionUserId
            val email = sessionEmail ?: prefs.getString(KEY_LAST_AUTH_EMAIL, null).takeIf { sameUser }
            if (!sameUser || prefs.getString(KEY_LAST_AUTH_EMAIL, null) != email) {
                prefs.edit()
                    .putString(KEY_LAST_AUTH_USER_ID, sessionUserId)
                    .putString(KEY_LAST_AUTH_EMAIL, email)
                    .apply()
            }
            return sessionUserId
        }
        return prefs.getString(KEY_LAST_AUTH_USER_ID, null) ?: guestId()
    }

    /**
     * The email of the remembered id. supabase-kt clears the session itself when its
     * auto-refresh fails, so a session expiry often finds no email on the session (#33).
     */
    fun lastEmail(): String? = prefs.getString(KEY_LAST_AUTH_EMAIL, null)

    /** The id of the last authenticated user, or null after a sign-out or for a guest. */
    fun rememberedUserId(): String? = prefs.getString(KEY_LAST_AUTH_USER_ID, null)

    /**
     * A cold process (a widget tap, a sync job) starts before supabase-kt loads the session.
     * The remembered user is signed in until the session loads. A sign-out or a session end
     * clears the remembered id, and [sessionEnded] covers a session that the server ended.
     */
    fun isSignedIn(sessionLoggedIn: Boolean, sessionEnded: Boolean): Boolean =
        sessionLoggedIn || (!sessionEnded && rememberedUserId() != null)

    fun clear() {
        prefs.edit().remove(KEY_LAST_AUTH_USER_ID).remove(KEY_LAST_AUTH_EMAIL).apply()
    }

    private companion object {
        const val PREFS_NAME = "habit_tracker_auth"
        const val KEY_LAST_AUTH_USER_ID = "last_auth_user_id"
        const val KEY_LAST_AUTH_EMAIL = "last_auth_email"
    }
}
