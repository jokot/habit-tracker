package com.habittracker.domain.usecase

import com.habittracker.domain.model.RateTier

/**
 * Stepped tier ladder for the Phase 6 exchange rate. Pure + stateless.
 *
 * Rate applies to Want spending only — habit earning is unchanged.
 */
object ExchangeRateCalculator {
    val tiers: List<RateTier> = listOf(
        RateTier(level = 1, rate = 1.0, minStreak = 0,  maxStreak = 6),
        RateTier(level = 2, rate = 1.2, minStreak = 7,  maxStreak = 13),
        RateTier(level = 3, rate = 1.4, minStreak = 14, maxStreak = 20),
        RateTier(level = 4, rate = 1.6, minStreak = 21, maxStreak = 29),
        RateTier(level = 5, rate = 2.0, minStreak = 30, maxStreak = null),
    )

    fun rateFor(streak: Int): Double = tierFor(streak).rate

    fun tierFor(streak: Int): RateTier = tiers.first { tier ->
        streak >= tier.minStreak && (tier.maxStreak == null || streak <= tier.maxStreak)
    }

    /** Days remaining until the user moves to the next tier. Null at the top tier. */
    fun daysToNextTier(streak: Int): Int? {
        val current = tierFor(streak)
        val next = tiers.firstOrNull { it.level == current.level + 1 } ?: return null
        return next.minStreak - streak
    }
}
