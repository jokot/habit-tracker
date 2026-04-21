package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class LogWantResult(val log: WantLog, val pointsSpent: Int)

class LogWantUseCase(
    private val wantLogRepository: WantLogRepository,
    private val wantActivityRepository: WantActivityRepository,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(
        userId: String,
        activityId: String,
        quantity: Double,
        deviceMode: DeviceMode,
    ): Result<LogWantResult> = runCatching {
        val activity = wantActivityRepository.getWantActivities(userId)
            .firstOrNull { it.id == activityId }
            ?: error("Activity $activityId not found")
        val now = Clock.System.now()
        val id = Uuid.random().toString()
        val log = wantLogRepository.insertLog(id, userId, activityId, quantity, deviceMode, now)
        val points = PointCalculator.pointsSpent(quantity, activity.costPerUnit)
        LogWantResult(log, points)
    }
}
