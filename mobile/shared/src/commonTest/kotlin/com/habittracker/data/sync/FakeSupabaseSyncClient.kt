package com.habittracker.data.sync

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog

class FakeSupabaseSyncClient : SupabaseSyncClient {
    val habits = mutableListOf<Habit>()
    val wantActivities = mutableListOf<WantActivity>()
    val habitLogs = mutableListOf<HabitLog>()
    val wantLogs = mutableListOf<WantLog>()

    var shouldThrowOnNext: Throwable? = null

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
        failIfNeeded()
        return habits.filter { it.userId == userId && it.updatedAt.toEpochMilliseconds() > sinceMs }
    }

    override suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity> {
        failIfNeeded()
        return wantActivities.filter {
            (it.createdByUserId == userId || it.createdByUserId == null) &&
                it.updatedAt.toEpochMilliseconds() > sinceMs
        }
    }

    override suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog> {
        failIfNeeded()
        return habitLogs.filter {
            it.userId == userId && (it.syncedAt?.toEpochMilliseconds() ?: 0L) > sinceMs
        }
    }

    override suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog> {
        failIfNeeded()
        return wantLogs.filter {
            it.userId == userId && (it.syncedAt?.toEpochMilliseconds() ?: 0L) > sinceMs
        }
    }
}
