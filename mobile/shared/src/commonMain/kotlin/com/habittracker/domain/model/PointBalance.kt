package com.habittracker.domain.model

data class PointBalance(
    val earned: Int,
    val spent: Int,
    val balance: Int,
    val earnedToday: Int = 0,
    val spentToday: Int = 0,
)
