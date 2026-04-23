package com.habittracker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.WantLog as WantLogEntity
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    override fun observeAllActiveLogsForUser(userId: String): Flow<List<WantLog>> =
        db.habitTrackerDatabaseQueries
            .getAllActiveWantLogsForUser(userId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getAllActiveLogsForUser(userId: String): List<WantLog> =
        db.habitTrackerDatabaseQueries
            .getAllActiveWantLogsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        db.habitTrackerDatabaseQueries.migrateWantLogsUserId(newUserId, oldUserId)
    }

    override suspend fun clearForUser(userId: String) {
        db.habitTrackerDatabaseQueries.clearWantLogsForUser(userId)
    }

    override suspend fun getUnsyncedFor(userId: String): List<WantLog> =
        db.habitTrackerDatabaseQueries
            .getUnsyncedWantLogsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun markSynced(id: String, syncedAt: Instant) {
        db.habitTrackerDatabaseQueries.markWantLogSynced(syncedAt.toEpochMilliseconds(), id)
    }

    override suspend fun mergePulled(row: WantLog) {
        db.habitTrackerDatabaseQueries.insertWantLog(
            id = row.id,
            userId = row.userId,
            activityId = row.activityId,
            quantity = row.quantity,
            deviceMode = row.deviceMode.toDbValue(),
            loggedAt = row.loggedAt.toEpochMilliseconds(),
            deletedAt = row.deletedAt?.toEpochMilliseconds(),
            syncedAt = row.syncedAt?.toEpochMilliseconds(),
        )
    }
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
