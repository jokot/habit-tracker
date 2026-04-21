package com.habittracker.data.repository

import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
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

    suspend fun getAllActiveLogsForUser(userId: String): List<WantLog>
}
