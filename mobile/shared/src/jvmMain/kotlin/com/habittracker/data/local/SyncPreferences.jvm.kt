package com.habittracker.data.local

actual class SyncPreferences {
    private val map = mutableMapOf<String, Long>()
    actual fun getLong(key: String): Long = map.getOrDefault(key, 0L)
    actual fun putLong(key: String, value: Long) { map[key] = value }
}
