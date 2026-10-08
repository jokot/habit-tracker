package com.habittracker.data.sync

import com.habittracker.data.local.PullProgress
import com.habittracker.data.local.SyncTable
import com.habittracker.data.local.WatermarkReader
import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.data.repository.FakeWantActivityRepository
import com.habittracker.data.repository.FakeWantLogRepository
import com.habittracker.data.repository.SessionReadiness
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncEngineTest {

    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()
    private val wantActivityRepo = FakeWantActivityRepository()
    private val wantLogRepo = FakeWantLogRepository()
    private val identityRepo = FakeIdentityRepository()
    private val supabase = FakeSupabaseSyncClient()
    private val watermarks = InMemoryWatermarks()
    private val auth = FakeAuthIdentity("user-1", authenticated = true)

    /** Saturday 2026-10-03, noon UTC. */
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    private val engine = SyncEngine(
        habitRepo, habitLogRepo, wantActivityRepo, wantLogRepo, identityRepo,
        supabase, watermarks, auth,
        clock = object : Clock { override fun now() = this@SyncEngineTest.now },
        timeZone = TimeZone.UTC,
    )

    private val t0: Instant = Clock.System.now()
    private fun tPlus(seconds: Int) = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() + seconds * 1000L)

    private fun makeHabit(id: String, updatedAt: Instant = t0) = Habit(
        id = id,
        userId = "user-1",
        templateId = "tpl",
        name = "Read",
        unit = "pages",
        thresholdPerPoint = 3.0,
        dailyTarget = 3,
        createdAt = t0,
        updatedAt = updatedAt,
        syncedAt = null,
    )

    @Test
    fun `push marks rows synced`() = runTest {
        habitRepo.saveHabit(makeHabit("h1"))
        val result = engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(1, result.pushed)
        assertNotNull(habitRepo.habits.first().syncedAt)
    }

    @Test
    fun `pull inserts new remote rows`() = runTest {
        supabase.habits.add(makeHabit("h1", updatedAt = tPlus(10)))
        val result = engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(1, result.pulled)
        assertEquals("h1", habitRepo.habits.first().id)
    }

    @Test
    fun `LWW overwrites local when remote updatedAt is newer`() = runTest {
        val local = makeHabit("h1", updatedAt = tPlus(1)).copy(name = "Old", syncedAt = tPlus(1))
        habitRepo.saveHabit(local)
        supabase.habits.add(local.copy(name = "New", updatedAt = tPlus(5)))
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals("New", habitRepo.habits.first().name)
    }

    @Test
    fun `pull no-ops when local updatedAt is newer`() = runTest {
        val local = makeHabit("h1", updatedAt = tPlus(10)).copy(name = "Local", syncedAt = tPlus(10))
        habitRepo.saveHabit(local)
        supabase.habits.add(local.copy(name = "Stale", updatedAt = tPlus(5)))
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals("Local", habitRepo.habits.first().name)
    }

    @Test
    fun `soft delete merge applies remote tombstone`() = runTest {
        val log = HabitLog("l1", "user-1", "h1", 3.0, tPlus(1))
        habitLogRepo.insertLog(log.id, log.userId, log.habitId, log.quantity, log.loggedAt)
        // mark local as synced so push doesn't re-send
        habitLogRepo.markSynced(log.id, tPlus(1))
        supabase.habitLogs.add(log.copy(deletedAt = tPlus(5), syncedAt = tPlus(5)))
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(false, habitLogRepo.logs.first { it.id == "l1" }.isActive)
    }

    @Test
    fun `idempotent — back-to-back sync calls push 0 on second run`() = runTest {
        habitRepo.saveHabit(makeHabit("h1"))
        engine.sync(SyncReason.MANUAL).getOrThrow()
        val second = engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(0, second.pushed)
    }

    @Test
    fun `unauthenticated sync returns zero without touching network`() = runTest {
        val offlineAuth = FakeAuthIdentity("user-1", authenticated = false)
        val offlineEngine = SyncEngine(
            habitRepo, habitLogRepo, wantActivityRepo, wantLogRepo, identityRepo,
            supabase, watermarks, offlineAuth,
        )
        habitRepo.saveHabit(makeHabit("h1"))
        val result = offlineEngine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(0, result.pushed)
        assertEquals(0, result.pulled)
    }

    private fun engineWith(readiness: SessionReadiness) = SyncEngine(
        habitRepo, habitLogRepo, wantActivityRepo, wantLogRepo, identityRepo,
        supabase, watermarks, FakeAuthIdentity("user-1", authenticated = true, readiness = readiness),
    )

    @Test
    fun `a failed refresh fails without touching network`() = runTest {
        // An offline cold start keeps the session, but supabase-kt has no token to send.
        val keptEngine = engineWith(SessionReadiness.REFRESH_FAILED)
        habitRepo.saveHabit(makeHabit("h1"))
        val result = keptEngine.sync(SyncReason.MANUAL)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() !is IllegalStateException, "the worker must retry")
        assertTrue(supabase.habits.isEmpty())
        assertTrue(supabase.fetches.isEmpty())
        assertNull(habitRepo.habits.first().syncedAt)
        val state = keptEngine.syncState.value
        assertTrue(state is SyncState.Error)
        assertEquals("Server unreachable", state.message)
    }

    @Test
    fun `a session that is not loaded fails without touching network or showing an error`() = runTest {
        // In the background, supabase-kt does not load the session. Nobody sees the app.
        val backgroundEngine = engineWith(SessionReadiness.UNAVAILABLE)
        habitRepo.saveHabit(makeHabit("h1"))
        val result = backgroundEngine.sync(SyncReason.POST_LOG)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() !is IllegalStateException, "the worker must retry")
        assertTrue(supabase.habits.isEmpty())
        assertTrue(supabase.fetches.isEmpty())
        assertEquals(SyncState.Idle, backgroundEngine.syncState.value)
    }

    @Test
    fun `only a background sync waits for the next token refresh`() = runTest {
        engine.sync(SyncReason.MANUAL)
        engine.sync(SyncReason.POST_LOG)
        engine.sync(SyncReason.WIDGET_WRITE)
        assertEquals(listOf(false, true, true), auth.waits)
    }

    private suspend fun logOffline(count: Int) = repeat(count) { i ->
        habitLogRepo.insertLog("log-$i", "user-1", "h1", 1.0, now)
    }

    @Test
    fun `a push sends the rows of a table in batches of 500`() = runTest {
        // One request per row took 0.3 s each, so 200 rows took a minute (#37).
        logOffline(1200)
        val result = engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(1200, result.pushed)
        assertEquals(listOf(500, 500, 200), supabase.upserts.filter { it.first == "habit_logs" }.map { it.second })
        assertTrue(habitLogRepo.logs.all { it.syncedAt != null })
    }

    @Test
    fun `a failed batch keeps the batches before it synced`() = runTest {
        logOffline(1200)
        supabase.throwOnUpsert = 2
        assertTrue(engine.sync(SyncReason.MANUAL).isFailure)
        assertEquals(500, habitLogRepo.logs.count { it.syncedAt != null })
        assertEquals(500, supabase.habitLogs.size)
    }

    @Test
    fun `a table with nothing to push sends no request`() = runTest {
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertTrue(supabase.upserts.isEmpty())
    }

    @Test
    fun `a pull asks again for the last 5 s before the watermark`() = runTest {
        // A transaction stamps now() at its start. One that commits after a pull of another
        // device has a time below that watermark, and a pull from the watermark skips it (#35).
        for (table in listOf(SyncTable.HABITS, SyncTable.WANT_ACTIVITIES, SyncTable.HABIT_LOGS, SyncTable.WANT_LOGS, SyncTable.HABIT_IDENTITIES)) {
            watermarks.set(table, 100_000)
            watermarks.markPulled(table)
        }
        watermarks.markRecentLogsPulled()
        engine.sync(SyncReason.MANUAL).getOrThrow()
        for (table in listOf("habits", "want_activities", "habit_logs", "want_logs", "habit_identities")) {
            assertEquals(95_000, supabase.sinces[table], table)
        }
    }

    @Test
    fun `a first pull does not ask for a time before 0`() = runTest {
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(0, supabase.sinces["habit_logs"])
    }

    @Test
    fun `a pull of only the overlap does not move the watermark back`() = runTest {
        watermarks.set(SyncTable.HABIT_LOGS, tPlus(10).toEpochMilliseconds())
        SyncTable.entries.forEach { watermarks.markPulled(it) }
        watermarks.markRecentLogsPulled()
        supabase.habitLogs.add(HabitLog("old", "user-1", "h1", 1.0, now, syncedAt = tPlus(8)))
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(tPlus(10).toEpochMilliseconds(), watermarks.get(SyncTable.HABIT_LOGS))
    }

    @Test
    fun `push failure surfaces Error state`() = runTest {
        habitRepo.saveHabit(makeHabit("h1"))
        supabase.shouldThrowOnNext = RuntimeException("boom")
        val result = engine.sync(SyncReason.MANUAL)
        assertTrue(result.isFailure)
        assertTrue(engine.syncState.value is SyncState.Error)
    }

    @Test
    fun `a reset after a sign-out removes the error of the last user`() = runTest {
        // Offline, the sync before a sign-out fails. Today for the guest must not show that error.
        habitRepo.saveHabit(makeHabit("h1"))
        supabase.shouldThrowOnNext = RuntimeException("boom")
        engine.sync(SyncReason.MANUAL)
        assertTrue(engine.syncState.value is SyncState.Error)

        engine.reset()

        assertEquals(SyncState.Idle, engine.syncState.value)
    }

    @Test
    fun `watermark advances to max server timestamp pulled`() = runTest {
        supabase.habits.add(makeHabit("h1", updatedAt = tPlus(10)))
        supabase.habits.add(makeHabit("h2", updatedAt = tPlus(20)))
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(tPlus(20).toEpochMilliseconds(), watermarks.get(SyncTable.HABITS))
    }

    @Test
    fun `first pull fetches core tables, then recent logs, then history`() = runTest {
        engine.sync(SyncReason.POST_SIGN_IN).getOrThrow()
        assertEquals(
            listOf(
                "user_identities", "habits", "habit_identities", "want_activities",
                "habit_logs_recent", "want_logs_recent", "habit_logs", "want_logs",
            ),
            supabase.fetches,
        )
    }

    @Test
    fun `a first pull fetches the small tables and the recent logs together, then the history`() = runTest {
        val early = CORE + RECENT
        early.forEach { supabase.holds[it] = CompletableDeferred() }
        val sync = launch { engine.sync(SyncReason.POST_SIGN_IN).getOrThrow() }
        runCurrent()

        assertEquals(early.toSet(), supabase.fetches.toSet())

        supabase.holds.values.forEach { it.complete(Unit) }
        sync.join()
        assertEquals(HISTORY, supabase.fetches.takeLast(2))
    }

    @Test
    fun `a later pull fetches every table together`() = runTest {
        engine.sync(SyncReason.POST_SIGN_IN).getOrThrow()
        supabase.fetches.clear()
        (CORE + HISTORY).forEach { supabase.holds[it] = CompletableDeferred() }
        val sync = launch { engine.sync(SyncReason.MANUAL).getOrThrow() }
        runCurrent()

        assertEquals((CORE + HISTORY).toSet(), supabase.fetches.toSet())

        supabase.holds.values.forEach { it.complete(Unit) }
        sync.join()
    }

    @Test
    fun `a table that arrives first still merges after the tables before it`() = runTest {
        CORE.forEach { supabase.holds[it] = CompletableDeferred() }
        val sync = launch { engine.sync(SyncReason.POST_SIGN_IN).getOrThrow() }
        runCurrent()

        // Finish the fetches in reverse order. Nothing merges until user_identities is in.
        CORE.reversed().dropLast(1).forEach {
            supabase.holds.getValue(it).complete(Unit)
            runCurrent()
            assertEquals(emptySet(), watermarks.progress.value.tables)
        }
        supabase.holds.getValue("user_identities").complete(Unit)
        sync.join()
        assertEquals(SyncTable.entries.toSet(), watermarks.progress.value.tables)
    }

    @Test
    fun `a failed fetch keeps the tables merged before it and merges no later table`() = runTest {
        supabase.habits.add(makeHabit("h1", updatedAt = tPlus(10)))
        CORE.forEach { supabase.holds[it] = CompletableDeferred() }
        supabase.throwOn = "habit_identities"
        val sync = async { engine.sync(SyncReason.POST_SIGN_IN) }
        runCurrent()

        supabase.holds.getValue("user_identities").complete(Unit)
        supabase.holds.getValue("habits").complete(Unit)
        runCurrent()
        supabase.holds.getValue("habit_identities").complete(Unit)

        assertTrue(sync.await().isFailure)
        val tables = watermarks.progress.value.tables
        assertEquals(setOf(SyncTable.USER_IDENTITIES, SyncTable.HABITS), tables)
        assertEquals(listOf("h1"), habitRepo.getHabitsForUser("user-1").map { it.id })
        assertEquals(0L, watermarks.get(SyncTable.WANT_ACTIVITIES))
    }

    @Test
    fun `an empty server marks every table pulled`() = runTest {
        engine.sync(SyncReason.POST_SIGN_IN).getOrThrow()
        assertEquals(PullProgress(SyncTable.entries.toSet(), recentLogs = true), watermarks.progress.value)
    }

    @Test
    fun `a later sync skips the recent-logs stage`() = runTest {
        engine.sync(SyncReason.POST_SIGN_IN).getOrThrow()
        supabase.fetches.clear()
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertFalse(supabase.fetches.any { it.endsWith("_recent") })
    }

    @Test
    fun `recent-logs stage pulls logs from the start of the day 6 days ago`() = runTest {
        val inRange = HabitLog("in", "user-1", "h1", 1.0, Instant.parse("2026-09-27T00:00:00Z"), syncedAt = now)
        val tooOld = HabitLog("old", "user-1", "h1", 1.0, Instant.parse("2026-09-26T23:59:59Z"), syncedAt = now)
        supabase.habitLogs += listOf(inRange, tooOld)
        supabase.throwOn = "habit_logs" // stop before the history stage

        engine.sync(SyncReason.POST_SIGN_IN)

        assertEquals(listOf("in"), habitLogRepo.logs.map { it.id })
    }

    @Test
    fun `a failure in the history stage keeps the earlier stages and the log watermark`() = runTest {
        supabase.habits.add(makeHabit("h1", updatedAt = tPlus(10)))
        supabase.habitLogs.add(HabitLog("l1", "user-1", "h1", 1.0, now, syncedAt = now))
        supabase.throwOn = "habit_logs"

        val result = engine.sync(SyncReason.POST_SIGN_IN)

        assertTrue(result.isFailure)
        assertTrue(engine.syncState.value is SyncState.Error)
        val progress = watermarks.progress.value
        assertTrue(SyncTable.HABITS in progress.tables)
        assertTrue(progress.recentLogs)
        assertFalse(SyncTable.HABIT_LOGS in progress.tables)
        assertEquals(0L, watermarks.get(SyncTable.HABIT_LOGS))
    }

    @Test
    fun `push syncs unsynced user and habit identities`() = runTest {
        val habit = makeHabit(id = "h1")
        habitRepo.saveHabit(habit)
        // FakeIdentityRepository.getUnsyncedHabitIdentitiesFor filters by habits seeded into it
        identityRepo.seedHabit(habit)
        identityRepo.setUserIdentities("user-1", setOf("reader"))
        identityRepo.linkHabitToIdentities("h1", setOf("reader"))

        engine.sync(SyncReason.MANUAL).getOrThrow()

        assertEquals(1, supabase.userIdentities.size)
        assertEquals("user-1", supabase.userIdentities.first().userId)
        assertEquals("reader", supabase.userIdentities.first().identityId)
        assertEquals(1, supabase.habitIdentities.size)
        assertEquals("h1", supabase.habitIdentities.first().habitId)
        assertEquals("reader", supabase.habitIdentities.first().identityId)
    }
}

