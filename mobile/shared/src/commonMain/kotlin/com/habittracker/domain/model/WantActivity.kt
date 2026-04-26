package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class WantActivity(
    val id: String,
    val name: String,
    val unit: String,
    val costPerUnit: Double,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
    val updatedAt: Instant = Instant.fromEpochMilliseconds(0L),
    val syncedAt: Instant? = null,
)
