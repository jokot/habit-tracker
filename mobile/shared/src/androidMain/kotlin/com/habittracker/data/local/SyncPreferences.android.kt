package com.habittracker.data.local

import android.content.Context

actual class SyncPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE,
    )

    actual fun getLong(key: String): Long = prefs.getLong(key, 0L)
    actual fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "habit_tracker_sync_prefs"
    }
}
