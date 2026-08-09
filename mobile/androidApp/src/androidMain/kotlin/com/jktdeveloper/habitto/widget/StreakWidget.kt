package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.MainActivity
import com.jktdeveloper.habitto.ui.theme.DarkColorScheme
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.FlameOrangeDark
import com.jktdeveloper.habitto.ui.theme.LightColorScheme

/**
 * Streak count plus a heat grid of recent days.
 *
 * Cell edge, column count and row count are all computed from the real widget size so
 * the grid spans the frame instead of sitting in a corner of it. The history it draws
 * from is a ceiling a very large widget could ask for; smaller frames render its tail.
 *
 * [withHeader] false is the "pure items" variant — the heat grid alone, no streak line.
 */
open class StreakWidget(private val withHeader: Boolean = true) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val initial = awaitWidgetData(container)

        provideContent {
            val data = liveWidgetData(container, initial)
            // The history comes with the rest of the widget data so it is computed once
            // for every widget rather than once per streak widget, and updates live.
            val days = data.streakDays
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                val size = LocalSize.current
                val innerWidth = size.width - SURFACE_PADDING * 2
                val innerHeight = size.height - SURFACE_PADDING * 2
                val showHeader = withHeader && size.height >= 140.dp
                val gridHeight = if (showHeader) innerHeight - HEADER_HEIGHT else innerHeight
                // EPSILON absorbs float division landing a hair under a whole cell — at 2×2
                // the row count divides out to exactly 5, and without it can floor to 4.
                val columns = (innerWidth / TARGET_CELL + EPSILON).toInt().coerceIn(5, 20)
                val slot = innerWidth / columns
                val maxRows = (gridHeight / slot + EPSILON).toInt().coerceAtLeast(1)
                val rows = minOf(maxRows, days.size / columns).coerceAtLeast(1)
                val visible = days.takeLast(columns * rows)

                WidgetSurface {
                    if (visible.isEmpty()) {
                        WidgetEmpty("Start a streak")
                    } else {
                        if (showHeader) {
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .clickable(actionStartActivity<MainActivity>()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "🔥 ${data.currentStreak}",
                                    style = TextStyle(
                                        color = ColorProvider(
                                            day = FlameOrange,
                                            night = FlameOrangeDark,
                                        ),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Text(
                                    " day streak",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    ),
                                )
                            }
                        }
                        visible.chunked(columns).forEach { week ->
                            Row(modifier = GlanceModifier.fillMaxWidth()) {
                                week.forEach { day ->
                                    // The gutter is the outer Box's padding — padding on the
                                    // coloured Box would shrink its fill, not separate cells.
                                    Box(modifier = GlanceModifier.size(slot).padding(GAP)) {
                                        Box(
                                            modifier = GlanceModifier
                                                .fillMaxSize()
                                                .background(heatColor(day))
                                                .cornerRadius(4.dp),
                                        ) {}
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
        /** Preferred cell edge; five columns is the floor, which is what a 2×2 lands on. */
        val TARGET_CELL = 28.dp
        val GAP = 2.dp
        val HEADER_HEIGHT = 30.dp
        const val EPSILON = 0.02f
    }
}

/** Same heat grid, no streak line — the "pure items" variant. */
class StreakPlainWidget : StreakWidget(withHeader = false)
