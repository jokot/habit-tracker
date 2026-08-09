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
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
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

class BalanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(MIN_SIZE, EXPANDED_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val data: WidgetData = container.getWidgetDataUseCase.execute(
            userId = container.currentUserId(),
            habitSlots = 0,
            wantSlots = 0,
        )
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                val compactWidth = LocalSize.current.width <= MIN_SIZE.width
                WidgetSurface(
                    // BalanceHeader (expanded branch) already carries its own clickable Row;
                    // the compact branch is bare Text, so the surface itself is the tap target.
                    modifier = if (compactWidth) {
                        GlanceModifier.clickable(actionStartActivity<MainActivity>())
                    } else {
                        GlanceModifier
                    },
                ) {
                    if (compactWidth) {
                        // 110dp cannot hold numeral, unit and flame on one line — stack them.
                        Text(
                            data.balance.toString(),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            "pts",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 13.sp,
                            ),
                        )
                        if (data.currentStreak > 0) {
                            Text(
                                "🔥 ${data.currentStreak}",
                                style = TextStyle(
                                    color = ColorProvider(day = FlameOrange, night = FlameOrangeDark),
                                    fontSize = 13.sp,
                                ),
                            )
                        }
                    } else {
                        BalanceHeader(balance = data.balance, currentStreak = data.currentStreak)
                    }
                }
            }
        }
    }

    companion object {
        val MIN_SIZE = DpSize(110.dp, 110.dp)
        val EXPANDED_SIZE = DpSize(250.dp, 110.dp)
    }
}
