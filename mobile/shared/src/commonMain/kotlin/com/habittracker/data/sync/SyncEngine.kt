package com.habittracker.data.sync

import com.habittracker.data.local.PullProgress
import com.habittracker.data.local.SyncTable
import com.habittracker.data.local.WatermarkReader
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

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
    private val identityRepo: IdentityRepository,
    private val supabase: SupabaseSyncClient,
    private val watermarks: WatermarkReader,
    private val identity: SyncIdentity,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _state.asStateFlow()

    /** Which tables the first sync on this device has pulled so far. */
    val pullProgress: StateFlow<PullProgress> = watermarks.progress

    private val mutex = Mutex()

    suspend fun sync(reason: SyncReason): Result<SyncOutcome> = mutex.withLock {
        if (!identity.isAuthenticated()) {
            return@withLock Result.success(SyncOutcome(0, 0))
        }
        val userId = identity.currentUserId()
        val start = clock.now()
        _state.value = SyncState.Running(start, reason)
        runCatching {
            val pushed = push(userId)
            val pulled = pull(userId)
            val outcome = SyncOutcome(pushed, pulled)
            _state.value = SyncState.Synced(clock.now(), pushed, pulled)
            outcome
        }.onFailure { e ->
            // Full detail goes to logcat; UI gets a short categorized label.
            println("SyncEngine: sync($reason) failed — ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            _state.value = SyncState.Error(
                message = categorize(e),
                since = clock.now(),
            )
        }
    }

    private fun categorize(e: Throwable): String {
        val name = e::class.simpleName.orEmpty()
        val msg = e.message.orEmpty()
        return when {
            "JWT" in msg || "Unauthorized" in name -> "Session expired"
            "timed out" in msg || "timeout" in msg.lowercase() -> "Network timeout"
            "BadRequest" in name -> "Sync rejected by server"
            name.startsWith("UnknownHost") || "network" in msg.lowercase() -> "No network"
            else -> "Sync failed"
        }
    }

    private suspend fun push(userId: String): Int {
        var count = 0
        val now = clock.now()
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
        identityRepo.getUnsyncedUserIdentitiesFor(userId).forEach { row ->
            val stamped = row.copy(syncedAt = now)
            supabase.upsertUserIdentity(stamped)
            identityRepo.markUserIdentitySynced(row.userId, row.identityId, now)
            count++
        }
        identityRepo.getUnsyncedHabitIdentitiesFor(userId).forEach { row ->
            val stamped = row.copy(syncedAt = now)
            supabase.upsertHabitIdentity(stamped)
            identityRepo.markHabitIdentitySynced(row.habitId, row.identityId, now)
            count++
        }
        return count
    }

    /**
     * Pulls in three stages, so each Today section can show as soon as its data is local:
     * the small tables first, then the last 7 days of logs (first pull only), then the
     * full log history.
     */
    private suspend fun pull(userId: String): Int {
        // Habits before habit_identities, so the links have their habits.
        var pulled = step(SyncTable.USER_IDENTITIES) { pullUserIdentities(userId) } +
            step(SyncTable.HABITS) { pullHabits(userId) } +
            step(SyncTable.HABIT_IDENTITIES) { pullHabitIdentities(userId) } +
            step(SyncTable.WANT_ACTIVITIES) { pullWantActivities(userId) }
        if (!recentLogsLocal()) pullRecentLogs(userId)
        pulled += step(SyncTable.HABIT_LOGS) { pullHabitLogs(userId) } +
            step(SyncTable.WANT_LOGS) { pullWantLogs(userId) }
        return pulled
    }

    /** Runs one table's pull, then records the table as pulled, also when it had no new rows. */
    private inline fun step(table: SyncTable, pull: () -> Int): Int =
        pull().also { if (table !in watermarks.progress.value.tables) watermarks.markPulled(table) }

    private fun recentLogsLocal(): Boolean = watermarks.progress.value.let {
        it.recentLogs || (SyncTable.HABIT_LOGS in it.tables && SyncTable.WANT_LOGS in it.tables)
    }

    /**
     * Logs from the start of the day 6 days ago: the 7-day strip, and never later than
     * Monday, so the week's points too. The history stage pulls these rows again, so
     * this moves no watermark. The merge replaces by id, so the second pull is safe.
     */
    private suspend fun pullRecentLogs(userId: String) {
        val today = clock.now().toLocalDateTime(timeZone).date
        val fromMs = today.minus(6, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
        habitLogRepo.mergePulledAll(supabase.fetchHabitLogsLoggedFrom(userId, fromMs))
        wantLogRepo.mergePulledAll(supabase.fetchWantLogsLoggedFrom(userId, fromMs))
        watermarks.markRecentLogsPulled()
    }

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
        habitLogRepo.mergePulledAll(remote)
        val maxTs = remote.mapNotNull { it.syncedAt?.toEpochMilliseconds() }.maxOrNull() ?: last
        watermarks.set(SyncTable.HABIT_LOGS, maxTs)
        return remote.size
    }

    private suspend fun pullWantLogs(userId: String): Int {
        val last = watermarks.get(SyncTable.WANT_LOGS)
        val remote = supabase.fetchWantLogsSince(userId, last)
        if (remote.isEmpty()) return 0
        wantLogRepo.mergePulledAll(remote)
        val maxTs = remote.mapNotNull { it.syncedAt?.toEpochMilliseconds() }.maxOrNull() ?: last
        watermarks.set(SyncTable.WANT_LOGS, maxTs)
        return remote.size
    }

    private suspend fun pullUserIdentities(userId: String): Int {
        val last = watermarks.get(SyncTable.USER_IDENTITIES)
        val remote = supabase.fetchUserIdentitiesSince(userId, last)
        if (remote.isEmpty()) return 0
        remote.forEach { row -> identityRepo.mergePulledUserIdentity(row) }
        val maxTs = remote.maxOf { it.syncedAt?.toEpochMilliseconds() ?: it.addedAt.toEpochMilliseconds() }
        watermarks.set(SyncTable.USER_IDENTITIES, maxTs)
        return remote.size
    }

    private suspend fun pullHabitIdentities(userId: String): Int {
        val last = watermarks.get(SyncTable.HABIT_IDENTITIES)
        val remote = supabase.fetchHabitIdentitiesSince(userId, last)
        if (remote.isEmpty()) return 0
        remote.forEach { row -> identityRepo.mergePulledHabitIdentity(row) }
        val maxTs = remote.maxOf { it.syncedAt?.toEpochMilliseconds() ?: it.addedAt.toEpochMilliseconds() }
        watermarks.set(SyncTable.HABIT_IDENTITIES, maxTs)
        return remote.size
    }
}
