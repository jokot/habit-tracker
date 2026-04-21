package com.habittracker.data.repository

import com.habittracker.domain.model.HabitLog
import kotlinx.datetime.Instant

interface HabitLogRepository {
    suspend fun insertLog(
        id: String,
        userId: String,
        habitId: String,
        quantity: Double,
        loggedAt: Instant,
    ): HabitLog

    suspend fun softDelete(logId: String, userId: String)

    suspend fun getActiveLogsForHabitOnDay(
        userId: String,
        habitId: String,
        dayStart: Instant,
        dayEnd: Instant,
    ): List<HabitLog>

    suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog>
}
