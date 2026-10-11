package com.habittracker.data.sync

import com.habittracker.data.repository.SessionReadiness
import com.habittracker.data.local.PullProgress
import com.habittracker.data.local.SyncTable
import com.habittracker.data.local.WatermarkReader
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    /**
     * Waits until the auth client has loaded the session, then tells if a request can use it.
     * With [waitForRefresh], a failed refresh waits for the next refresh attempt.
     */
    suspend fun awaitSessionReadiness(waitForRefresh: Boolean): SessionReadiness
}

/** The session has no token that a request can use. */
class NoLiveSessionException : Exception("The session has no valid token")

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
        // Without a token, a request goes out as anon. The server rejects it with
        // "Unauthorized", and the session guard then ends a session that is still valid.
        // A manual sync answers at once. A background job can wait for the next token refresh.
        when (identity.awaitSessionReadiness(waitForRefresh = reason != SyncReason.MANUAL)) {
            SessionReadiness.LIVE -> Unit
            SessionReadiness.REFRESH_FAILED -> {
                _state.value = SyncState.Error(message = "Server unreachable", since = clock.now())
                return@withLock Result.failure(NoLiveSessionException())
            }
            // The app is in the background, so the state does not change. The job retries.
            SessionReadiness.UNAVAILABLE -> return@withLock Result.failure(NoLiveSessionException())
        }
        val userId = identity.currentUserId()
        val start = clock.now()
        val before = _state.value
        _state.value = SyncState.Running(start, reason)
        runCatching {
            val pushed = push(userId)
            val pulled = pull(userId)
            val outcome = SyncOutcome(pushed, pulled)
            _state.value = SyncState.Synced(clock.now(), pushed, pulled)
            outcome
        }.onFailure { e ->
            // A stopped job is not a failed sync. ColorOS stops a background job after about
            // 5 s. As an Error, each stop showed "Sync failed" and counted toward the
            // "Sync has been failing" notification. The rows that were not pushed stay
            // pending, and the job runs again. An old Error goes back to Idle: each new Error
            // counts as a failure, and Today shows its message again.
            if (e is CancellationException) {
                _state.value = if (before is SyncState.Error) SyncState.Idle else before
                throw e
            }
            // Full detail goes to logcat; UI gets a short categorized label.
            println("SyncEngine: sync($reason) failed — ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            _state.value = SyncState.Error(
                message = categorize(e),
                since = clock.now(),
            )
        }
    }

    /** Forgets the result of the last sync. Call after a sign-out, so the next user does not see it. */
    fun reset() {
        _state.value = SyncState.Idle
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

    /**
     * Sends each table in batches of [PUSH_BATCH_SIZE] rows, one request per batch. A batch is
     * marked synced only after its request succeeds, so a failure keeps the earlier batches.
     * The server sets `synced_at` and `updated_at`, so the time of this phone goes nowhere.
     */
    private suspend fun push(userId: String): Int {
        val now = clock.now()
        return pushBatches(habitRepo.getUnsyncedFor(userId), { supabase.upsertHabits(it) }) {
            habitRepo.markSynced(it.id, now)
        } + pushBatches(wantActivityRepo.getUnsyncedFor(userId), { supabase.upsertWantActivities(it, userId) }) {
            wantActivityRepo.markSynced(it.id, now)
        } + pushBatches(habitLogRepo.getUnsyncedFor(userId), { supabase.upsertHabitLogs(it) }) {
            habitLogRepo.markSynced(it.id, now)
        } + pushBatches(wantLogRepo.getUnsyncedFor(userId), { supabase.upsertWantLogs(it) }) {
            wantLogRepo.markSynced(it.id, now)
        } + pushBatches(identityRepo.getUnsyncedUserIdentitiesFor(userId), { supabase.upsertUserIdentities(it) }) {
            identityRepo.markUserIdentitySynced(it.userId, it.identityId, now)
        } + pushBatches(identityRepo.getUnsyncedHabitIdentitiesFor(userId), { supabase.upsertHabitIdentities(it) }) {
            identityRepo.markHabitIdentitySynced(it.habitId, it.identityId, now)
        }
    }

    private suspend fun <T> pushBatches(
        rows: List<T>,
        upsert: suspend (List<T>) -> Unit,
        markSynced: suspend (T) -> Unit,
    ): Int {
        rows.chunked(PUSH_BATCH_SIZE).forEach { batch ->
            upsert(batch)
            batch.forEach { markSynced(it) }
        }
        return rows.size
    }

    /**
     * The watermark of [table], less [WATERMARK_OVERLAP_MS]. The server stamps a row with the
     * start time of its transaction. A transaction that commits after a pull of this device has
     * a time below the watermark of that pull, so the next pull asks for those seconds again.
     * The merges are by id, so a row that comes again changes nothing.
     */
    private fun since(table: SyncTable): Long =
        (watermarks.get(table) - WATERMARK_OVERLAP_MS).coerceAtLeast(0)

    /** Rows of the overlap are older than the watermark, so they must not move it back. */
    private fun advance(table: SyncTable, maxMs: Long) =
        watermarks.set(table, maxOf(watermarks.get(table), maxMs))

    /**
     * Pulls in three stages, so each Today section can show as soon as its data is local:
     * the small tables first, then the last 7 days of logs (first pull only), then the
     * full log history.
     *
     * The fetches of a stage run at the same time. The merges run one at a time, in
     * the order of [CORE_TABLES], the recent logs, then [HISTORY_TABLES]. On a first
     * pull the history waits for the earlier stages, so it does not slow them. On a later
     * pull every fetch is small, so all of them start at once.
     */
    private suspend fun pull(userId: String): Int = coroutineScope {
        val firstPull = !recentLogsLocal()
        val early = CORE_TABLES.associateWith { async { fetch(it, userId) } }
        val recent = if (firstPull) async { fetchRecentLogs(userId) } else null
        val late = if (firstPull) null else HISTORY_TABLES.associateWith { async { fetch(it, userId) } }

        var pulled = early.entries.sumOf { (table, merge) -> step(table, merge.await()) }
        recent?.await()?.invoke()
        val history = late ?: HISTORY_TABLES.associateWith { async { fetch(it, userId) } }
        pulled += history.entries.sumOf { (table, merge) -> step(table, merge.await()) }
        pulled
    }

    /** Runs one table's merge, then records the table as pulled, also when it had no new rows. */
    private suspend fun step(table: SyncTable, merge: Merge): Int =
        merge().also { if (table !in watermarks.progress.value.tables) watermarks.markPulled(table) }

    private fun recentLogsLocal(): Boolean = watermarks.progress.value.let {
        it.recentLogs || (SyncTable.HABIT_LOGS in it.tables && SyncTable.WANT_LOGS in it.tables)
    }

    private suspend fun fetch(table: SyncTable, userId: String): Merge = when (table) {
        SyncTable.USER_IDENTITIES -> fetchUserIdentities(userId)
        SyncTable.HABITS -> fetchHabits(userId)
        SyncTable.HABIT_IDENTITIES -> fetchHabitIdentities(userId)
        SyncTable.WANT_ACTIVITIES -> fetchWantActivities(userId)
        SyncTable.HABIT_LOGS -> fetchHabitLogs(userId)
        SyncTable.WANT_LOGS -> fetchWantLogs(userId)
    }

    /**
     * Logs from the start of the day 6 days ago: the 7-day strip, and never later than
     * Monday, so the week's points too. The history stage pulls these rows again, so
     * this moves no watermark. The merge replaces by id, so the second pull is safe.
     */
    private suspend fun fetchRecentLogs(userId: String): Merge = coroutineScope {
        val today = clock.now().toLocalDateTime(timeZone).date
        val fromMs = today.minus(6, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
        val habitLogs = async { supabase.fetchHabitLogsLoggedFrom(userId, fromMs) }
        val wantLogs = async { supabase.fetchWantLogsLoggedFrom(userId, fromMs) }
        val habitRows = habitLogs.await()
        val wantRows = wantLogs.await()
        val merge: Merge = {
            habitLogRepo.mergePulledAll(habitRows)
            wantLogRepo.mergePulledAll(wantRows)
            watermarks.markRecentLogsPulled()
            habitRows.size + wantRows.size
        }
        merge
    }

    private suspend fun fetchHabits(userId: String): Merge {
        val remote = supabase.fetchHabitsSince(userId, since(SyncTable.HABITS))
        return merge@{
            if (remote.isEmpty()) return@merge 0
            val ids = remote.map { it.id }
            val locals = habitRepo.getByIdsForUser(userId, ids).associateBy { it.id }
            remote.forEach { row ->
                val local = locals[row.id]
                if (local == null || row.updatedAt > local.updatedAt) {
                    habitRepo.mergePulled(row.copy(syncedAt = row.updatedAt))
                }
            }
            advance(SyncTable.HABITS, remote.maxOf { it.updatedAt.toEpochMilliseconds() })
            remote.size
        }
    }

    private suspend fun fetchWantActivities(userId: String): Merge {
        val remote = supabase.fetchWantActivitiesSince(userId, since(SyncTable.WANT_ACTIVITIES))
        return merge@{
            if (remote.isEmpty()) return@merge 0
            val ids = remote.map { it.id }
            val locals = wantActivityRepo.getByIdsForUser(userId, ids).associateBy { it.id }
            remote.forEach { row ->
                val local = locals[row.id]
                if (local == null || row.updatedAt > local.updatedAt) {
                    wantActivityRepo.mergePulled(row.copy(syncedAt = row.updatedAt))
                }
            }
            advance(SyncTable.WANT_ACTIVITIES, remote.maxOf { it.updatedAt.toEpochMilliseconds() })
            remote.size
        }
    }

    private suspend fun fetchHabitLogs(userId: String): Merge {
        val last = watermarks.get(SyncTable.HABIT_LOGS)
        val remote = supabase.fetchHabitLogsSince(userId, since(SyncTable.HABIT_LOGS))
        return merge@{
            if (remote.isEmpty()) return@merge 0
            habitLogRepo.mergePulledAll(remote)
            val maxTs = remote.mapNotNull { it.syncedAt?.toEpochMilliseconds() }.maxOrNull() ?: last
            advance(SyncTable.HABIT_LOGS, maxTs)
            remote.size
        }
    }

    private suspend fun fetchWantLogs(userId: String): Merge {
        val last = watermarks.get(SyncTable.WANT_LOGS)
        val remote = supabase.fetchWantLogsSince(userId, since(SyncTable.WANT_LOGS))
        return merge@{
            if (remote.isEmpty()) return@merge 0
            wantLogRepo.mergePulledAll(remote)
            val maxTs = remote.mapNotNull { it.syncedAt?.toEpochMilliseconds() }.maxOrNull() ?: last
            advance(SyncTable.WANT_LOGS, maxTs)
            remote.size
        }
    }

    private suspend fun fetchUserIdentities(userId: String): Merge {
        val remote = supabase.fetchUserIdentitiesSince(userId, since(SyncTable.USER_IDENTITIES))
        return merge@{
            if (remote.isEmpty()) return@merge 0
            remote.forEach { row -> identityRepo.mergePulledUserIdentity(row) }
            val maxTs = remote.maxOf { it.syncedAt?.toEpochMilliseconds() ?: it.addedAt.toEpochMilliseconds() }
            advance(SyncTable.USER_IDENTITIES, maxTs)
            remote.size
        }
    }

    private suspend fun fetchHabitIdentities(userId: String): Merge {
        val remote = supabase.fetchHabitIdentitiesSince(userId, since(SyncTable.HABIT_IDENTITIES))
        return merge@{
            if (remote.isEmpty()) return@merge 0
            remote.forEach { row -> identityRepo.mergePulledHabitIdentity(row) }
            val maxTs = remote.maxOf { it.syncedAt?.toEpochMilliseconds() ?: it.addedAt.toEpochMilliseconds() }
            advance(SyncTable.HABIT_IDENTITIES, maxTs)
            remote.size
        }
    }
}

/** Writes one table's fetched rows and moves its watermark. Returns the row count. */
private typealias Merge = suspend () -> Int

/** Habits come before habit_identities, so the links have their habits. */
private val CORE_TABLES = listOf(
    SyncTable.USER_IDENTITIES, SyncTable.HABITS, SyncTable.HABIT_IDENTITIES, SyncTable.WANT_ACTIVITIES,
)

private val HISTORY_TABLES = listOf(SyncTable.HABIT_LOGS, SyncTable.WANT_LOGS)

/** Rows per upsert request. A batch of 500 logs is about 100 KB. */
internal const val PUSH_BATCH_SIZE = 500

/** A pull asks again for this time before the watermark. See SyncEngine.since. */
internal const val WATERMARK_OVERLAP_MS = 5_000L
