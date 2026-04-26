package com.habittracker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalHabit
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class LocalHabitRepository(
    private val db: HabitTrackerDatabase,
) : HabitRepository {

    override suspend fun getHabitsForUser(userId: String): List<Habit> =
        db.habitTrackerDatabaseQueries
            .getHabitsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> =
        db.habitTrackerDatabaseQueries
            .getHabitsForUser(userId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun saveHabit(habit: Habit) {
        val updatedAt = habit.updatedAt.takeIf { it.toEpochMilliseconds() > 0L }
            ?: Clock.System.now()
        db.habitTrackerDatabaseQueries.upsertHabit(
            id = habit.id,
            userId = habit.userId,
            templateId = habit.templateId,
            name = habit.name,
            unit = habit.unit,
            thresholdPerPoint = habit.thresholdPerPoint,
            dailyTarget = habit.dailyTarget.toLong(),
            createdAt = habit.createdAt.toEpochMilliseconds(),
            updatedAt = updatedAt.toEpochMilliseconds(),
        )
    }

    override suspend fun deleteHabit(habitId: String, userId: String) {
        db.habitTrackerDatabaseQueries.deleteHabit(id = habitId, userId = userId)
    }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        db.habitTrackerDatabaseQueries.migrateHabitsUserId(newUserId, oldUserId)
    }

    override suspend fun clearForUser(userId: String) {
        db.habitTrackerDatabaseQueries.clearHabitsForUser(userId)
    }

    override suspend fun getUnsyncedFor(userId: String): List<Habit> =
        db.habitTrackerDatabaseQueries
            .getUnsyncedHabitsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun markSynced(id: String, syncedAt: Instant) {
        db.habitTrackerDatabaseQueries.markHabitSynced(syncedAt.toEpochMilliseconds(), id)
    }

    override suspend fun getByIdsForUser(userId: String, ids: List<String>): List<Habit> {
        if (ids.isEmpty()) return emptyList()
        return db.habitTrackerDatabaseQueries
            .getHabitsByIdForUser(userId, ids)
            .executeAsList()
            .map { it.toDomain() }
    }

    override suspend fun mergePulled(row: Habit) {
        db.habitTrackerDatabaseQueries.mergePulledHabit(
            id = row.id,
            userId = row.userId,
            templateId = row.templateId,
            name = row.name,
            unit = row.unit,
            thresholdPerPoint = row.thresholdPerPoint,
            dailyTarget = row.dailyTarget.toLong(),
            createdAt = row.createdAt.toEpochMilliseconds(),
            updatedAt = row.updatedAt.toEpochMilliseconds(),
            syncedAt = row.syncedAt?.toEpochMilliseconds(),
        )
    }
}

private fun LocalHabit.toDomain(): Habit = Habit(
    id = id,
    userId = userId,
    templateId = templateId,
    name = name,
    unit = unit,
    thresholdPerPoint = thresholdPerPoint,
    dailyTarget = dailyTarget.toInt(),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    syncedAt = syncedAt?.let { Instant.fromEpochMilliseconds(it) },
)
