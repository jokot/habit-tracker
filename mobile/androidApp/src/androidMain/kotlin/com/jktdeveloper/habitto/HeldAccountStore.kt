package com.jktdeveloper.habitto

import android.content.Context

/** A user whose session expired while this phone had unsynced changes for that user. */
data class HeldAccount(val userId: String, val email: String?)

/**
 * Remembers the one held account across process death. A sign-in always resolves the
 * hold, so the store never needs more than one. See [HeldAccounts].
 */
class HeldAccountStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): HeldAccount? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        return HeldAccount(userId, prefs.getString(KEY_EMAIL, null))
    }

    fun hold(account: HeldAccount) {
        prefs.edit()
            .putString(KEY_USER_ID, account.userId)
            .putString(KEY_EMAIL, account.email)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "held_account"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
    }
}
