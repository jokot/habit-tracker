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
)

enum class WantTimerState { RUNNING, FINISHED, CANCELLED }
