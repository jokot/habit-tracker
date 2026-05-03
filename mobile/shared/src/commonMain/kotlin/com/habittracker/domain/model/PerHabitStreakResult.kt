package com.habittracker.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class PerHabitStreakResult(
    val habitId: String,
    val totalLogs: Int,
    val pointsEarned: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val firstLogDate: LocalDate?,
    val last30Days: List<PerHabitDayState>,
) {
    companion object {
        fun emptyFor(habitId: String, today: LocalDate): PerHabitStreakResult {
            val start = today.minus(29, DateTimeUnit.DAY)
            val cells = (0 until 30).map { offset ->
                PerHabitDayState(start.plus(offset, DateTimeUnit.DAY), StreakDayState.EMPTY)
            }
            return PerHabitStreakResult(
                habitId = habitId,
                totalLogs = 0,
                pointsEarned = 0,
                currentStreak = 0,
                longestStreak = 0,
                firstLogDate = null,
                last30Days = cells,
            )
        }
    }
}

data class PerHabitDayState(
    val date: LocalDate,
    val state: StreakDayState,
)
