package com.habittracker.domain.model

/**
 * One step on the exchange-rate ladder. The rate applies to every Want spend made
 * while the user-level streak falls within `minStreak..maxStreak` (inclusive). A
 * `maxStreak` of `null` denotes the top tier (no upper bound).
 */
data class RateTier(
    val level: Int,
    val rate: Double,
    val minStreak: Int,
    val maxStreak: Int?,
)
