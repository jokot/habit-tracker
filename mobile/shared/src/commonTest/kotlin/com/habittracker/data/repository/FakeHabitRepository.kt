package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class FakeHabitRepository : HabitRepository {
    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: List<Habit> get() = _habits.value

    override suspend fun getHabitsForUser(userId: String): List<Habit> =
        _habits.value.filter { it.userId == userId }

    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> =
        _habits.map { list -> list.filter { it.userId == userId } }

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

    override suspend fun getUnsyncedFor(userId: String): List<Habit> =
        _habits.value.filter { it.userId == userId && it.syncedAt == null }

    override suspend fun markSynced(id: String, syncedAt: Instant) {
        _habits.value = _habits.value.map {
            if (it.id == id) it.copy(syncedAt = syncedAt) else it
        }
    }

    override suspend fun getByIdsForUser(userId: String, ids: List<String>): List<Habit> =
        _habits.value.filter { it.userId == userId && it.id in ids }

    override suspend fun mergePulled(row: Habit) {
        _habits.value = _habits.value.filterNot { it.id == row.id } + row
    }

    override suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant) {
        _habits.value = _habits.value.map {
            if (it.id == habitId && it.userId == userId) {
                it.copy(effectiveTo = effectiveTo, syncedAt = null)
            } else it
        }
    }
}
