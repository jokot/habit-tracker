package com.habittracker.data.local

import android.content.Context
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class LocalUserIdStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @OptIn(ExperimentalUuidApi::class)
    actual fun getOrCreate(): String {
        val existing = prefs.getString(KEY_LOCAL_USER_ID, null)
        if (existing != null) return existing
        val fresh = Uuid.random().toString()
        prefs.edit().putString(KEY_LOCAL_USER_ID, fresh).apply()
        return fresh
    }

    private companion object {
        const val PREFS_NAME = "habit_tracker_identity"
        const val KEY_LOCAL_USER_ID = "local_user_id"
    }
}
