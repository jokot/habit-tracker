package com.habittracker.domain.usecase

object PointCalculator {
    /** Habit side — units accumulate, points = floor(quantity / threshold). */
    fun pointsEarned(quantity: Double, threshold: Double): Int =
        if (threshold <= 0.0) 0 else (quantity / threshold).toInt()

    /** Want side — one tap is one point. Multi-tap sums. */
    fun pointsSpent(taps: Int): Int = taps.coerceAtLeast(0)

    /**
     * Higher rate squeezes the unit count behind a single −1 pt tap.
     * Clamped to 1 so cheap wants (unitsPerPoint = 1) stay at 1 unit per tap
     * regardless of tier.
     */
    fun effectiveUnitsPerPoint(unitsPerPoint: Int, rate: Double): Int =
        if (unitsPerPoint <= 0 || rate <= 0.0) 1
        else (unitsPerPoint / rate).toInt().coerceAtLeast(1)
}
