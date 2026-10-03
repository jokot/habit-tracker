package com.habittracker.domain.model

import com.habittracker.data.local.PullProgress
import com.habittracker.data.local.SyncTable
import com.habittracker.data.local.SyncTable.HABITS
import com.habittracker.data.local.SyncTable.HABIT_LOGS
import com.habittracker.data.local.SyncTable.USER_IDENTITIES
import com.habittracker.data.local.SyncTable.WANT_ACTIVITIES
import com.habittracker.data.local.SyncTable.WANT_LOGS
import com.habittracker.domain.model.TodaySection.IDENTITIES
import com.habittracker.domain.model.TodaySection.POINTS
import com.habittracker.domain.model.TodaySection.STREAK
import com.habittracker.domain.model.TodaySection.WANTS
import kotlin.test.Test
import kotlin.test.assertEquals

class TodaySectionTest {

    private fun ready(vararg tables: SyncTable, recentLogs: Boolean = false) =
        PullProgress(tables.toSet(), recentLogs).readySections(isAuthenticated = true)

    @Test fun `a guest sees every section at once`() {
        assertEquals(TodaySection.entries.toSet(), PullProgress().readySections(isAuthenticated = false))
    }

    @Test fun `a signed-in user with nothing pulled sees no section`() {
        assertEquals(emptySet(), ready())
    }

    @Test fun `identities need only the user identities`() {
        assertEquals(setOf(IDENTITIES), ready(USER_IDENTITIES))
    }

    @Test fun `habits and points need habits and the recent logs`() {
        assertEquals(setOf(TodaySection.HABITS, POINTS), ready(HABITS, recentLogs = true))
    }

    @Test fun `habits without recent logs are not ready`() {
        assertEquals(emptySet(), ready(HABITS))
    }

    @Test fun `streak needs habits and the full habit-log history`() {
        assertEquals(setOf(STREAK), ready(HABITS, HABIT_LOGS))
    }

    @Test fun `both full log tables count as recent logs`() {
        assertEquals(
            setOf(TodaySection.HABITS, POINTS, STREAK, WANTS),
            ready(HABITS, WANT_ACTIVITIES, HABIT_LOGS, WANT_LOGS),
        )
    }

    @Test fun `wants wait for the habit-log history, for the spend rate`() {
        assertEquals(emptySet(), ready(WANT_ACTIVITIES, recentLogs = true))
        assertEquals(setOf(WANTS), ready(WANT_ACTIVITIES, HABIT_LOGS, recentLogs = true))
    }
}
