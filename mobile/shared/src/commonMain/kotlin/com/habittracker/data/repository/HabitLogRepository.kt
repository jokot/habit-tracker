package com.habittracker.data.repository

import com.habittracker.domain.model.HabitLog
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface HabitLogRepository {
    suspend fun insertLog(
        id: String,
        userId: String,
        habitId: String,
        quantity: Double,
        loggedAt: Instant,
    ): HabitLog

    suspend fun softDelete(logId: String, userId: String)

    fun observeActiveLogsForHabitOnDay(
        userId: String,
        habitId: String,
        dayStart: Instant,
        dayEnd: Instant,
    ): Flow<List<HabitLog>>

    suspend fun getActiveLogsForHabitOnDay(
        userId: String,
        habitId: String,
        dayStart: Instant,
        dayEnd: Instant,
    ): List<HabitLog>

    fun observeAllActiveLogsForUser(userId: String): Flow<List<HabitLog>>

    suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog>

    suspend fun migrateUserId(oldUserId: String, newUserId: String)

    suspend fun clearForUser(userId: String)

    suspend fun getUnsyncedFor(userId: String): List<HabitLog>

    suspend fun markSynced(id: String, syncedAt: Instant)

    suspend fun mergePulled(row: HabitLog)
}
