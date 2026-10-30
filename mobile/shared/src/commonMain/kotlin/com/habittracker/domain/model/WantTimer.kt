package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class WantTimer(
    val id: String,
    val userId: String,
    val activityId: String,
    val durationSec: Int,
    val startedAt: Instant,
    val endsAt: Instant,
    val state: WantTimerState,
    /** Minutes that 1 point buys, fixed at the start: the cost of the want with the rate of that day. */
    val minutesPerPoint: Int = 1,
    /** Points that the timer has spent so far. */
    val pointsSpent: Int = 0,
)

enum class WantTimerState { RUNNING, FINISHED, CANCELLED }
