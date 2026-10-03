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

    private fun failIfNeeded() {
        shouldThrowOnNext?.let { err ->
            shouldThrowOnNext = null
            throw err
        }
    }

    override suspend fun upsertHabit(row: Habit) {
        failIfNeeded()
        habits.removeAll { it.id == row.id }
        habits.add(row)
    }

    override suspend fun upsertWantActivity(row: WantActivity, ownerUserId: String) {
        failIfNeeded()
        wantActivities.removeAll { it.id == row.id }
        wantActivities.add(row.copy(createdByUserId = if (row.isCustom) ownerUserId else null))
    }

    override suspend fun upsertHabitLog(row: HabitLog) {
        failIfNeeded()
        habitLogs.removeAll { it.id == row.id }
        habitLogs.add(row)
    }

    override suspend fun upsertWantLog(row: WantLog) {
        failIfNeeded()
        wantLogs.removeAll { it.id == row.id }
        wantLogs.add(row)
    }

    override suspend fun fetchHabitsSince(userId: String, sinceMs: Long): List<Habit> {
        fetch("habits")
        return habits.filter { it.userId == userId && it.updatedAt.toEpochMilliseconds() > sinceMs }
    }

    override suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity> {
        fetch("want_activities")
        return wantActivities.filter {
            (it.createdByUserId == userId || it.createdByUserId == null) &&
                it.updatedAt.toEpochMilliseconds() > sinceMs
        }
    }

    override suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog> {
        fetch("habit_logs")
        return habitLogs.filter {
            it.userId == userId && (it.syncedAt?.toEpochMilliseconds() ?: 0L) > sinceMs
        }
    }

    override suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog> {
        fetch("want_logs")
        return wantLogs.filter {
            it.userId == userId && (it.syncedAt?.toEpochMilliseconds() ?: 0L) > sinceMs
        }
    }

    val userIdentities = mutableListOf<UserIdentityRow>()
    val habitIdentities = mutableListOf<HabitIdentityRow>()

    override suspend fun upsertUserIdentity(row: UserIdentityRow) {
        failIfNeeded()
        userIdentities.removeAll { it.userId == row.userId && it.identityId == row.identityId }
        userIdentities.add(row)
    }

    override suspend fun upsertHabitIdentity(row: HabitIdentityRow) {
        failIfNeeded()
        habitIdentities.removeAll { it.habitId == row.habitId && it.identityId == row.identityId }
        habitIdentities.add(row)
    }

    override suspend fun fetchUserIdentitiesSince(userId: String, sinceMs: Long): List<UserIdentityRow> {
        fetch("user_identities")
        return userIdentities.filter { it.userId == userId && (it.syncedAt?.toEpochMilliseconds() ?: it.addedAt.toEpochMilliseconds()) > sinceMs }
    }

    override suspend fun fetchHabitIdentitiesSince(userId: String, sinceMs: Long): List<HabitIdentityRow> {
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
