package com.habittracker.data.repository

import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface WantLogRepository {
    suspend fun insertLog(
        id: String,
        userId: String,
        activityId: String,
        quantity: Double,
        deviceMode: DeviceMode,
        loggedAt: Instant,
    ): WantLog

    suspend fun softDelete(logId: String, userId: String)

    fun observeAllActiveLogsForUser(userId: String): Flow<List<WantLog>>

    suspend fun getAllActiveLogsForUser(userId: String): List<WantLog>

    suspend fun migrateUserId(oldUserId: String, newUserId: String)

    suspend fun clearForUser(userId: String)

    suspend fun getUnsyncedFor(userId: String): List<WantLog>

    suspend fun markSynced(id: String, syncedAt: Instant)

    suspend fun mergePulled(row: WantLog)
}
