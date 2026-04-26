package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class Habit(
    val id: String,
    val userId: String,
    val templateId: String,
    val name: String,
    val unit: String,
    val thresholdPerPoint: Double,
    val dailyTarget: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncedAt: Instant? = null,
)
