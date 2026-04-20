package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class UserWantActivity(
    val id: String,
    val userId: String,
    val activityId: String,
    val name: String,
    val unit: String,
    val costPerUnit: Double,
    val createdAt: Instant,
)
