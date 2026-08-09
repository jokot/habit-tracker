package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import com.habittracker.domain.usecase.WidgetData
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.MainActivity
import com.jktdeveloper.habitto.ui.theme.DarkColorScheme
import com.jktdeveloper.habitto.ui.theme.LightColorScheme

private data class GridEntry(
    val label: String,
    val caption: String,
    val enabled: Boolean,
    val action: Action,
)

class QuickLogGridWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(MIN_SIZE, EXPANDED_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val data: WidgetData = container.getWidgetDataUseCase.execute(
            userId = container.currentUserId(),
            habitSlots = 3,
            wantSlots = 3,
        )
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                val expanded = LocalSize.current.height > MIN_SIZE.height
                val context = LocalContext.current
                val tiles = buildList {
                    data.items.habits.forEach { h ->
                        add(
                            GridEntry(
                                label = h.name,
                                caption = "+1",
                                enabled = true,
                                action = actionRunCallback<LogHabitAction>(
                                    actionParametersOf(
                                        LogHabitAction.habitIdKey to h.habitId,
                                        LogHabitAction.quantityKey to h.logQuantity,
                                    ),
                                ),
                            ),
                        )
                    }
                    data.items.wants.forEach { w ->
                        add(
                            GridEntry(
                                label = w.name,
                                caption = if (w.enabled) "−1 pt" else "no pts",
                                enabled = w.enabled,
                                action = when {
                                    !w.enabled -> actionStartActivity<MainActivity>()
                                    w.isTimed -> actionStartActivity(
                                        wantTimerIntent(context, w.activityId),
                                    )
                                    else -> actionRunCallback<LogWantAction>(
                                        actionParametersOf(
                                            LogWantAction.activityIdKey to w.activityId,
                                        ),
                                    )
                                },
                            ),
                        )
                    }
                }
                WidgetSurface {
                    if (tiles.isEmpty()) {
                        WidgetEmpty("No habits yet — open app")
                    } else {
                        if (expanded) {
                            BalanceHeader(
                                balance = data.balance,
                                currentStreak = data.currentStreak,
                                compact = true,
                            )
                        }
                        repeat(if (expanded) 2 else 1) { rowIndex ->
                            Row(modifier = GlanceModifier.fillMaxWidth()) {
                                repeat(COLUMNS) { col ->
                                    val entry = tiles.getOrNull(rowIndex * COLUMNS + col)
                                    if (entry == null) {
                                        Box(modifier = GlanceModifier.defaultWeight()) {}
                                    } else {
                                        GridTile(
                                            label = entry.label,
                                            caption = entry.caption,
                                            enabled = entry.enabled,
                                            action = entry.action,
                                            modifier = GlanceModifier
                                                .defaultWeight()
                                                .padding(2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val MIN_SIZE = DpSize(250.dp, 110.dp)
        val EXPANDED_SIZE = DpSize(250.dp, 250.dp)
        private const val COLUMNS = 3
    }
}
