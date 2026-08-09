package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.material3.ColorProviders
import com.habittracker.domain.usecase.WidgetData
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.ui.theme.DarkColorScheme
import com.jktdeveloper.habitto.ui.theme.LightColorScheme

class QuickLogListWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(MIN_SIZE, EXPANDED_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val data: WidgetData = container.getWidgetDataUseCase.execute(
            userId = container.currentUserId(),
            habitSlots = 2,
            wantSlots = 3,
        )
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                val expanded = LocalSize.current.height > MIN_SIZE.height
                WidgetSurface {
                    if (data.items.habits.isEmpty() && data.items.wants.isEmpty()) {
                        WidgetEmpty("No habits yet — open app")
                    } else {
                        BalanceHeader(
                            balance = data.balance,
                            currentStreak = data.currentStreak,
                            compact = true,
                        )
                        LazyColumn {
                            items(
                                items = data.items.habits,
                                itemId = { it.habitId.hashCode().toLong() },
                            ) { HabitRow(it) }
                            if (expanded && data.items.wants.isNotEmpty()) {
                                item(itemId = DIVIDER_ITEM_ID) {
                                    Box(
                                        modifier = GlanceModifier.fillMaxWidth().height(9.dp),
                                    ) {}
                                }
                                items(
                                    items = data.items.wants,
                                    itemId = { it.activityId.hashCode().toLong() },
                                ) { WantRow(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val MIN_SIZE = DpSize(250.dp, 110.dp)
        val EXPANDED_SIZE = DpSize(250.dp, 320.dp)
        private const val DIVIDER_ITEM_ID = -1L
    }
}
