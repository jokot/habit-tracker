package com.habittracker.data.repository

import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeWantLogRepository : WantLogRepository {
    private val _logs = MutableStateFlow<List<WantLog>>(emptyList())
    val logs: List<WantLog> get() = _logs.value

    override suspend fun insertLog(
        id: String, userId: String, activityId: String,
        quantity: Double, deviceMode: DeviceMode, loggedAt: Instant,
    ): WantLog {
        val log = WantLog(id = id, userId = userId, activityId = activityId, quantity = quantity, deviceMode = deviceMode, loggedAt = loggedAt)
        _logs.value = _logs.value + log
        return log
    }

    override suspend fun softDelete(logId: String, userId: String) {
        _logs.value = _logs.value.map {
            if (it.id == logId && it.userId == userId) it.copy(deletedAt = Clock.System.now()) else it
        }
    }

    override fun observeAllActiveLogsForUser(userId: String): Flow<List<WantLog>> =
        _logs.map { list -> list.filter { it.isActive && it.userId == userId } }

    override suspend fun getAllActiveLogsForUser(userId: String): List<WantLog> =
        _logs.value.filter { it.isActive && it.userId == userId }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        _logs.value = _logs.value.map {
            if (it.userId == oldUserId) it.copy(userId = newUserId) else it
        }
    }

    override suspend fun clearForUser(userId: String) {
        _logs.value = _logs.value.filterNot { it.userId == userId }
    }

    override suspend fun getUnsyncedFor(userId: String): List<WantLog> =
        _logs.value.filter { it.userId == userId && it.syncedAt == null }

    override suspend fun markSynced(id: String, syncedAt: Instant) {
        _logs.value = _logs.value.map {
            if (it.id == id) it.copy(syncedAt = syncedAt) else it
        }
    }

    override suspend fun mergePulled(row: WantLog) {
        _logs.value = _logs.value.filterNot { it.id == row.id } + row
    }
}
