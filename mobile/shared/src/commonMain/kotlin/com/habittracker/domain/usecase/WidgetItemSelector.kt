package com.habittracker.domain.usecase

import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.WantActivity
import kotlin.math.nextUp

/** One habit as a widget renders it. */
data class WidgetHabitItem(
    val habitId: String,
    val name: String,
    val progressText: String,
    val isGoalMet: Boolean,
    val logQuantity: Double,
)

/** One want as a widget renders it. */
data class WidgetWantItem(
    val activityId: String,
    val name: String,
    val rateText: String,
    val isTimed: Boolean,
    val enabled: Boolean,
    /** Carried through so a widget can draw the same glyph the want lists draw. */
    val iconKey: String? = null,
)

data class WidgetItems(
    val habits: List<WidgetHabitItem>,
    val wants: List<WidgetWantItem>,
)

data class WidgetData(
    val balance: Int,
    val currentStreak: Int,
    val items: WidgetItems,
    /**
     * Recent days for the streak heat grid. Assembled here rather than fetched by the
     * streak widget so all widgets share one computation of it.
     */
    val streakDays: List<StreakDay> = emptyList(),
)

/**
 * Turns domain models into the rows a widget can draw, applying the two rules that
 * are easy to get wrong: how many items fit, and which wants are affordable.
 *
 * Affordability is uniform. PointCalculator.pointsSpent(taps) = taps, so every want
 * costs exactly one point per tap regardless of unitsPerPoint — which sets how many
 * units a point buys, not what it costs. This mirrors the gate WantTimerController
 * already enforces: `if (balance <= 0) throw InsufficientPointsException`.
 */
object WidgetItemSelector {

    /** Wants measured in this unit run on a timer instead of logging instantly. */
    const val MINUTE_UNIT: String = "min"

    fun select(
        habits: List<HabitWithProgress>,
        wants: List<WantActivity>,
        balance: Int,
        rate: Double,
        habitSlots: Int,
        wantSlots: Int,
    ): WidgetItems {
        val habitItems = habits
            .take(habitSlots.coerceAtLeast(0))
            .map { hp ->
                WidgetHabitItem(
                    habitId = hp.habit.id,
                    name = hp.habit.name,
                    progressText = hp.progressText,
                    isGoalMet = hp.isGoalMet,
                    // ponytail: one tap = one point. thresholdPerPoint can land a hair under
                    // the true value once it round-trips through a Double (e.g. 1/3 stored as
                    // 0.3333333333333333), which then floors to zero points in
                    // PointCalculator.pointsEarned. nextUp() nudges by a single ULP to clear
                    // that noise without perturbing the logged quantity. Carried over from the
                    // v1 HabitWidget fix, which applied it to dailyTarget * thresholdPerPoint.
                    logQuantity = hp.habit.thresholdPerPoint.nextUp(),
                )
            }

        val affordable = balance > 0
        val wantItems = wants
            .filter { it.hiddenAt == null }
            .take(wantSlots.coerceAtLeast(0))
            .map { want ->
                val units = PointCalculator.effectiveUnitsPerPoint(want.unitsPerPoint, rate)
                WidgetWantItem(
                    activityId = want.id,
                    name = want.name,
                    rateText = "$units ${want.unit}",
                    isTimed = want.unit == MINUTE_UNIT,
                    enabled = affordable,
                    iconKey = want.iconKey,
                )
            }

        return WidgetItems(habits = habitItems, wants = wantItems)
    }
}
