package com.habittracker.data.repository

import com.habittracker.domain.model.HabitLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeHabitLogRepository : HabitLogRepository {
    private val _logs = MutableStateFlow<List<HabitLog>>(emptyList())
    val logs: List<HabitLog> get() = _logs.value

    override suspend fun insertLog(
        id: String, userId: String, habitId: String,
        quantity: Double, loggedAt: Instant,
    ): HabitLog {
        val log = HabitLog(id = id, userId = userId, habitId = habitId, quantity = quantity, loggedAt = loggedAt)
        _logs.value = _logs.value + log
        return log
    }

    override suspend fun softDelete(logId: String, userId: String) {
        _logs.value = _logs.value.map {
            if (it.id == logId && it.userId == userId) it.copy(deletedAt = Clock.System.now()) else it
        }
    }

    override fun observeActiveLogsForHabitOnDay(
        userId: String, habitId: String, dayStart: Instant, dayEnd: Instant,
    ): Flow<List<HabitLog>> = _logs.map { list ->
        list.filter {
            it.isActive && it.userId == userId && it.habitId == habitId
                && it.loggedAt >= dayStart && it.loggedAt < dayEnd
        }
    }

    override suspend fun getActiveLogsForHabitOnDay(
        userId: String, habitId: String, dayStart: Instant, dayEnd: Instant,
    ): List<HabitLog> = _logs.value.filter {
        it.isActive && it.userId == userId && it.habitId == habitId
            && it.loggedAt >= dayStart && it.loggedAt < dayEnd
    }

    override fun observeAllActiveLogsForUser(userId: String): Flow<List<HabitLog>> =
        _logs.map { list -> list.filter { it.isActive && it.userId == userId } }

    override suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog> =
        _logs.value.filter { it.isActive && it.userId == userId }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        _logs.value = _logs.value.map {
            if (it.userId == oldUserId) it.copy(userId = newUserId) else it
        }
    }

    override suspend fun clearForUser(userId: String) {
        _logs.value = _logs.value.filterNot { it.userId == userId }
    }

    override suspend fun getUnsyncedFor(userId: String): List<HabitLog> =
        _logs.value.filter { it.userId == userId && it.syncedAt == null }

    override suspend fun markSynced(id: String, syncedAt: Instant) {
        _logs.value = _logs.value.map {
            if (it.id == id) it.copy(syncedAt = syncedAt) else it
        }
    }

    override suspend fun mergePulled(row: HabitLog) {
        _logs.value = _logs.value.filterNot { it.id == row.id } + row
    }
}
