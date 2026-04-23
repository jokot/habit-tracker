package com.habittracker.domain.usecase

import kotlin.math.ceil

object PointCalculator {
    /** Points earned rounds down: need [threshold] quantity for each point. */
    fun pointsEarned(quantity: Double, threshold: Double): Int =
        (quantity / threshold).toInt()

    /**
     * Points spent rounds up: any positive consumption costs at least 1 pt.
     * Prevents "free" micro-sessions (e.g. 1 min of a 2-min-per-pt activity).
     */
    fun pointsSpent(quantity: Double, costPerUnit: Double): Int {
        if (quantity <= 0.0 || costPerUnit <= 0.0) return 0
        return ceil(quantity * costPerUnit).toInt().coerceAtLeast(1)
    }
}
