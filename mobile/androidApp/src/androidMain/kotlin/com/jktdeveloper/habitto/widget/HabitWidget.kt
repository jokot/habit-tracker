package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.PointBalance
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.MainActivity
import com.jktdeveloper.habitto.ui.theme.DarkColorScheme
import com.jktdeveloper.habitto.ui.theme.LightColorScheme
import kotlin.math.nextUp

class HabitWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val userId = container.currentUserId()
        val habits = container.getTodayHabitsUseCase.execute(userId)
        val balance = container.getPointBalanceUseCase.execute(userId)
            .getOrDefault(PointBalance(0, 0, 0))

        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                WidgetContent(habits = habits, balance = balance.balance)
            }
        }
    }
}

@Composable
private fun WidgetContent(habits: List<HabitWithProgress>, balance: Int) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            "$balance pts",
            style = TextStyle(fontWeight = FontWeight.Bold),
        )
        if (habits.isEmpty()) {
            Text("No habits yet — open app")
        } else {
            LazyColumn {
                items(habits, itemId = { it.habit.id.hashCode().toLong() }) { hp ->
                    HabitRow(hp)
                }
            }
        }
    }
}

@Composable
private fun HabitRow(hp: HabitWithProgress) {
    val habit = hp.habit
    // ponytail: dailyTarget * thresholdPerPoint can double-round just under the true
    // value (e.g. 3 * 0.7 -> 2.0999999999999996), which then floors one point short in
    // PointCalculator.pointsEarned. nextUp() nudges by a single ULP to clear that noise
    // without perturbing the logged quantity — verified dailyTarget=3/threshold=0.7,
    // dailyTarget=7/threshold=1/3, dailyTarget=6/threshold=0.7 all recover exactly.
    val logQuantity = (habit.dailyTarget * habit.thresholdPerPoint).nextUp()
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(
                actionRunCallback<LogHabitAction>(
                    actionParametersOf(
                        LogHabitAction.habitIdKey to habit.id,
                        LogHabitAction.quantityKey to logQuantity,
                    )
                )
            ),
    ) {
        Text(habit.name, modifier = GlanceModifier.defaultWeight())
        Text(hp.progressText)
    }
}
