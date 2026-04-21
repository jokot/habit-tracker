package com.habittracker.data.repository

import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.WantLog as WantLogEntity
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class LocalWantLogRepository(
    private val db: HabitTrackerDatabase,
) : WantLogRepository {

    override suspend fun insertLog(
        id: String,
        userId: String,
        activityId: String,
        quantity: Double,
        deviceMode: DeviceMode,
        loggedAt: Instant,
    ): WantLog {
        db.habitTrackerDatabaseQueries.insertWantLog(
            id = id,
            userId = userId,
            activityId = activityId,
            quantity = quantity,
            deviceMode = deviceMode.toDbValue(),
            loggedAt = loggedAt.toEpochMilliseconds(),
            deletedAt = null,
            syncedAt = null,
        )
        return WantLog(id = id, userId = userId, activityId = activityId, quantity = quantity, deviceMode = deviceMode, loggedAt = loggedAt)
    }

    override suspend fun softDelete(logId: String, userId: String) {
        db.habitTrackerDatabaseQueries.softDeleteWantLog(
            deletedAt = Clock.System.now().toEpochMilliseconds(),
            id = logId,
            userId = userId,
        )
    }

    override suspend fun getAllActiveLogsForUser(userId: String): List<WantLog> =
        db.habitTrackerDatabaseQueries
            .getAllActiveWantLogsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }
}

private fun DeviceMode.toDbValue(): String = when (this) {
    DeviceMode.THIS_DEVICE -> "this_device"
    DeviceMode.OTHER -> "other"
}

private fun String.toDeviceMode(): DeviceMode = when (this) {
    "this_device" -> DeviceMode.THIS_DEVICE
    else -> DeviceMode.OTHER
}

private fun WantLogEntity.toDomain(): WantLog = WantLog(
    id = id,
    userId = userId,
    activityId = activityId,
    quantity = quantity,
    deviceMode = deviceMode.toDeviceMode(),
    loggedAt = Instant.fromEpochMilliseconds(loggedAt),
    deletedAt = deletedAt?.let { Instant.fromEpochMilliseconds(it) },
    syncedAt = syncedAt?.let { Instant.fromEpochMilliseconds(it) },
)
