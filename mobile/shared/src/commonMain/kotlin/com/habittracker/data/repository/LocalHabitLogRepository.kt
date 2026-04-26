package com.habittracker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.habittracker.data.local.HabitLog as HabitLogEntity
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.domain.model.HabitLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    override fun observeActiveLogsForHabitOnDay(
        userId: String,
        habitId: String,
        dayStart: Instant,
        dayEnd: Instant,
    ): Flow<List<HabitLog>> =
        db.habitTrackerDatabaseQueries
            .getActiveHabitLogsForHabitOnDay(
                userId = userId,
                habitId = habitId,
                loggedAt = dayStart.toEpochMilliseconds(),
                loggedAt_ = dayEnd.toEpochMilliseconds(),
            )
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

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

    override fun observeAllActiveLogsForUser(userId: String): Flow<List<HabitLog>> =
        db.habitTrackerDatabaseQueries
            .getAllActiveHabitLogsForUser(userId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog> =
        db.habitTrackerDatabaseQueries
            .getAllActiveHabitLogsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        db.habitTrackerDatabaseQueries.migrateHabitLogsUserId(newUserId, oldUserId)
    }

    override suspend fun clearForUser(userId: String) {
        db.habitTrackerDatabaseQueries.clearHabitLogsForUser(userId)
    }

    override suspend fun getUnsyncedFor(userId: String): List<HabitLog> =
        db.habitTrackerDatabaseQueries
            .getUnsyncedHabitLogsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun markSynced(id: String, syncedAt: Instant) {
        db.habitTrackerDatabaseQueries.markHabitLogSynced(syncedAt.toEpochMilliseconds(), id)
    }

    override suspend fun mergePulled(row: HabitLog) {
        db.habitTrackerDatabaseQueries.mergePulledHabitLog(
            id = row.id,
            userId = row.userId,
            habitId = row.habitId,
            quantity = row.quantity,
            loggedAt = row.loggedAt.toEpochMilliseconds(),
            deletedAt = row.deletedAt?.toEpochMilliseconds(),
            syncedAt = row.syncedAt?.toEpochMilliseconds(),
        )
    }
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
