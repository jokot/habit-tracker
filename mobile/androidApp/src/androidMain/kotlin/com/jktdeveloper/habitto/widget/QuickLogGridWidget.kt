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
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
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
    val id: Long,
    val icon: ImageVector,
    val hue: Float,
    val label: String,
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
                                id = h.habitId.hashCode().toLong(),
                                icon = habitIcon(h.name),
                                hue = HABIT_HUE,
                                label = h.name,
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
                                id = w.activityId.hashCode().toLong(),
                                icon = resolveWantIcon(w.iconKey, w.name),
                                hue = WANT_HUE,
                                label = w.name,
                                enabled = w.enabled,
                                action = when {
                                    !w.enabled -> actionStartActivity<MainActivity>()
                                    w.isTimed -> actionStartActivity(
                                        wantDetailTimerIntent(context, w.activityId),
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
                // Same balance type as the list widget, switching to the small line at the
                // same height, so the two read as one family on the same home screen.
                val compactHeader = size.height < COMPACT_HEADER_HEIGHT
                val headerHeight = if (compactHeader) HEADER_HEIGHT else HEADER_HEIGHT_LARGE
                val gridHeight = if (showHeader) innerHeight - headerHeight else innerHeight
                // EPSILON absorbs float division landing a hair under a whole cell — without it
                // a frame that fits exactly three rows can floor to two.
                val columns = (innerWidth / TARGET_TILE + EPSILON).toInt().coerceIn(2, 5)
                val cellWidth = innerWidth / columns
                val rows = (gridHeight / TARGET_TILE + EPSILON).toInt().coerceAtLeast(1)
                // Cells divide the frame and the tile is the largest square that fits one, so
                // both variants fill their frame edge to edge — with a header the rows simply
                // share what is left after the balance line.
                val cellHeight = gridHeight / rows
                val tile = minOf(cellWidth, cellHeight) - GUTTER * 2

                WidgetSurface {
                    if (tiles.isEmpty()) {
                        WidgetEmpty("No habits yet — open app")
                    } else {
                        if (showHeader) {
                            BalanceHeader(
                                balance = data.balance,
                                currentStreak = data.currentStreak,
                                compact = compactHeader,
                            )
                        }
                        // Every tile, scrollable: the frame decides how many are in view, not
                        // which ones exist. Glance caps a fixed grid at five columns, which is
                        // also where [columns] is coerced.
                        LazyVerticalGrid(
                            GridCells.Fixed(columns),
                            modifier = GlanceModifier.fillMaxSize(),
                        ) {
                            items(items = tiles, itemId = { it.id }) { entry ->
                                Box(
                                    modifier = GlanceModifier
                                        .width(cellWidth)
                                        .height(cellHeight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(modifier = GlanceModifier.size(tile)) {
                                        GridTile(
                                            icon = entry.icon,
                                            hue = entry.hue,
                                            label = entry.label,
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

    private companion object {
        /**
         * Smallest tile edge worth rendering. 52dp puts six tiles in a 2×3 frame
         * (≈110×180dp: two columns, three rows) while a 4×2 still lands on four columns.
         */
        val TARGET_TILE = 52.dp
        val GUTTER = 3.dp
        val HEADER_HEIGHT = 28.dp

        /** What the balance line costs once it is drawing the list widget's larger numeral. */
        val HEADER_HEIGHT_LARGE = 52.dp

        /** Below this the balance drops to the small line — the list widget's threshold. */
        val COMPACT_HEADER_HEIGHT = 200.dp

        /**
         * Above this the balance line earns its space — two cells tall and up, matching the
         * list widget. Only a one-cell-tall frame skips it, where the header would leave no
         * room for a row of tiles.
         */
        val HEADER_MIN_HEIGHT = 180.dp
        const val EPSILON = 0.02f
    }
}

/** Same grid, no balance header — the "pure items" variant. */
class QuickLogGridPlainWidget : QuickLogGridWidget(withHeader = false)
