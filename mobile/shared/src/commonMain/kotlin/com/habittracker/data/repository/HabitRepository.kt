package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface HabitRepository {
    fun observeHabitsForUser(userId: String): Flow<List<Habit>>
    suspend fun getHabitsForUser(userId: String): List<Habit>
    suspend fun saveHabit(habit: Habit)
    suspend fun deleteHabit(habitId: String, userId: String)
    suspend fun migrateUserId(oldUserId: String, newUserId: String)
    suspend fun clearForUser(userId: String)
    suspend fun getUnsyncedFor(userId: String): List<Habit>
    suspend fun markSynced(id: String, syncedAt: Instant)
    suspend fun getByIdsForUser(userId: String, ids: List<String>): List<Habit>
    suspend fun mergePulled(row: Habit)
    suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant)
}
