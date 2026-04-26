package com.habittracker.data.sync

import com.habittracker.data.local.SyncTable
import com.habittracker.data.local.WatermarkReader
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/** Minimal identity surface SyncEngine needs. AppContainer bridges UserIdentityProvider. */
interface SyncIdentity {
    fun currentUserId(): String
    fun isAuthenticated(): Boolean
}

class SyncEngine(
    private val habitRepo: HabitRepository,
    private val habitLogRepo: HabitLogRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val wantLogRepo: WantLogRepository,
    private val supabase: SupabaseSyncClient,
    private val watermarks: WatermarkReader,
    private val identity: SyncIdentity,
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _state.asStateFlow()

    private val mutex = Mutex()

    suspend fun sync(reason: SyncReason): Result<SyncOutcome> = mutex.withLock {
        if (!identity.isAuthenticated()) {
            return@withLock Result.success(SyncOutcome(0, 0))
        }
        val userId = identity.currentUserId()
        val start = Clock.System.now()
        _state.value = SyncState.Running(start, reason)
        runCatching {
            val pushed = push(userId)
            val pulled = pull(userId)
            val outcome = SyncOutcome(pushed, pulled)
            _state.value = SyncState.Synced(Clock.System.now(), pushed, pulled)
            outcome
        }.onFailure { e ->
            println("SyncEngine: sync($reason) failed — ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            _state.value = SyncState.Error(
                message = "${e::class.simpleName}: ${e.message ?: "unknown"}",
                since = Clock.System.now(),
            )
        }
    }

    private suspend fun push(userId: String): Int {
        var count = 0
        val now = Clock.System.now()
        habitRepo.getUnsyncedFor(userId).forEach { row ->
            supabase.upsertHabit(row)
            habitRepo.markSynced(row.id, now)
            count++
        }
        wantActivityRepo.getUnsyncedFor(userId).forEach { row ->
            supabase.upsertWantActivity(row, userId)
            wantActivityRepo.markSynced(row.id, now)
            count++
        }
        habitLogRepo.getUnsyncedFor(userId).forEach { row ->
            val stamped = row.copy(syncedAt = now)
            supabase.upsertHabitLog(stamped)
            habitLogRepo.markSynced(row.id, now)
            count++
        }
        wantLogRepo.getUnsyncedFor(userId).forEach { row ->
            val stamped = row.copy(syncedAt = now)
            supabase.upsertWantLog(stamped)
            wantLogRepo.markSynced(row.id, now)
            count++
        }
        return count
    }

    private suspend fun pull(userId: String): Int =
        pullHabits(userId) +
            pullWantActivities(userId) +
            pullHabitLogs(userId) +
            pullWantLogs(userId)

    private suspend fun pullHabits(userId: String): Int {
        val last = watermarks.get(SyncTable.HABITS)
        val remote = supabase.fetchHabitsSince(userId, last)
        if (remote.isEmpty()) return 0
        val ids = remote.map { it.id }
        val locals = habitRepo.getByIdsForUser(userId, ids).associateBy { it.id }
        remote.forEach { row ->
            val local = locals[row.id]
            if (local == null || row.updatedAt > local.updatedAt) {
                habitRepo.mergePulled(row.copy(syncedAt = row.updatedAt))
            }
        }
        watermarks.set(SyncTable.HABITS, remote.maxOf { it.updatedAt.toEpochMilliseconds() })
        return remote.size
    }

    private suspend fun pullWantActivities(userId: String): Int {
        val last = watermarks.get(SyncTable.WANT_ACTIVITIES)
        val remote = supabase.fetchWantActivitiesSince(userId, last)
        if (remote.isEmpty()) return 0
        val ids = remote.map { it.id }
        val locals = wantActivityRepo.getByIdsForUser(userId, ids).associateBy { it.id }
        remote.forEach { row ->
            val local = locals[row.id]
            if (local == null || row.updatedAt > local.updatedAt) {
                wantActivityRepo.mergePulled(row.copy(syncedAt = row.updatedAt))
            }
        }
        watermarks.set(SyncTable.WANT_ACTIVITIES, remote.maxOf { it.updatedAt.toEpochMilliseconds() })
        return remote.size
    }

    private suspend fun pullHabitLogs(userId: String): Int {
        val last = watermarks.get(SyncTable.HABIT_LOGS)
        val remote = supabase.fetchHabitLogsSince(userId, last)
        if (remote.isEmpty()) return 0
        remote.forEach { row -> habitLogRepo.mergePulled(row) }
        val maxTs = remote.mapNotNull { it.syncedAt?.toEpochMilliseconds() }.maxOrNull() ?: last
        watermarks.set(SyncTable.HABIT_LOGS, maxTs)
        return remote.size
    }

    private suspend fun pullWantLogs(userId: String): Int {
        val last = watermarks.get(SyncTable.WANT_LOGS)
        val remote = supabase.fetchWantLogsSince(userId, last)
        if (remote.isEmpty()) return 0
        remote.forEach { row -> wantLogRepo.mergePulled(row) }
        val maxTs = remote.mapNotNull { it.syncedAt?.toEpochMilliseconds() }.maxOrNull() ?: last
        watermarks.set(SyncTable.WANT_LOGS, maxTs)
        return remote.size
    }
}
