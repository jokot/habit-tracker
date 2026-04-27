package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.work.WorkManager

/**
 * Single entry point for managing notification work.
 *
 * Filled in by Task 13 once all worker classes (Tasks 10–12) exist.
 */
class NotificationScheduler(
    private val context: Context,
    private val prefs: NotificationPreferences,
) {
    fun ensureChannels() = NotificationChannels.ensureChannels(context)

    /** Cancels all 3 periodic works. */
    fun cancelAll() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_DAILY)
        wm.cancelUniqueWork(WORK_RISK)
        wm.cancelUniqueWork(WORK_DAY_BOUNDARY)
    }

    suspend fun reschedule() {
        // Filled in by Task 13.
    }

    companion object {
        const val WORK_DAILY = "phase4-daily-reminder"
        const val WORK_RISK = "phase4-streak-risk"
        const val WORK_DAY_BOUNDARY = "phase4-day-boundary"
    }
}
