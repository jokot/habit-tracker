package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHabitRepository : HabitRepository {
    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: List<Habit> get() = _habits.value

    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> =
        _habits.map { list -> list.filter { it.userId == userId } }

    override suspend fun getHabitsForUser(userId: String): List<Habit> =
        _habits.value.filter { it.userId == userId }

    override suspend fun saveHabit(habit: Habit) {
        _habits.value = _habits.value.filterNot { it.id == habit.id } + habit
    }

    override suspend fun deleteHabit(habitId: String, userId: String) {
        _habits.value = _habits.value.filterNot { it.id == habitId && it.userId == userId }
    }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        _habits.value = _habits.value.map {
            if (it.userId == oldUserId) it.copy(userId = newUserId) else it
        }
    }

    override suspend fun clearForUser(userId: String) {
        _habits.value = _habits.value.filterNot { it.userId == userId }
    }
}
