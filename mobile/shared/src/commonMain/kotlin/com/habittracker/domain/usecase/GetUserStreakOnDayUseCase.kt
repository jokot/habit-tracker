package com.habittracker.domain.usecase

import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.StreakDayState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Returns the user-level streak ending on `date`. Mirrors
 * `ComputeStreakUseCase.computeSummaryNow`'s state machine while walking
 * backward from `date`:
 *
 * - COMPLETE → +1, continue.
 * - FROZEN → streak survives; don't increment, continue.
 * - TODAY_PENDING → not yet logged today; don't increment, continue
 *   (yesterday's run still stands so the rate matches what the user sees on
 *   the ExchangeRate screen even before today is closed).
 * - BROKEN / EMPTY / FUTURE / missing → break.
 *
 * Used by LogWantUseCase + GetPointBalanceUseCase + GetDayPointsUseCase to
 * apply rate-at-log-day for Want spends (Phase 6).
 */
class GetUserStreakOnDayUseCase(
    private val streakUseCase: ComputeStreakUseCase,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend fun execute(userId: String, date: LocalDate): Int {
        val window = DateRange(
            start = date.minus(365, DateTimeUnit.DAY),
            endExclusive = date.plus(1, DateTimeUnit.DAY),
        )
        val result = streakUseCase.computeNow(userId, window)
        val byDate = result.days.associateBy { it.date }
        var run = 0
        var cursor = date
        while (true) {
            val state = byDate[cursor]?.state ?: break
            when (state) {
                StreakDayState.COMPLETE -> run += 1
                StreakDayState.FROZEN, StreakDayState.TODAY_PENDING -> Unit
                StreakDayState.BROKEN, StreakDayState.EMPTY, StreakDayState.FUTURE -> break
            }
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        return run
    }
}
