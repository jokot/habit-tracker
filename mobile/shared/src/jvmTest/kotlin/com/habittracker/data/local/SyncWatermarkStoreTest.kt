package com.habittracker.data.local

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncWatermarkStoreTest {

    private val store = SyncWatermarkStore(SyncPreferences())

    @Test fun `a fresh store has nothing pulled`() {
        assertEquals(PullProgress(), store.progress.value)
    }

    @Test fun `markPulled adds the table to the flow`() {
        store.markPulled(SyncTable.HABITS)
        assertEquals(setOf(SyncTable.HABITS), store.progress.value.tables)
    }

    @Test fun `markRecentLogsPulled sets the flag`() {
        store.markRecentLogsPulled()
        assertEquals(true, store.progress.value.recentLogs)
    }

    @Test fun `progress survives a new store on the same preferences`() {
        val prefs = SyncPreferences()
        SyncWatermarkStore(prefs).apply {
            markPulled(SyncTable.WANT_ACTIVITIES)
            markRecentLogsPulled()
        }
        assertEquals(
            PullProgress(setOf(SyncTable.WANT_ACTIVITIES), recentLogs = true),
            SyncWatermarkStore(prefs).progress.value,
        )
    }

    @Test fun `an install from before the flags with a watermark counts as fully pulled`() {
        val prefs = SyncPreferences().apply { putLong("watermark.habit_logs", 5L) }
        assertEquals(
            PullProgress(SyncTable.entries.toSet(), recentLogs = true),
            SyncWatermarkStore(prefs).progress.value,
        )
    }

    @Test fun `an install from before the flags with no watermark has nothing pulled`() {
        assertEquals(PullProgress(), SyncWatermarkStore(SyncPreferences()).progress.value)
    }

    @Test fun `a first pull cut short by a restart does not count as pulled`() {
        val prefs = SyncPreferences()
        SyncWatermarkStore(prefs).apply {
            set(SyncTable.HABITS, 5L)
            markPulled(SyncTable.HABITS)
        }
        assertEquals(PullProgress(setOf(SyncTable.HABITS)), SyncWatermarkStore(prefs).progress.value)
    }

    @Test fun `reset is not mistaken for an install from before the flags`() {
        val prefs = SyncPreferences()
        SyncWatermarkStore(prefs).apply {
            set(SyncTable.HABITS, 5L)
            reset()
        }
        assertEquals(PullProgress(), SyncWatermarkStore(prefs).progress.value)
    }

    @Test fun `reset clears the watermarks, the flags and the recent-logs flag`() {
        store.markPulled(SyncTable.HABITS)
        store.markRecentLogsPulled()
        store.set(SyncTable.HABIT_LOGS, 9L)
        store.reset()
        assertEquals(PullProgress(), store.progress.value)
        assertEquals(0L, store.get(SyncTable.HABIT_LOGS))
    }
}
