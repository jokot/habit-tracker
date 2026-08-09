package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * Single fan-out point for widget refreshes. Every source that used to poke the old
 * single-widget provider directly — action callbacks, the WidgetRefresher Flow
 * collector, the want-timer minute tick — calls this instead of updating the four
 * widgets itself.
 */
object WidgetUpdates {
    suspend fun updateAll(context: Context) {
        BalanceWidget().updateAll(context)
        QuickLogListWidget().updateAll(context)
        QuickLogGridWidget().updateAll(context)
        StreakWidget().updateAll(context)
    }
}
