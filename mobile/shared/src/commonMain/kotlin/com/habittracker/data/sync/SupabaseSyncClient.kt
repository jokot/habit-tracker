package com.habittracker.data.sync

import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog

interface SupabaseSyncClient {
    suspend fun upsertHabit(row: Habit)
    suspend fun upsertWantActivity(row: WantActivity, ownerUserId: String)
    suspend fun upsertHabitLog(row: HabitLog)
    suspend fun upsertWantLog(row: WantLog)
    suspend fun upsertUserIdentity(row: UserIdentityRow)
    suspend fun upsertHabitIdentity(row: HabitIdentityRow)

    suspend fun fetchHabitsSince(userId: String, sinceMs: Long): List<Habit>
    suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity>
    suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog>
    suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog>
    suspend fun fetchUserIdentitiesSince(userId: String, sinceMs: Long): List<UserIdentityRow>
    suspend fun fetchHabitIdentitiesSince(userId: String, sinceMs: Long): List<HabitIdentityRow>
}
