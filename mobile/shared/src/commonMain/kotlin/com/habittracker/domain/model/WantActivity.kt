package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class WantActivity(
    val id: String,
    val name: String,
    val unit: String,
    val unitsPerPoint: Int,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
    val iconKey: String? = null,
    val hiddenAt: Instant? = null,
    val updatedAt: Instant = Instant.fromEpochMilliseconds(0),
    val syncedAt: Instant? = null,
)