private val CORE = listOf("user_identities", "habits", "habit_identities", "want_activities")
private val RECENT = listOf("habit_logs_recent", "want_logs_recent")
private val HISTORY = listOf("habit_logs", "want_logs")

class InMemoryWatermarks : WatermarkReader {
    private val store = mutableMapOf<SyncTable, Long>()
    private val _progress = MutableStateFlow(PullProgress())
    override val progress: StateFlow<PullProgress> = _progress
    override fun get(table: SyncTable): Long = store[table] ?: 0L
    override fun set(table: SyncTable, valueMs: Long) { store[table] = valueMs }
    override fun markPulled(table: SyncTable) = _progress.update { it.copy(tables = it.tables + table) }
    override fun markRecentLogsPulled() = _progress.update { it.copy(recentLogs = true) }
}

class FakeAuthIdentity(
    private val uid: String,
    private val authenticated: Boolean,
    private val readiness: SessionReadiness = SessionReadiness.LIVE,
) : SyncIdentity {
    /** The waitForRefresh value of each readiness check, in order. */
    val waits = mutableListOf<Boolean>()
    override fun currentUserId(): String = uid
    override fun isAuthenticated(): Boolean = authenticated
    override suspend fun awaitSessionReadiness(waitForRefresh: Boolean): SessionReadiness {
        waits += waitForRefresh
        return readiness
    }
}
