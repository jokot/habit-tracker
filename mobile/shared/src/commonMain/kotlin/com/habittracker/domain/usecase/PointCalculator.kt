package com.habittracker.domain.usecase

object PointCalculator {
    fun pointsEarned(quantity: Double, threshold: Double): Int =
        (quantity / threshold).toInt()

    fun pointsSpent(quantity: Double, costPerUnit: Double): Int =
        (quantity * costPerUnit).toInt()
}
