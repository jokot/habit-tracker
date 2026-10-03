package com.habittracker.data.repository

import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantLog
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A pulled page of logs must reach observers as one change. One change per row
 * makes Home recompute its whole history once per row while the user scrolls.
 */
class MergePulledBatchTest {

    private lateinit var db: HabitTrackerDatabase

    @BeforeTest fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HabitTrackerDatabase.Schema.create(driver)
        db = HabitTrackerDatabase(driver)
    }

    @Test fun `merging a batch of habit logs notifies observers once`() = runTest {
        val repo = LocalHabitLogRepository(db)
        var changes = 0
        db.habitTrackerDatabaseQueries.getAllActiveHabitLogsForUser("u1")
            .addListener(Query.Listener { changes++ })

        repo.mergePulledAll((1..500).map { habitLog("l$it", it) })

        assertEquals(1, changes)
        assertEquals(500, repo.getAllActiveLogsForUser("u1").size)
    }

    @Test fun `merging a batch of want logs notifies observers once`() = runTest {
        val repo = LocalWantLogRepository(db)
        var changes = 0
        db.habitTrackerDatabaseQueries.getAllActiveWantLogsForUser("u1")
            .addListener(Query.Listener { changes++ })

        repo.mergePulledAll((1..500).map { wantLog("w$it", it) })

        assertEquals(1, changes)
        assertEquals(500, repo.getAllActiveLogsForUser("u1").size)
    }

    private fun habitLog(id: String, hour: Int) = HabitLog(
        id, "u1", "h1", 1.0, Instant.fromEpochSeconds(hour * 3600L),
        syncedAt = Instant.fromEpochSeconds(1),
    )

    private fun wantLog(id: String, hour: Int) = WantLog(
        id, "u1", "a1", 1.0, 1, DeviceMode.THIS_DEVICE, Instant.fromEpochSeconds(hour * 3600L),
        syncedAt = Instant.fromEpochSeconds(1),
    )
}
