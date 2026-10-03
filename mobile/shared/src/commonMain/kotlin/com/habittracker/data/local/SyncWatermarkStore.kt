package com.habittracker.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SyncTable(val key: String) {
    HABITS("habits"),
    HABIT_LOGS("habit_logs"),
    WANT_ACTIVITIES("want_activities"),
    WANT_LOGS("want_logs"),
    USER_IDENTITIES("user_identities"),
    HABIT_IDENTITIES("habit_identities"),
}

/** What the first sync on this device has pulled so far. */
data class PullProgress(
    /** Tables whose whole history is local. */
    val tables: Set<SyncTable> = emptySet(),
    /** Habit logs and want logs of the last 7 days are local. */
    val recentLogs: Boolean = false,
)

/** Watermark read/write surface used by SyncEngine. Allows in-memory test impls. */
interface WatermarkReader {
    fun get(table: SyncTable): Long
    fun set(table: SyncTable, valueMs: Long)

    /** Which tables have finished their first pull. Survives restarts. */
    val progress: StateFlow<PullProgress>
    fun markPulled(table: SyncTable)
    fun markRecentLogsPulled()
}

/** Persists the most recent pulled server timestamp per sync table. */
class SyncWatermarkStore(private val prefs: SyncPreferences) : WatermarkReader {
    private val _progress = MutableStateFlow(load())
    override val progress: StateFlow<PullProgress> = _progress.asStateFlow()

    override fun get(table: SyncTable): Long = prefs.getLong(prefixed(table))
    override fun set(table: SyncTable, valueMs: Long) = prefs.putLong(prefixed(table), valueMs)

    override fun markPulled(table: SyncTable) {
        prefs.putLong(pulledKey(table), 1L)
        _progress.update { it.copy(tables = it.tables + table) }
    }

    override fun markRecentLogsPulled() {
        prefs.putLong(RECENT_LOGS_KEY, 1L)
        _progress.update { it.copy(recentLogs = true) }
    }

    /** Reset all watermarks and pull flags to 0 — call after wiping local data so the next pull
     *  fetches everything from the server (otherwise pull skips rows that
     *  arrived before the cached watermark and Home stays empty). */
    fun reset() {
        SyncTable.entries.forEach { table ->
            prefs.putLong(prefixed(table), 0L)
            prefs.putLong(pulledKey(table), 0L)
        }
        prefs.putLong(RECENT_LOGS_KEY, 0L)
        _progress.value = PullProgress()
    }

    private fun load(): PullProgress {
        if (prefs.getLong(FLAGS_KEY) == 0L) {
            prefs.putLong(FLAGS_KEY, 1L)
            // An install from before the flags existed: a watermark means it synced
            // in full before, so every table counts, empty ones too.
            if (SyncTable.entries.any { get(it) > 0L }) {
                SyncTable.entries.forEach { prefs.putLong(pulledKey(it), 1L) }
                prefs.putLong(RECENT_LOGS_KEY, 1L)
            }
        }
        return PullProgress(
            tables = SyncTable.entries.filter { prefs.getLong(pulledKey(it)) == 1L }.toSet(),
            recentLogs = prefs.getLong(RECENT_LOGS_KEY) == 1L,
        )
    }

    private fun prefixed(table: SyncTable) = "watermark.${table.key}"
    private fun pulledKey(table: SyncTable) = "pulled.${table.key}"

    private companion object {
        const val RECENT_LOGS_KEY = "pulled.recent_logs"

        /** Set once this version has run, so the pre-flags check in [load] runs only once. */
        const val FLAGS_KEY = "pulled.flags_version"
    }
}
