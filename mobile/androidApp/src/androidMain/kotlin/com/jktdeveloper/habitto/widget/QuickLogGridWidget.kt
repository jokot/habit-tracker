package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import com.habittracker.domain.usecase.WidgetData
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.MainActivity
import com.jktdeveloper.habitto.ui.components.habitIcon
import com.jktdeveloper.habitto.ui.components.resolveWantIcon
import com.jktdeveloper.habitto.ui.theme.DarkColorScheme
import com.jktdeveloper.habitto.ui.theme.LightColorScheme

private class GridEntry(
    val icon: ImageVector,
    val hue: Float,
    val label: String,
    val caption: String,
    val enabled: Boolean,
    val action: Action,
)

/**
 * Habits and wants as square icon tiles.
 *
 * Column count, row count and tile edge are all derived from the real widget size —
 * Glance has no `aspectRatio` and no flowing grid, so a square tile only happens if the
 * caller computes the edge itself. The gutter lives on a wrapper [Box] rather than on
 * the tile: Glance applies `padding` inside a view's background, so padding on the tile
 * shrinks its fill instead of separating it from its neighbour.
 *
 * [withHeader] false is the "pure items" variant — same grid, no balance line.
 */
open class QuickLogGridWidget(private val withHeader: Boolean = true) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val initial = awaitWidgetData(container)
        provideContent {
            val data: WidgetData = liveWidgetData(container, initial)
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                val context = LocalContext.current
                val tiles = buildList {
                    data.items.habits.forEach { h ->
                        add(
                            GridEntry(
                                icon = habitIcon(h.name),
                                hue = HABIT_HUE,
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
                                icon = resolveWantIcon(w.iconKey, w.name),
                                hue = WANT_HUE,
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
                val showHeader = withHeader && size.height >= HEADER_MIN_HEIGHT
                val gridHeight = if (showHeader) innerHeight - HEADER_HEIGHT else innerHeight
                // EPSILON absorbs float division landing a hair under a whole cell — without it
                // a frame that fits exactly three rows can floor to two.
                val columns = (innerWidth / TARGET_TILE + EPSILON).toInt().coerceIn(2, 5)
                val rows = (gridHeight / TARGET_TILE + EPSILON).toInt().coerceAtLeast(1)
                // Cells divide the frame; the tile is the largest square that fits one, so a
                // wide-and-short frame yields squares rather than stretched rectangles.
                val cellWidth = innerWidth / columns
                val cellHeight = gridHeight / rows
                val tile = minOf(cellWidth, cellHeight) - GUTTER * 2
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
                                        modifier = GlanceModifier
                                            .width(cellWidth)
                                            .height(cellHeight),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (entry != null) {
                                            Box(modifier = GlanceModifier.size(tile)) {
                                                GridTile(
                                                    icon = entry.icon,
                                                    hue = entry.hue,
                                                    label = entry.label,
                                                    caption = entry.caption,
                                                    enabled = entry.enabled,
                                                    action = entry.action,
                                                    tileSize = tile,
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
    }

    private companion object {
        /**
         * Smallest tile edge worth rendering. 52dp puts six tiles in a 2×3 frame
         * (≈110×180dp: two columns, three rows) while a 4×2 still lands on four columns.
         */
        val TARGET_TILE = 52.dp
        val GUTTER = 3.dp
        val HEADER_HEIGHT = 28.dp

        /** Above this the balance line earns its space; a 2×3 frame is shorter and skips it. */
        val HEADER_MIN_HEIGHT = 220.dp
        const val EPSILON = 0.02f
    }
}

/** Same grid, no balance header — the "pure items" variant. */
class QuickLogGridPlainWidget : QuickLogGridWidget(withHeader = false)
