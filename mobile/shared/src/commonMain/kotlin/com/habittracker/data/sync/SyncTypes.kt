package com.habittracker.data.sync

import kotlinx.datetime.Instant

/** Why the sync is running — useful for telemetry / debug. */
enum class SyncReason {
    APP_FOREGROUND,
    POST_LOG,
    MANUAL,
    POST_SIGN_IN,
    WIDGET_WRITE,
}

/** Latest visible sync state for ViewModels to render. */
sealed class SyncState {
    object Idle : SyncState()
    data class Running(val since: Instant, val reason: SyncReason) : SyncState()
    data class Synced(val at: Instant, val pushed: Int, val pulled: Int) : SyncState()
    data class Error(val message: String, val since: Instant) : SyncState()
}

/** Outcome of a single sync attempt. */
data class SyncOutcome(val pushed: Int, val pulled: Int)
