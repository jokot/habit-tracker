package com.habittracker.data.repository

import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeWantLogRepository : WantLogRepository {
    private val _logs = mutableListOf<WantLog>()
    val logs: List<WantLog> get() = _logs

    override suspend fun insertLog(
        id: String, userId: String, activityId: String,
        quantity: Double, deviceMode: DeviceMode, loggedAt: Instant,
    ): WantLog {
        val log = WantLog(id = id, userId = userId, activityId = activityId, quantity = quantity, deviceMode = deviceMode, loggedAt = loggedAt)
        _logs.add(log)
        return log
    }

    override suspend fun softDelete(logId: String, userId: String) {
        val i = _logs.indexOfFirst { it.id == logId && it.userId == userId }
        if (i >= 0) _logs[i] = _logs[i].copy(deletedAt = Clock.System.now())
    }

    override suspend fun getAllActiveLogsForUser(userId: String): List<WantLog> =
        _logs.filter { it.isActive && it.userId == userId }
}
