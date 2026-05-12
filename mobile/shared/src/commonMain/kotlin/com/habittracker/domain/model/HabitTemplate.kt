package com.habittracker.domain.model

data class HabitTemplate(
    val id: String,
    val name: String,
    val unit: String,
    val defaultThreshold: Double,
    val defaultDailyTarget: Int,
    val iconName: String? = null,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
)
