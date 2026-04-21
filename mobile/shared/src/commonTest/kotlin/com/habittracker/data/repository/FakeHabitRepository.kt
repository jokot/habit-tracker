package com.habittracker.data.repository

import com.habittracker.domain.model.Habit

class FakeHabitRepository : HabitRepository {
    val habits = mutableListOf<Habit>()

    override suspend fun getHabitsForUser(userId: String): List<Habit> =
        habits.filter { it.userId == userId }

    override suspend fun saveHabit(habit: Habit) {
        habits.removeAll { it.id == habit.id }
        habits.add(habit)
    }

    override suspend fun deleteHabit(habitId: String, userId: String) {
        habits.removeAll { it.id == habitId && it.userId == userId }
    }
}
