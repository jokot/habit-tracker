package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.habittracker.domain.model.DateRange
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.MainActivity
import com.jktdeveloper.habitto.ui.theme.DarkColorScheme
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.FlameOrangeDark
import com.jktdeveloper.habitto.ui.theme.LightColorScheme
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class StreakWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(MIN_SIZE, EXPANDED_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val userId = container.currentUserId()
        val data = container.getWidgetDataUseCase.execute(userId, habitSlots = 0, wantSlots = 0)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val range = DateRange(
            start = today.minus(EXPANDED_DAYS - 1, DateTimeUnit.DAY),
            endExclusive = today.plus(1, DateTimeUnit.DAY),
        )
        val days = runCatching { container.computeStreakUseCase.computeNow(userId, range).days }
            .getOrDefault(emptyList())

        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                val expanded = LocalSize.current.height > MIN_SIZE.height
                val columns = if (expanded) EXPANDED_COLUMNS else MIN_COLUMNS
                val visible = days.takeLast(if (expanded) EXPANDED_DAYS else MIN_DAYS)
                WidgetSurface {
                    if (visible.isEmpty()) {
                        WidgetEmpty("Start a streak")
                    } else {
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .clickable(actionStartActivity<MainActivity>()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "🔥 ${data.currentStreak}",
                                style = TextStyle(
                                    color = ColorProvider(day = FlameOrange, night = FlameOrangeDark),
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
                        visible.chunked(columns).forEach { week ->
                            Row(modifier = GlanceModifier.padding(top = 2.dp)) {
                                week.forEach { day ->
                                    Box(
                                        modifier = GlanceModifier
                                            .size(CELL_SIZE)
                                            .padding(1.dp)
                                            .background(heatColor(day))
                                            .cornerRadius(2.dp),
                                    ) {}
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
        val EXPANDED_SIZE = DpSize(250.dp, 180.dp)
        private const val MIN_COLUMNS = 12
        private const val MIN_DAYS = 36
        private const val EXPANDED_COLUMNS = 15
        private const val EXPANDED_DAYS = 60
        private val CELL_SIZE = 14.dp
    }
}
