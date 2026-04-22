package com.habittracker.data.repository

import com.habittracker.domain.model.HabitLog
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeHabitLogRepository : HabitLogRepository {
    private val _logs = mutableListOf<HabitLog>()
    val logs: List<HabitLog> get() = _logs

    override suspend fun insertLog(
        id: String, userId: String, habitId: String,
        quantity: Double, loggedAt: Instant,
    ): HabitLog {
        val log = HabitLog(id = id, userId = userId, habitId = habitId, quantity = quantity, loggedAt = loggedAt)
        _logs.add(log)
        return log
    }

    override suspend fun softDelete(logId: String, userId: String) {
        val i = _logs.indexOfFirst { it.id == logId && it.userId == userId }
        if (i >= 0) _logs[i] = _logs[i].copy(deletedAt = Clock.System.now())
    }

    override suspend fun getActiveLogsForHabitOnDay(
        userId: String, habitId: String, dayStart: Instant, dayEnd: Instant,
    ): List<HabitLog> = _logs.filter {
        it.isActive && it.userId == userId && it.habitId == habitId
            && it.loggedAt >= dayStart && it.loggedAt < dayEnd
    }

    override suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog> =
        _logs.filter { it.isActive && it.userId == userId }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        val indices = _logs.indices.filter { _logs[it].userId == oldUserId }
        indices.forEach { i -> _logs[i] = _logs[i].copy(userId = newUserId) }
    }

    override suspend fun clearForUser(userId: String) {
        _logs.removeAll { it.userId == userId }
    }
}
