package com.habittracker.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class PointCalculatorTest {

    @Test
    fun pointsEarned_exactThreshold_returnsOne() {
        assertEquals(1, PointCalculator.pointsEarned(quantity = 3.0, threshold = 3.0))
    }

    @Test
    fun pointsEarned_belowThreshold_returnsZero() {
        assertEquals(0, PointCalculator.pointsEarned(quantity = 2.0, threshold = 3.0))
    }

    @Test
    fun pointsEarned_multipleThresholds_returnsFloor() {
        assertEquals(2, PointCalculator.pointsEarned(quantity = 7.0, threshold = 3.0))
    }

    @Test
    fun pointsEarned_fractionalQuantity_truncates() {
        assertEquals(1, PointCalculator.pointsEarned(quantity = 5.9, threshold = 3.0))
    }

    @Test
    fun pointsSpent_exactUnit_returnsOne() {
        assertEquals(1, PointCalculator.pointsSpent(quantity = 10.0, costPerUnit = 0.1))
    }

    @Test
    fun pointsSpent_threeUnits_returnsThree() {
        assertEquals(3, PointCalculator.pointsSpent(quantity = 30.0, costPerUnit = 0.1))
    }

    @Test
    fun pointsSpent_partialUnit_roundsUpToOne() {
        // 5 min × 0.1 pt/min = 0.5 → ceil 1. Any positive consumption costs ≥ 1 pt.
        assertEquals(1, PointCalculator.pointsSpent(quantity = 5.0, costPerUnit = 0.1))
    }

    @Test
    fun pointsSpent_zeroQuantity_returnsZero() {
        assertEquals(0, PointCalculator.pointsSpent(quantity = 0.0, costPerUnit = 1.0))
    }

    @Test
    fun pointsSpent_fractional_roundsUp() {
        // 3 min × 0.5 pt/min = 1.5 → ceil 2.
        assertEquals(2, PointCalculator.pointsSpent(quantity = 3.0, costPerUnit = 0.5))
    }

    @Test
    fun pointsSpent_scrollOneMinute_returnsOne() {
        assertEquals(1, PointCalculator.pointsSpent(quantity = 1.0, costPerUnit = 1.0))
    }

    // ─── pointsSpentWithRate (Phase 6 exchange rate) ────────────────────────

    @Test
    fun `pointsSpentWithRate streak-0 rate-1 same as base`() {
        assertEquals(10, PointCalculator.pointsSpentWithRate(10.0, 1.0, 1.0))
    }

    @Test
    fun `pointsSpentWithRate rate 1_2 ceils up`() {
        assertEquals(12, PointCalculator.pointsSpentWithRate(10.0, 1.0, 1.2))
    }

    @Test
    fun `pointsSpentWithRate rate 1_4 ceils up`() {
        assertEquals(14, PointCalculator.pointsSpentWithRate(10.0, 1.0, 1.4))
    }

    @Test
    fun `pointsSpentWithRate fractional inputs ceil up to at least 1pt`() {
        assertEquals(1, PointCalculator.pointsSpentWithRate(0.1, 0.1, 1.0))
    }

    @Test
    fun `pointsSpentWithRate zero quantity returns 0`() {
        assertEquals(0, PointCalculator.pointsSpentWithRate(0.0, 5.0, 1.4))
    }

    @Test
    fun `pointsSpentWithRate zero cost returns 0`() {
        assertEquals(0, PointCalculator.pointsSpentWithRate(5.0, 0.0, 1.4))
    }
}
