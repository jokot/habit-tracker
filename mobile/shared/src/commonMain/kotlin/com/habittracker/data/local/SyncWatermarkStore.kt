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

    /** Reset all watermarks to 0 — call after wiping local data so the next pull
     *  fetches everything from the server (otherwise pull skips rows that
     *  arrived before the cached watermark and Home stays empty). */
    fun reset() {
        SyncTable.entries.forEach { table -> prefs.putLong(prefixed(table), 0L) }
    }

    private fun prefixed(table: SyncTable) = "watermark.${table.key}"
}
