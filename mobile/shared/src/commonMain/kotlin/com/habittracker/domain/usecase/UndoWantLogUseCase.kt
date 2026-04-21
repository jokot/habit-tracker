package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantLogRepository

class UndoWantLogUseCase(private val repository: WantLogRepository) {
    suspend fun execute(logId: String, userId: String): Result<Unit> =
        runCatching { repository.softDelete(logId, userId) }
}
