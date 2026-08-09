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
    /**
     * Each widget is guarded on its own. Unguarded and sequential, a throw from the first
     * pinned widget's render — a DB read inside `provideGlance`, a launcher that has gone
     * away — silently skips every widget after it, which reads on the home screen as "only
     * some of my widgets update".
     */
    suspend fun updateAll(context: Context) {
        runCatching { BalanceWidget().updateAll(context) }
        runCatching { QuickLogListWidget().updateAll(context) }
        runCatching { QuickLogGridWidget().updateAll(context) }
        runCatching { StreakWidget().updateAll(context) }
    }
}
