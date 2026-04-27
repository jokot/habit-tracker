package com.jktdeveloper.habitto.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    const val DAILY_REMINDER = "daily_reminder"
    const val STREAK_RISK = "streak_risk"
    const val STREAK_STATUS = "streak_status"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(DAILY_REMINDER, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminds you each day to log your habits."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(STREAK_RISK, "Streak alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Late-day reminder when your streak is about to break."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(STREAK_STATUS, "Streak status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet updates when your streak is frozen or has reset."
            }
        )
    }
}
