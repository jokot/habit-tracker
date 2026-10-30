package com.jktdeveloper.habitto.timer

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.habittracker.data.sync.SyncReason
import com.habittracker.domain.usecase.WantTimerCharge
import com.jktdeveloper.habitto.R
import com.jktdeveloper.habitto.notifications.NotificationChannels
import com.jktdeveloper.habitto.notifications.NotificationPreferences
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import com.jktdeveloper.habitto.notifications.PermissionUtils
import com.jktdeveloper.habitto.sync.SyncTriggers

/**
 * What the service and [WantTimerRecovery] do when a charge stops a timer: push the new
 * want logs, and tell the user why the timer stopped.
 */
internal suspend fun onTimerStopped(
    context: Context,
    notificationPreferences: NotificationPreferences,
    activityName: String,
    charge: WantTimerCharge,
    contentIntent: PendingIntent? = null,
) {
    if (charge.timer.pointsSpent > 0) {
        runCatching { SyncTriggers.enqueue(context, SyncReason.POST_LOG) }
    }

    val prefs = notificationPreferences.current()
    val canFire = prefs.masterEnabled &&
        prefs.isEnabled(NotificationTypeId.WANT_TIMER_END) &&
        PermissionUtils.hasNotificationPermission(context)
    if (!canFire) return

    val builder = NotificationCompat.Builder(context, NotificationChannels.WANT_TIMER_END)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Habitto")
        .setContentText(timerEndText(activityName, charge))
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
    if (contentIntent != null) builder.setContentIntent(contentIntent)
    NotificationManagerCompat.from(context).notify(WantTimerService.NOTIF_END_ID, builder.build())
}

internal fun timerEndText(activityName: String, charge: WantTimerCharge): String {
    val spent = charge.timer.pointsSpent
    val segment = if (spent > 0) " · ${charge.minutesLogged} min logged · −$spent pt" else ""
    return if (charge.outOfPoints) {
        "$activityName timer stopped: no points left$segment"
    } else {
        "$activityName timer finished$segment"
    }
}
