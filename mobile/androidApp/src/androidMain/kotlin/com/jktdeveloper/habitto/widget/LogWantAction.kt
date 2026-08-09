package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.habittracker.domain.model.DeviceMode
import com.jktdeveloper.habitto.HabitTrackerApplication

/**
 * Logs one tap on a non-timed want. Minute-unit wants never reach this action — they
 * deep-link the want-timer screen instead (see [wantTimerIntent]).
 */
class LogWantAction : ActionCallback {
    companion object {
        val activityIdKey = ActionParameters.Key<String>("activityId")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val activityId = parameters[activityIdKey] ?: return
        val container = (context.applicationContext as HabitTrackerApplication).container

        // System-triggered callback with no in-widget error UI — swallow failures,
        // the next periodic refresh reconciles state (spec: Error handling).
        // LogWantUseCase.execute already refuses unaffordable spend via Result, so a
        // stale widget tapped at zero balance fails safely on its own.
        // One local write and nothing else — see [LogHabitAction] for why updating the
        // widgets does not belong on this path.
        runCatching {
            container.logWantUseCase.execute(
                userId = container.currentUserId(),
                activityId = activityId,
                taps = 1,
                deviceMode = DeviceMode.OTHER,
            )
        }
    }
}
