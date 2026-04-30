package com.habittracker.domain.model

data class DayPoints(
    val earned: Int,
    val spent: Int,
) {
    val net: Int get() = earned - spent
}
