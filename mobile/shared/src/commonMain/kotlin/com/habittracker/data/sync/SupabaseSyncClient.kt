package com.habittracker.data.sync

import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog

interface SupabaseSyncClient {
    /**
     * Each upsert sends all [rows] in one request, so the server saves all of them or none.
     * An empty list sends no request. The server sets `synced_at` and `updated_at` itself.
     */
    suspend fun upsertHabits(rows: List<Habit>)
    suspend fun upsertWantActivities(rows: List<WantActivity>, ownerUserId: String)
    suspend fun upsertHabitLogs(rows: List<HabitLog>)
    suspend fun upsertWantLogs(rows: List<WantLog>)
    suspend fun upsertUserIdentities(rows: List<UserIdentityRow>)
    suspend fun upsertHabitIdentities(rows: List<HabitIdentityRow>)

    suspend fun fetchHabitsSince(userId: String, sinceMs: Long): List<Habit>
    suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity>
    suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog>
    suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog>
    suspend fun fetchUserIdentitiesSince(userId: String, sinceMs: Long): List<UserIdentityRow>
    suspend fun fetchHabitIdentitiesSince(userId: String, sinceMs: Long): List<HabitIdentityRow>

    /** Logs with `logged_at >= fromMs`, whatever their sync time. Moves no watermark. */
    suspend fun fetchHabitLogsLoggedFrom(userId: String, fromMs: Long): List<HabitLog>
    suspend fun fetchWantLogsLoggedFrom(userId: String, fromMs: Long): List<WantLog>
}
