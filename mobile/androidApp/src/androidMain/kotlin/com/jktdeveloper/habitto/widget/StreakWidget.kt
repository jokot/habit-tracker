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
import androidx.glance.layout.Column
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
 * The cell edge is fixed at [SLOT]; the real widget size decides how many columns and
 * rows fit, and the grid is centred in whatever is left over. A bigger frame therefore
 * shows more days at the same size rather than the same days blown up or shrunk. The
 * history it draws from is a ceiling a very large widget could ask for; smaller frames
 * render its tail.
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
                // The cell is a fixed edge, so a day reads the same on a 2×2 as on a 4×2 and
                // only the number of them changes with the frame. EPSILON absorbs float
                // division landing a hair under a whole cell and flooring a column away.
                val slot = SLOT
                val columns = (innerWidth / slot + EPSILON).toInt().coerceAtLeast(3)
                val maxRows = (gridHeight / slot + EPSILON).toInt().coerceAtLeast(1)
                val rows = minOf(maxRows, days.size / columns).coerceAtLeast(1)
                val visible = days.takeLast(columns * rows)

                WidgetSurface(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        // Rows wrap their cells instead of filling the width, so the
                        // surface's centre alignment lands the block in the middle of the
                        // frame on both axes, whatever size the launcher hands us.
                        //
                        // The nesting is not decoration: a Glance container renders at most
                        // MAX_CHILDREN children, silently dropping the rest, which is what
                        // pinned every size to a 10×10 grid however much room it had.
                        visible.chunked(columns).chunked(MAX_CHILDREN).forEach { rowGroup ->
                            Column {
                                rowGroup.forEach { week ->
                                    Row {
                                        week.chunked(MAX_CHILDREN).forEach { cellGroup ->
                                            Row {
                                                cellGroup.forEach { day ->
                                                    // The gutter is the outer Box's padding —
                                                    // padding on the coloured Box would shrink
                                                    // its fill, not separate cells.
                                                    Box(
                                                        modifier = GlanceModifier
                                                            .size(slot)
                                                            .padding(GAP),
                                                    ) {
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
                }
            }
        }
    }

    private companion object {
        /**
         * Cell edge including its gutter, the same at every widget size — a bigger frame
         * buys more days, not bigger days. [GAP] comes off it, so the coloured square is
         * 24dp.
         */
        val SLOT = 28.dp

        /**
         * How many children a Glance container actually renders. Anything past this is
         * dropped without a warning, so rows and cells are nested in groups of it.
         */
        const val MAX_CHILDREN = 10
        val GAP = 2.dp
        val HEADER_HEIGHT = 30.dp
        const val EPSILON = 0.02f
    }
}

/** Same heat grid, no streak line — the "pure items" variant. */
class StreakPlainWidget : StreakWidget(withHeader = false)
