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
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.habittracker.domain.usecase.WidgetData
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.MainActivity
import com.jktdeveloper.habitto.ui.theme.DarkColorScheme
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.FlameOrangeDark
import com.jktdeveloper.habitto.ui.theme.LightColorScheme

/**
 * The balance numeral, and nothing else. Sized from 1×1 up.
 *
 * [SizeMode.Exact] rather than Responsive: with a fixed set of declared sizes, Glance
 * hands the composition the nearest *declared* size, so a widget the user stretched to
 * 4×4 still lays out for the 2×2 bucket and leaves the rest of the frame empty. Exact
 * reports the real size, which is what the type scale below reads.
 */
class BalanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val data: WidgetData = container.getWidgetDataUseCase.execute(
            userId = container.currentUserId(),
            habitSlots = 0,
            wantSlots = 0,
        )
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                val size = LocalSize.current
                // The numeral is bounded by the shorter edge — a 4×1 strip has as little
                // room for a 72sp glyph as a 1×1 cell does.
                val shortEdge = minOf(size.width, size.height)
                val numeralSize = when {
                    shortEdge < 70.dp -> 26.sp
                    shortEdge < 110.dp -> 40.sp
                    shortEdge < 170.dp -> 56.sp
                    else -> 72.sp
                }
                val labelSize = (numeralSize.value / 2.8f).coerceIn(11f, 24f).sp
                WidgetSurface(
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            data.balance.toString(),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = numeralSize,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        if (shortEdge >= 60.dp) {
                            Text(
                                " pts",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = labelSize,
                                ),
                            )
                        }
                    }
                    if (data.currentStreak > 0 && shortEdge >= 90.dp) {
                        Text(
                            "🔥 ${data.currentStreak}",
                            style = TextStyle(
                                color = ColorProvider(day = FlameOrange, night = FlameOrangeDark),
                                fontSize = labelSize,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }
}
