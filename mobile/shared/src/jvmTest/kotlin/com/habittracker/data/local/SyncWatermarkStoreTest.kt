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

    @Test fun `a watermark above zero counts as pulled, for upgraded installs`() {
        val prefs = SyncPreferences()
        SyncWatermarkStore(prefs).set(SyncTable.HABIT_LOGS, 5L)
        assertEquals(setOf(SyncTable.HABIT_LOGS), SyncWatermarkStore(prefs).progress.value.tables)
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
