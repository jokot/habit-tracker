package com.habittracker.data.sync

import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog
import kotlinx.coroutines.CompletableDeferred

class FakeSupabaseSyncClient : SupabaseSyncClient {
    val habits = mutableListOf<Habit>()
    val wantActivities = mutableListOf<WantActivity>()
    val habitLogs = mutableListOf<HabitLog>()
    val wantLogs = mutableListOf<WantLog>()

    var shouldThrowOnNext: Throwable? = null

    /** Names of the fetches made, in order. A `_recent` suffix marks the recent-logs fetches. */
    val fetches = mutableListOf<String>()

    /** The fetch with this name throws, every time. */
    var throwOn: String? = null

    /** A fetch with a name in this map waits for its deferred before it returns. */
    val holds = mutableMapOf<String, CompletableDeferred<Unit>>()

    private suspend fun fetch(name: String) {
        fetches += name
        holds[name]?.await()
        if (throwOn == name) throw RuntimeException("fetch $name failed")
        failIfNeeded()
    }

    /** Each upsert request, in order: the table and the row count. */
    val upserts = mutableListOf<Pair<String, Int>>()

    /** The upsert request with this 1-based number throws. */
    var throwOnUpsert: Int? = null

    /** The `sinceMs` value of the last fetch of each table. */
    val sinces = mutableMapOf<String, Long>()

    private fun upsert(table: String, rows: Int) {
        upserts += table to rows
        if (upserts.size == throwOnUpsert) throw RuntimeException("upsert ${upserts.size} failed")
        failIfNeeded()
    }

    private fun failIfNeeded() {
        shouldThrowOnNext?.let { err ->
            shouldThrowOnNext = null
            throw err
        }
    }

    override suspend fun upsertHabits(rows: List<Habit>) {
        upsert("habits", rows.size)
        rows.forEach { row ->
            habits.removeAll { it.id == row.id }
            habits.add(row)
        }
    }

    override suspend fun upsertWantActivities(rows: List<WantActivity>, ownerUserId: String) {
        upsert("want_activities", rows.size)
        rows.forEach { row ->
            wantActivities.removeAll { it.id == row.id }
            wantActivities.add(row.copy(createdByUserId = if (row.isCustom) ownerUserId else null))
        }
    }

    /** Server time for `synced_at`, as the trigger sets it. */
    var serverNow: () -> kotlinx.datetime.Instant = { kotlinx.datetime.Clock.System.now() }

    override suspend fun upsertHabitLogs(rows: List<HabitLog>) {
        upsert("habit_logs", rows.size)
        rows.forEach { row ->
            habitLogs.removeAll { it.id == row.id }
            habitLogs.add(row.copy(syncedAt = serverNow()))
        }
    }

    override suspend fun upsertWantLogs(rows: List<WantLog>) {
        upsert("want_logs", rows.size)
        rows.forEach { row ->
            wantLogs.removeAll { it.id == row.id }
            wantLogs.add(row.copy(syncedAt = serverNow()))
        }
    }

    override suspend fun fetchHabitsSince(userId: String, sinceMs: Long): List<Habit> {
        sinces["habits"] = sinceMs
        fetch("habits")
        return habits.filter { it.userId == userId && it.updatedAt.toEpochMilliseconds() > sinceMs }
    }

    override suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity> {
        sinces["want_activities"] = sinceMs
        fetch("want_activities")
        return wantActivities.filter {
            (it.createdByUserId == userId || it.createdByUserId == null) &&
                it.updatedAt.toEpochMilliseconds() > sinceMs
        }
    }

    override suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog> {
        sinces["habit_logs"] = sinceMs
        fetch("habit_logs")
        return habitLogs.filter {
            it.userId == userId && (it.syncedAt?.toEpochMilliseconds() ?: 0L) > sinceMs
        }
    }

    override suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog> {
        sinces["want_logs"] = sinceMs
        fetch("want_logs")
        return wantLogs.filter {
            it.userId == userId && (it.syncedAt?.toEpochMilliseconds() ?: 0L) > sinceMs
        }
    }

    val userIdentities = mutableListOf<UserIdentityRow>()
    val habitIdentities = mutableListOf<HabitIdentityRow>()

    override suspend fun upsertUserIdentities(rows: List<UserIdentityRow>) {
        upsert("user_identities", rows.size)
        rows.forEach { row ->
            userIdentities.removeAll { it.userId == row.userId && it.identityId == row.identityId }
            userIdentities.add(row)
        }
    }

    override suspend fun upsertHabitIdentities(rows: List<HabitIdentityRow>) {
        upsert("habit_identities", rows.size)
        rows.forEach { row ->
            habitIdentities.removeAll { it.habitId == row.habitId && it.identityId == row.identityId }
            habitIdentities.add(row)
        }
    }

    override suspend fun fetchUserIdentitiesSince(userId: String, sinceMs: Long): List<UserIdentityRow> {
        sinces["user_identities"] = sinceMs
        fetch("user_identities")
        return userIdentities.filter { it.userId == userId && (it.syncedAt?.toEpochMilliseconds() ?: it.addedAt.toEpochMilliseconds()) > sinceMs }
    }

    override suspend fun fetchHabitIdentitiesSince(userId: String, sinceMs: Long): List<HabitIdentityRow> {
        sinces["habit_identities"] = sinceMs
        fetch("habit_identities")
        @Suppress("UNUSED_PARAMETER") val _u = userId
        return habitIdentities.filter { (it.syncedAt?.toEpochMilliseconds() ?: it.addedAt.toEpochMilliseconds()) > sinceMs }
    }

    override suspend fun fetchHabitLogsLoggedFrom(userId: String, fromMs: Long): List<HabitLog> {
        fetch("habit_logs_recent")
        return habitLogs.filter { it.userId == userId && it.loggedAt.toEpochMilliseconds() >= fromMs }
    }

    override suspend fun fetchWantLogsLoggedFrom(userId: String, fromMs: Long): List<WantLog> {
        fetch("want_logs_recent")
        return wantLogs.filter { it.userId == userId && it.loggedAt.toEpochMilliseconds() >= fromMs }
    }
}
