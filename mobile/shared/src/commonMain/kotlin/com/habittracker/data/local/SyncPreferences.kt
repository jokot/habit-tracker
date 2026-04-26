package com.habittracker.data.local

/** Platform key/value persistence for small primitives. */
expect class SyncPreferences {
    fun getLong(key: String): Long
    fun putLong(key: String, value: Long)
}
