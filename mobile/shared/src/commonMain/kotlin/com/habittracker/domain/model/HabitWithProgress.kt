package com.habittracker.domain.model

data class HabitWithProgress(
    val habit: Habit,
    val pointsToday: Int,
) {
    val isGoalMet: Boolean get() = pointsToday >= habit.dailyTarget
    val progressFraction: Float get() = (pointsToday.toFloat() / habit.dailyTarget).coerceAtMost(1f)
    val progressText: String get() = "$pointsToday / ${habit.dailyTarget}"
}
