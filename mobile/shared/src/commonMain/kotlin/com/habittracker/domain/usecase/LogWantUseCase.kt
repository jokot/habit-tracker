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
    /** Called after each saved log. The app uses it for analytics. */
    private val onLogged: (LogWantResult) -> Unit = {},
) {
    /**
     * [quantity] replaces the units that the log records. A want timer uses it to record
     * the minutes that it ran. Without it, the log records 1 cost unit for each tap.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(
        userId: String,
        activityId: String,
        taps: Int = 1,
        deviceMode: DeviceMode,
        quantity: Double? = null,
    ): Result<LogWantResult> = runCatching {
        require(taps >= 1) { "taps must be >= 1" }
        val effUnits = unitsPerPoint(userId, activityId)
            ?: error("Activity $activityId not found")
        val loggedQuantity = quantity ?: (effUnits.toLong() * taps.toLong()).toDouble()
        val points = PointCalculator.pointsSpent(taps)
        val now = clock.now()

        val balance = getPointBalanceUseCase.execute(userId).getOrThrow().balance
        if (points > balance) throw InsufficientPointsException(balance, points)

        val id = Uuid.random().toString()
        val log = wantLogRepository.insertLog(
            id = id,
            userId = userId,
            activityId = activityId,
            quantity = loggedQuantity,
            pointsSpent = points,
            deviceMode = deviceMode,
            loggedAt = now,
        )
        LogWantResult(log, points)
    }.onSuccess(onLogged)

    /** The units that 1 point buys now, with the rate of today. Null if the want does not exist. */
    suspend fun unitsPerPoint(userId: String, activityId: String): Int? {
        val activity = wantActivityRepository.getWantActivities(userId)
            .firstOrNull { it.id == activityId }
            ?: return null
        val today = clock.now().toLocalDateTime(timeZone).date
        val rate = ExchangeRateCalculator.rateFor(getUserStreakOnDayUseCase.execute(userId, today))
        return PointCalculator.effectiveUnitsPerPoint(activity.unitsPerPoint, rate)
    }
}
