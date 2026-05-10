package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class LogWantResult(val log: WantLog, val pointsSpent: Int)

class InsufficientPointsException(
    val available: Int,
    val required: Int,
) : Exception("Not enough points: need $required, have $available")

class LogWantUseCase(
    private val wantLogRepository: WantLogRepository,
    private val wantActivityRepository: WantActivityRepository,
    private val getPointBalanceUseCase: GetPointBalanceUseCase,
    private val getUserStreakOnDayUseCase: GetUserStreakOnDayUseCase,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(
        userId: String,
        activityId: String,
        taps: Int = 1,
        deviceMode: DeviceMode,
    ): Result<LogWantResult> = runCatching {
        require(taps >= 1) { "taps must be >= 1" }
        val activity = wantActivityRepository.getWantActivities(userId)
            .firstOrNull { it.id == activityId }
            ?: error("Activity $activityId not found")

        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val streakOnDay = getUserStreakOnDayUseCase.execute(userId, today)
        val rate = ExchangeRateCalculator.rateFor(streakOnDay)
        val effUnits = PointCalculator.effectiveUnitsPerPoint(activity.unitsPerPoint, rate)
        val quantity = (effUnits.toLong() * taps.toLong()).toDouble()
        val points = PointCalculator.pointsSpent(taps)

        val balance = getPointBalanceUseCase.execute(userId).getOrThrow().balance
        if (points > balance) throw InsufficientPointsException(balance, points)

        val id = Uuid.random().toString()
        val log = wantLogRepository.insertLog(
            id = id,
            userId = userId,
            activityId = activityId,
            quantity = quantity,
            pointsSpent = points,
            deviceMode = deviceMode,
            loggedAt = now,
        )
        LogWantResult(log, points)
    }
}
