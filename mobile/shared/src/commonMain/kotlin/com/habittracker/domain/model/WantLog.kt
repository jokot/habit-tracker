package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class WantLog(
    val id: String,
    val userId: String,
    val activityId: String,
    val quantity: Double,
    val deviceMode: DeviceMode,
    val loggedAt: Instant,
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null,
) {
    val isActive: Boolean get() = deletedAt == null
}
