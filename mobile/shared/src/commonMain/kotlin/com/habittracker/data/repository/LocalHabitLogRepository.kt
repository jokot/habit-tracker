package com.habittracker.data.repository

import com.habittracker.data.local.HabitLog as HabitLogEntity
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.domain.model.HabitLog
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class LocalHabitLogRepository(
    private val db: HabitTrackerDatabase,
) : HabitLogRepository {

    override suspend fun insertLog(
        id: String,
        userId: String,
        habitId: String,
        quantity: Double,
        loggedAt: Instant,
    ): HabitLog {
        db.habitTrackerDatabaseQueries.insertHabitLog(
            id = id,
            userId = userId,
            habitId = habitId,
            quantity = quantity,
            loggedAt = loggedAt.toEpochMilliseconds(),
            deletedAt = null,
            syncedAt = null,
        )
        return HabitLog(id = id, userId = userId, habitId = habitId, quantity = quantity, loggedAt = loggedAt)
    }

    override suspend fun softDelete(logId: String, userId: String) {
        db.habitTrackerDatabaseQueries.softDeleteHabitLog(
            deletedAt = Clock.System.now().toEpochMilliseconds(),
            id = logId,
            userId = userId,
        )
    }

    override suspend fun getActiveLogsForHabitOnDay(
        userId: String,
        habitId: String,
        dayStart: Instant,
        dayEnd: Instant,
    ): List<HabitLog> =
        db.habitTrackerDatabaseQueries
            .getActiveHabitLogsForHabitOnDay(
                userId = userId,
                habitId = habitId,
                loggedAt = dayStart.toEpochMilliseconds(),
                loggedAt_ = dayEnd.toEpochMilliseconds(),
            )
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog> =
        db.habitTrackerDatabaseQueries
            .getAllActiveHabitLogsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }
}

private fun HabitLogEntity.toDomain(): HabitLog = HabitLog(
    id = id,
    userId = userId,
    habitId = habitId,
    quantity = quantity,
    loggedAt = Instant.fromEpochMilliseconds(loggedAt),
    deletedAt = deletedAt?.let { Instant.fromEpochMilliseconds(it) },
    syncedAt = syncedAt?.let { Instant.fromEpochMilliseconds(it) },
)
