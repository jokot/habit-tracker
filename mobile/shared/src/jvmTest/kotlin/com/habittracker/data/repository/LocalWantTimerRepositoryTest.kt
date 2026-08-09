package com.habittracker.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalWantTimerRepositoryTest {

    private lateinit var db: HabitTrackerDatabase
    private lateinit var repo: LocalWantTimerRepository

    @BeforeTest fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HabitTrackerDatabase.Schema.create(driver)
        db = HabitTrackerDatabase(driver)
        repo = LocalWantTimerRepository(db)
    }

    @Test fun `insert then getActive returns inserted RUNNING timer`() = runTest {
        val t = WantTimer(
            id = "t1", userId = "u1", activityId = "a1",
            durationSec = 600,
            startedAt = Instant.fromEpochSeconds(1000),
            endsAt = Instant.fromEpochSeconds(1600),
            state = WantTimerState.RUNNING,
        )
        repo.insert(t)
        assertEquals(t, repo.getActive("u1"))
    }

    @Test fun `startReplacing cancels previous RUNNING timer`() = runTest {
        val t1 = WantTimer("t1", "u1", "a1", 600,
            Instant.fromEpochSeconds(1000), Instant.fromEpochSeconds(1600), WantTimerState.RUNNING)
        val t2 = WantTimer("t2", "u1", "a2", 300,
            Instant.fromEpochSeconds(2000), Instant.fromEpochSeconds(2300), WantTimerState.RUNNING)
        repo.insert(t1)
        repo.startReplacing(t2)
        assertEquals(t2, repo.getActive("u1"))
        assertEquals(WantTimerState.CANCELLED, repo.getById("t1")?.state)
    }

    @Test fun `getActive returns null when none running`() = runTest {
        assertNull(repo.getActive("u1"))
    }

    @Test fun `transition to FINISHED is persisted`() = runTest {
        val t = WantTimer("t1", "u1", "a1", 600,
            Instant.fromEpochSeconds(1000), Instant.fromEpochSeconds(1600), WantTimerState.RUNNING)
        repo.insert(t)
        repo.setState("t1", WantTimerState.FINISHED)
        assertEquals(WantTimerState.FINISHED, repo.getById("t1")?.state)
        assertNull(repo.getActive("u1"))
    }
}
