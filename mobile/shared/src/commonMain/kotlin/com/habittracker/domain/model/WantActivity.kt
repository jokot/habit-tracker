package com.habittracker.domain.model

data class WantActivity(
    val id: String,
    val name: String,
    val unit: String,
    val costPerUnit: Double,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
)
