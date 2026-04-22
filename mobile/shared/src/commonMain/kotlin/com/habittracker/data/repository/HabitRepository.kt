package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    fun observeHabitsForUser(userId: String): Flow<List<Habit>>
    suspend fun getHabitsForUser(userId: String): List<Habit>
    suspend fun saveHabit(habit: Habit)
    suspend fun deleteHabit(habitId: String, userId: String)
    suspend fun migrateUserId(oldUserId: String, newUserId: String)
    suspend fun clearForUser(userId: String)
}
