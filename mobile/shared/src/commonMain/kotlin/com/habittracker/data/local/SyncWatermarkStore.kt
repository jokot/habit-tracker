package com.habittracker.data.local

enum class SyncTable(val key: String) {
    HABITS("habits"),
    HABIT_LOGS("habit_logs"),
    WANT_ACTIVITIES("want_activities"),
    WANT_LOGS("want_logs"),
}

/** Persists the most recent pulled server timestamp per sync table. */
class SyncWatermarkStore(private val prefs: SyncPreferences) {
    fun get(table: SyncTable): Long = prefs.getLong(prefixed(table))
    fun set(table: SyncTable, valueMs: Long) = prefs.putLong(prefixed(table), valueMs)

    private fun prefixed(table: SyncTable) = "watermark.${table.key}"
}
