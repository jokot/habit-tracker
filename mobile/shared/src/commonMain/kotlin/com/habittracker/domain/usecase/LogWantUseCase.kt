package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
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
        quantity: Double,
        deviceMode: DeviceMode,
    ): Result<LogWantResult> = runCatching {
        val activity = wantActivityRepository.getWantActivities(userId)
            .firstOrNull { it.id == activityId }
            ?: error("Activity $activityId not found")

        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val streakOnDay = getUserStreakOnDayUseCase.execute(userId, today)
        val rate = ExchangeRateCalculator.rateFor(streakOnDay)
        val points = pointsSpentWithRate(quantity, activity.costPerUnit, rate)

        val balance = getPointBalanceUseCase.execute(userId).getOrThrow().balance
        if (points > balance) throw InsufficientPointsException(balance, points)

        val id = Uuid.random().toString()
        val log = wantLogRepository.insertLog(id, userId, activityId, quantity, deviceMode, now)
        LogWantResult(log, points)
    }

    /** Cost × rate, rounded up, with `1pt` minimum if any quantity was consumed. */
    internal fun pointsSpentWithRate(quantity: Double, costPerUnit: Double, rate: Double): Int {
        if (quantity <= 0.0 || costPerUnit <= 0.0) return 0
        return ceil(quantity * costPerUnit * rate).toInt().coerceAtLeast(1)
    }
}
