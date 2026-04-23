package com.habittracker.data.sync

import com.habittracker.data.local.SyncTable
import com.habittracker.data.local.WatermarkReader
import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeWantActivityRepository
import com.habittracker.data.repository.FakeWantLogRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncEngineTest {

    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()
    private val wantActivityRepo = FakeWantActivityRepository()
    private val wantLogRepo = FakeWantLogRepository()
    private val supabase = FakeSupabaseSyncClient()
    private val watermarks = InMemoryWatermarks()
    private val auth = FakeAuthIdentity("user-1", authenticated = true)

    private val engine = SyncEngine(
        habitRepo, habitLogRepo, wantActivityRepo, wantLogRepo,
        supabase, watermarks, auth,
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
            habitRepo, habitLogRepo, wantActivityRepo, wantLogRepo,
            supabase, watermarks, offlineAuth,
        )
        habitRepo.saveHabit(makeHabit("h1"))
        val result = offlineEngine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(0, result.pushed)
        assertEquals(0, result.pulled)
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
    fun `watermark advances to max server timestamp pulled`() = runTest {
        supabase.habits.add(makeHabit("h1", updatedAt = tPlus(10)))
        supabase.habits.add(makeHabit("h2", updatedAt = tPlus(20)))
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(tPlus(20).toEpochMilliseconds(), watermarks.get(SyncTable.HABITS))
    }
}

class InMemoryWatermarks : WatermarkReader {
    private val store = mutableMapOf<SyncTable, Long>()
    override fun get(table: SyncTable): Long = store[table] ?: 0L
    override fun set(table: SyncTable, valueMs: Long) { store[table] = valueMs }
}

class FakeAuthIdentity(
    private val uid: String,
    private val authenticated: Boolean,
) : SyncIdentity {
    override fun currentUserId(): String = uid
    override fun isAuthenticated(): Boolean = authenticated
}
