package com.habittracker.domain.usecase

import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.StreakDayState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Returns the user-level streak ending on `date` — count of consecutive COMPLETE
 * days going backward from `date` inclusive. Returns 0 if `date` itself is not
 * COMPLETE or the user has no relevant logs.
 *
 * Used by GetPointBalanceUseCase + GetDayPointsUseCase to apply rate-at-log-day
 * for past Want spends (Phase 6).
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
            if (state == StreakDayState.COMPLETE) {
                run += 1
                cursor = cursor.minus(1, DateTimeUnit.DAY)
            } else break
        }
        return run
    }
}
