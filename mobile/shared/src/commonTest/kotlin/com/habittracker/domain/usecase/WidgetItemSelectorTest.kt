package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.WantActivity
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun habit(
    id: String,
    name: String,
    thresholdPerPoint: Double = 1.0,
    dailyTarget: Int = 3,
    pointsToday: Int = 0,
) = HabitWithProgress(
    habit = Habit(
        id = id,
        userId = "u1",
        templateId = null,
        name = name,
        unit = "rep",
        thresholdPerPoint = thresholdPerPoint,
        dailyTarget = dailyTarget,
        createdAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
        syncedAt = null,
        effectiveFrom = Instant.fromEpochSeconds(0),
        effectiveTo = null,
    ),
    pointsToday = pointsToday,
)

private fun want(
    id: String,
    name: String,
    unit: String = "min",
    unitsPerPoint: Int = 10,
    hidden: Boolean = false,
) = WantActivity(
    id = id,
    name = name,
    unit = unit,
    unitsPerPoint = unitsPerPoint,
    isCustom = false,
    createdByUserId = null,
    iconKey = null,
    hiddenAt = if (hidden) Instant.fromEpochSeconds(1) else null,
    updatedAt = Instant.fromEpochSeconds(0),
    syncedAt = null,
)

class WidgetItemSelectorTest {

    @Test
    fun `zero balance disables every want`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "YouTube"), want("w2", "Soda", unit = "cup")),
            balance = 0,
            rate = 1.0,
            habitSlots = 2,
            wantSlots = 3,
        )
        assertEquals(2, result.wants.size)
        assertTrue(result.wants.none { it.enabled })
    }

    @Test
    fun `negative balance disables every want`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "YouTube")),
            balance = -4,
            rate = 1.0,
            habitSlots = 2,
            wantSlots = 3,
        )
        assertFalse(result.wants.single().enabled)
    }

    @Test
    fun `positive balance disables no want`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "YouTube"), want("w2", "Soda", unit = "cup")),
            balance = 1,
            rate = 1.0,
            habitSlots = 2,
            wantSlots = 3,
        )
        assertTrue(result.wants.all { it.enabled })
    }

    @Test
    fun `truncates to slot counts`() {
        val result = WidgetItemSelector.select(
            habits = listOf(habit("h1", "Run"), habit("h2", "Read"), habit("h3", "Meditate")),
            wants = listOf(want("w1", "A"), want("w2", "B"), want("w3", "C"), want("w4", "D")),
            balance = 10,
            rate = 1.0,
            habitSlots = 2,
            wantSlots = 3,
        )
        assertEquals(listOf("h1", "h2"), result.habits.map { it.habitId })
        assertEquals(listOf("w1", "w2", "w3"), result.wants.map { it.activityId })
    }

    @Test
    fun `fewer items than slots returns all of them`() {
        val result = WidgetItemSelector.select(
            habits = listOf(habit("h1", "Run")),
            wants = listOf(want("w1", "A")),
            balance = 10,
            rate = 1.0,
            habitSlots = 3,
            wantSlots = 3,
        )
        assertEquals(1, result.habits.size)
        assertEquals(1, result.wants.size)
    }

    @Test
    fun `hidden wants are excluded before truncation`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "A", hidden = true), want("w2", "B"), want("w3", "C")),
            balance = 10,
            rate = 1.0,
            habitSlots = 0,
            wantSlots = 2,
        )
        assertEquals(listOf("w2", "w3"), result.wants.map { it.activityId })
    }

    @Test
    fun `logQuantity is exactly one point worth of the habit unit`() {
        val result = WidgetItemSelector.select(
            habits = listOf(habit("h1", "Read", thresholdPerPoint = 0.7, dailyTarget = 3)),
            wants = emptyList(),
            balance = 0,
            rate = 1.0,
            habitSlots = 1,
            wantSlots = 0,
        )
        val logged = result.habits.single().logQuantity
        assertEquals(1, PointCalculator.pointsEarned(logged, 0.7))
    }

    @Test
    fun `logQuantity survives a repeating threshold`() {
        val third = 1.0 / 3.0
        val result = WidgetItemSelector.select(
            habits = listOf(habit("h1", "Water", thresholdPerPoint = third, dailyTarget = 3)),
            wants = emptyList(),
            balance = 0,
            rate = 1.0,
            habitSlots = 1,
            wantSlots = 0,
        )
        assertEquals(1, PointCalculator.pointsEarned(result.habits.single().logQuantity, third))
    }

    @Test
    fun `progressText comes from HabitWithProgress`() {
        val result = WidgetItemSelector.select(
            habits = listOf(habit("h1", "Run", dailyTarget = 3, pointsToday = 2)),
            wants = emptyList(),
            balance = 0,
            rate = 1.0,
            habitSlots = 1,
            wantSlots = 0,
        )
        assertEquals("2 / 3", result.habits.single().progressText)
        assertFalse(result.habits.single().isGoalMet)
    }

    @Test
    fun `rateText scales units per point by the exchange rate`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "YouTube", unit = "min", unitsPerPoint = 10)),
            balance = 5,
            rate = 2.0,
            habitSlots = 0,
            wantSlots = 1,
        )
        // effectiveUnitsPerPoint(10, 2.0) == 5
        assertEquals("5 min", result.wants.single().rateText)
    }

    @Test
    fun `isTimed is true only for the min unit`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "YouTube", unit = "min"), want("w2", "Soda", unit = "cup")),
            balance = 5,
            rate = 1.0,
            habitSlots = 0,
            wantSlots = 2,
        )
        assertTrue(result.wants[0].isTimed)
        assertFalse(result.wants[1].isTimed)
    }
}
