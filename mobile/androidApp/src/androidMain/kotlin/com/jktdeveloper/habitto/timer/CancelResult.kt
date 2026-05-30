package com.jktdeveloper.habitto.timer

sealed interface CancelResult {
    object NoActiveTimer : CancelResult
    object Discarded : CancelResult
    data class Logged(val minutes: Int, val pointsSpent: Int) : CancelResult
}
