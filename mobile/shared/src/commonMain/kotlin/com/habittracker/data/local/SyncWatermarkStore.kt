package com.habittracker.data.local

enum class SyncTable(val key: String) {
    HABITS("habits"),
    HABIT_LOGS("habit_logs"),
    WANT_ACTIVITIES("want_activities"),
    WANT_LOGS("want_logs"),
}

/** Watermark read/write surface used by SyncEngine. Allows in-memory test impls. */
interface WatermarkReader {
    fun get(table: SyncTable): Long
    fun set(table: SyncTable, valueMs: Long)
}

/** Persists the most recent pulled server timestamp per sync table. */
class SyncWatermarkStore(private val prefs: SyncPreferences) : WatermarkReader {
    override fun get(table: SyncTable): Long = prefs.getLong(prefixed(table))
    override fun set(table: SyncTable, valueMs: Long) = prefs.putLong(prefixed(table), valueMs)

    private fun prefixed(table: SyncTable) = "watermark.${table.key}"
}
