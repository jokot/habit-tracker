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

    /**
     * Cost × rate, rounded up, with `1pt` minimum if any quantity was consumed.
     * Phase 6: rate is the exchange-rate multiplier (1.0..1.4) keyed off user-level streak.
     */
    fun pointsSpentWithRate(quantity: Double, costPerUnit: Double, rate: Double): Int {
        if (quantity <= 0.0 || costPerUnit <= 0.0) return 0
        return ceil(quantity * costPerUnit * rate).toInt().coerceAtLeast(1)
    }
}
