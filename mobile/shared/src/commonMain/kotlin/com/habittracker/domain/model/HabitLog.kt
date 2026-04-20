package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class HabitLog(
    val id: String,
    val userId: String,
    val habitId: String,
    val quantity: Double,
    val loggedAt: Instant,
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null,
) {
    val isActive: Boolean get() = deletedAt == null
}
