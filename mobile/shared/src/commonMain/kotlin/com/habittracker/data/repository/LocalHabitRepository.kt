package com.habittracker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalHabit
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class LocalHabitRepository(
    private val db: HabitTrackerDatabase,
) : HabitRepository {

    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> =
        db.habitTrackerDatabaseQueries
            .getHabitsForUser(userId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getHabitsForUser(userId: String): List<Habit> =
        db.habitTrackerDatabaseQueries
            .getHabitsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun saveHabit(habit: Habit) {
        db.habitTrackerDatabaseQueries.upsertHabit(
            id = habit.id,
            userId = habit.userId,
            templateId = habit.templateId,
            name = habit.name,
            unit = habit.unit,
            thresholdPerPoint = habit.thresholdPerPoint,
            dailyTarget = habit.dailyTarget.toLong(),
            createdAt = habit.createdAt.toEpochMilliseconds(),
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
)
