package com.jktdeveloper.habitto.widget

import android.content.Context
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.material3.ColorProviders
import com.habittracker.domain.usecase.WidgetData
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.ui.theme.DarkColorScheme
import com.jktdeveloper.habitto.ui.theme.LightColorScheme

/**
 * Every habit and every want, scrollable.
 *
 * The list is not truncated to a slot count. A fixed count can only ever be right for
 * one widget size, and the size is not knowable in [provideGlance] — [LocalSize] exists
 * only inside the composition. A scrolling list sidesteps the question: it shows as much
 * as the frame holds and the rest is a swipe away.
 *
 * [SizeMode.Exact] for the same reason [BalanceWidget] uses it — Responsive lays out for
 * the nearest declared size, not the real one.
 */
class QuickLogListWidget : GlanceAppWidget() {

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
                val compactHeader = LocalSize.current.height < 200.dp
                WidgetSurface {
                    if (data.items.habits.isEmpty() && data.items.wants.isEmpty()) {
                        WidgetEmpty("No habits yet — open app")
                    } else {
                        BalanceHeader(
                            balance = data.balance,
                            currentStreak = data.currentStreak,
                            compact = compactHeader,
                        )
                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            items(
                                items = data.items.habits,
                                itemId = { it.habitId.hashCode().toLong() },
                            ) { HabitRow(it) }
                            if (data.items.habits.isNotEmpty() && data.items.wants.isNotEmpty()) {
                                item(itemId = DIVIDER_ITEM_ID) {
                                    Box(
                                        modifier = GlanceModifier.fillMaxWidth().height(9.dp),
                                    ) {}
                                }
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

    private companion object {
        const val DIVIDER_ITEM_ID = -1L
    }
}
