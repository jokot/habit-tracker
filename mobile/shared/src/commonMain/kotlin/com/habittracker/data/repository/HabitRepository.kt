package com.habittracker.data.repository

import com.habittracker.domain.model.Habit

interface HabitRepository {
    suspend fun getHabitsForUser(userId: String): List<Habit>
    suspend fun saveHabit(habit: Habit)
    suspend fun deleteHabit(habitId: String, userId: String)
}
