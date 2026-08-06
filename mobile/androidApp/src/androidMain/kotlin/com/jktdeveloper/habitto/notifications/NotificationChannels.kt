package com.jktdeveloper.habitto.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    // Grouped channels per canvas v5
    const val REMINDER = "reminder"
    const val ALERT = "alert"
    const val STATUS = "status"
    const val SYSTEM = "system"

    // Timer-specific channels (kept separate so users can mute the live countdown
    // without losing the completion alert).
    const val WANT_TIMER_RUNNING = "want_timer_running"
    const val WANT_TIMER_END = "want_timer_end"

    // Legacy Phase 4 ids — aliases so still-in-flight code compiles during rebind.
    @Deprecated("Use REMINDER", ReplaceWith("REMINDER"))
    const val DAILY_REMINDER = REMINDER
    @Deprecated("Use ALERT", ReplaceWith("ALERT"))
    const val STREAK_RISK = ALERT
    @Deprecated("Use STATUS", ReplaceWith("STATUS"))
    const val STREAK_STATUS = STATUS

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService<NotificationManager>() ?: return

        mgr.createNotificationChannel(
            NotificationChannel(REMINDER, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Gentle daily nudges to log habits."
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(ALERT, "Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Time-sensitive alerts: streak risk, want-timer end."
                enableVibration(true)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(STATUS, "Status updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Streak frozen/reset, tier advances, milestones."
                setSound(null, null)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(SYSTEM, "System", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Session expired, cloud restore complete, sync failures."
                setSound(null, null)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(WANT_TIMER_RUNNING, "Want timer (running)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live countdown while a want timer is running."
                setShowBadge(false)
                setSound(null, null)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(WANT_TIMER_END, "Want timer (end)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alert when a want timer finishes."
                enableVibration(true)
            }
        )
    }
}
