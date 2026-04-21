package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository

class UndoHabitLogUseCase(private val repository: HabitLogRepository) {
    suspend fun execute(logId: String, userId: String): Result<Unit> =
        runCatching { repository.softDelete(logId, userId) }
}
