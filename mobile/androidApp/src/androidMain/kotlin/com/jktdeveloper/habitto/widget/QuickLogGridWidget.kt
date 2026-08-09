package com.jktdeveloper.habitto.widget

import android.content.Context
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
import androidx.glance.layout.size
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

/**
 * Habits and wants as square tiles.
 *
 * Column count, row count and tile edge are all derived from the real widget size —
 * Glance has no `aspectRatio` and no flowing grid, so a square tile only happens if the
 * caller computes the edge itself. The gutter lives on a wrapper [Box] rather than on
 * the tile: Glance applies `padding` inside a view's background, so padding on the tile
 * shrinks its fill instead of separating it from its neighbour.
 */
class QuickLogGridWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val data: WidgetData = container.getWidgetDataUseCase.execute(
            userId = container.currentUserId(),
            habitSlots = Int.MAX_VALUE,
            wantSlots = Int.MAX_VALUE,
        )
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
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
                val size = LocalSize.current
                val innerWidth = size.width - SURFACE_PADDING * 2
                val innerHeight = size.height - SURFACE_PADDING * 2
                val showHeader = size.height >= 170.dp
                val gridHeight = if (showHeader) innerHeight - HEADER_HEIGHT else innerHeight
                val columns = (innerWidth / TARGET_TILE).toInt().coerceIn(2, 5)
                val slot = innerWidth / columns
                val rows = (gridHeight / slot).toInt().coerceAtLeast(1)
                val visible = tiles.take(columns * rows)

                WidgetSurface {
                    if (tiles.isEmpty()) {
                        WidgetEmpty("No habits yet — open app")
                    } else {
                        if (showHeader) {
                            BalanceHeader(
                                balance = data.balance,
                                currentStreak = data.currentStreak,
                                compact = true,
                            )
                        }
                        repeat(rows) { rowIndex ->
                            Row(modifier = GlanceModifier.fillMaxWidth()) {
                                repeat(columns) { col ->
                                    val entry = visible.getOrNull(rowIndex * columns + col)
                                    Box(
                                        modifier = GlanceModifier.size(slot).padding(GUTTER),
                                    ) {
                                        if (entry != null) {
                                            GridTile(
                                                label = entry.label,
                                                caption = entry.caption,
                                                enabled = entry.enabled,
                                                action = entry.action,
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
    }

    private companion object {
        /** Smallest tile edge worth rendering; column count is `innerWidth / this`. */
        val TARGET_TILE = 70.dp
        val GUTTER = 4.dp
        val HEADER_HEIGHT = 28.dp
    }
}
