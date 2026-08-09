package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.jktdeveloper.habitto.HabitTrackerApplication

class LogHabitAction : ActionCallback {
    companion object {
        val habitIdKey = ActionParameters.Key<String>("habitId")
        val quantityKey = ActionParameters.Key<Double>("quantity")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[habitIdKey] ?: return
        val quantity = parameters[quantityKey] ?: return
        val container = (context.applicationContext as HabitTrackerApplication).container

        // System-triggered callback with no in-widget error UI — swallow failures,
        // the next periodic refresh reconciles state (spec: Error handling).
        // One local write and nothing else. Android delivers broadcasts to a receiver
        // serially, so anything done here delays the *next* tap — updating all seven widgets
        // from here made every tap after the first wait behind seven re-renders. Widgets
        // collect the data flow this write feeds, so they repaint themselves.
        runCatching {
            container.logHabitUseCase.execute(container.currentUserId(), habitId, quantity)
        }
    }
}
