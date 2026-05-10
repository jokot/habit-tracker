package com.habittracker.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class PointCalculatorTest {
    // Habit side — unchanged.
    @Test fun `pointsEarned floors quantity by threshold`() {
        assertEquals(0, PointCalculator.pointsEarned(2.0, 3.0))
        assertEquals(1, PointCalculator.pointsEarned(3.0, 3.0))
        assertEquals(2, PointCalculator.pointsEarned(7.0, 3.0))
    }

    // Want side — taps-based, always 1 pt per tap.
    @Test fun `pointsSpent is taps`() {
        assertEquals(0, PointCalculator.pointsSpent(0))
        assertEquals(1, PointCalculator.pointsSpent(1))
        assertEquals(7, PointCalculator.pointsSpent(7))
    }

    @Test fun `effectiveUnitsPerPoint clamps to 1`() {
        assertEquals(1, PointCalculator.effectiveUnitsPerPoint(1, 1.0))
        assertEquals(1, PointCalculator.effectiveUnitsPerPoint(1, 2.0))
        assertEquals(1, PointCalculator.effectiveUnitsPerPoint(0, 1.0))
    }

    @Test fun `effectiveUnitsPerPoint floors by rate`() {
        assertEquals(10, PointCalculator.effectiveUnitsPerPoint(10, 1.0))
        assertEquals(8,  PointCalculator.effectiveUnitsPerPoint(10, 1.2))
        assertEquals(7,  PointCalculator.effectiveUnitsPerPoint(10, 1.4))
        assertEquals(6,  PointCalculator.effectiveUnitsPerPoint(10, 1.6))
        assertEquals(5,  PointCalculator.effectiveUnitsPerPoint(10, 2.0))
    }
}
