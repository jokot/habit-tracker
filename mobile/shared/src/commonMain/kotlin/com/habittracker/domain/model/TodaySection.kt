package com.habittracker.domain.model

import com.habittracker.data.local.PullProgress
import com.habittracker.data.local.SyncTable

/** The parts of Today that load on their own. */
enum class TodaySection { IDENTITIES, HABITS, POINTS, STREAK, WANTS }

/**
 * Sections whose data is all local. A section never shows a number from part of
 * its data: the streak counts from the first log ever, so it waits for the whole
 * history, and so do wants, whose spend rate comes from the streak.
 */
fun PullProgress.readySections(isAuthenticated: Boolean): Set<TodaySection> {
    if (!isAuthenticated) return TodaySection.entries.toSet()
    val history = SyncTable.HABIT_LOGS in tables
    val recent = recentLogs || (history && SyncTable.WANT_LOGS in tables)
    return buildSet {
        if (SyncTable.USER_IDENTITIES in tables) add(TodaySection.IDENTITIES)
        if (SyncTable.HABITS in tables && recent) {
            add(TodaySection.HABITS)
            add(TodaySection.POINTS)
        }
        if (SyncTable.HABITS in tables && history) add(TodaySection.STREAK)
        if (SyncTable.WANT_ACTIVITIES in tables && recent && history) add(TodaySection.WANTS)
    }
}
