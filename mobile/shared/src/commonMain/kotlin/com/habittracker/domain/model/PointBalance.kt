package com.habittracker.domain.model

data class PointBalance(val earned: Int, val spent: Int) {
    val balance: Int get() = earned - spent
}
