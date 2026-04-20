package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class Goal(
    val id: String,
    val userId: String,
    val identityId: String,
    val createdAt: Instant,
)
