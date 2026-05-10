package com.habittracker.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExchangeRateCalculatorTest {
    @Test fun `rateFor 0 returns 1_0`() = assertEquals(1.0, ExchangeRateCalculator.rateFor(0))
    @Test fun `rateFor 6 returns 1_0`() = assertEquals(1.0, ExchangeRateCalculator.rateFor(6))
    @Test fun `rateFor 7 returns 1_2`() = assertEquals(1.2, ExchangeRateCalculator.rateFor(7))
    @Test fun `rateFor 13 returns 1_2`() = assertEquals(1.2, ExchangeRateCalculator.rateFor(13))
    @Test fun `rateFor 14 returns 1_4`() = assertEquals(1.4, ExchangeRateCalculator.rateFor(14))
    @Test fun `rateFor 20 returns 1_4`() = assertEquals(1.4, ExchangeRateCalculator.rateFor(20))
    @Test fun `rateFor 21 returns 1_6`() = assertEquals(1.6, ExchangeRateCalculator.rateFor(21))
    @Test fun `rateFor 29 returns 1_6`() = assertEquals(1.6, ExchangeRateCalculator.rateFor(29))
    @Test fun `rateFor 30 returns 2_0`() = assertEquals(2.0, ExchangeRateCalculator.rateFor(30))
    @Test fun `rateFor 365 returns 2_0`() = assertEquals(2.0, ExchangeRateCalculator.rateFor(365))

    @Test fun `tierFor 0 returns level 1`() = assertEquals(1, ExchangeRateCalculator.tierFor(0).level)
    @Test fun `tierFor 7 returns level 2`() = assertEquals(2, ExchangeRateCalculator.tierFor(7).level)
    @Test fun `tierFor 14 returns level 3`() = assertEquals(3, ExchangeRateCalculator.tierFor(14).level)
    @Test fun `tierFor 21 returns level 4`() = assertEquals(4, ExchangeRateCalculator.tierFor(21).level)
    @Test fun `tierFor 30 returns level 5`() = assertEquals(5, ExchangeRateCalculator.tierFor(30).level)

    @Test fun `daysToNextTier 0 returns 7`() = assertEquals(7, ExchangeRateCalculator.daysToNextTier(0))
    @Test fun `daysToNextTier 6 returns 1`() = assertEquals(1, ExchangeRateCalculator.daysToNextTier(6))
    @Test fun `daysToNextTier 7 returns 7`() = assertEquals(7, ExchangeRateCalculator.daysToNextTier(7))
    @Test fun `daysToNextTier 13 returns 1`() = assertEquals(1, ExchangeRateCalculator.daysToNextTier(13))
    @Test fun `daysToNextTier 30 returns null at top tier`() = assertNull(ExchangeRateCalculator.daysToNextTier(30))
    @Test fun `daysToNextTier 100 returns null at top tier`() = assertNull(ExchangeRateCalculator.daysToNextTier(100))

    @Test fun `tiers list has 5 entries`() = assertEquals(5, ExchangeRateCalculator.tiers.size)
}
