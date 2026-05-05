package com.jktdeveloper.habitto.devtools

import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DevSeederTest {
    private val today = LocalDate(2026, 6, 1)
    private val seededRng = Random(42)

    private fun input(
        days: Int = 14,
        mode: SeedMode = SeedMode.Constant,
        constantLevel: Int = 4,
        freezeCount: Int = 0,
        brokenCount: Int = 0,
    ) = SeedInput(days, mode, constantLevel, freezeCount, brokenCount)

    @Test
    fun `quantityForLevel L1 returns 1`() {
        assertEquals(1, DevSeeder.quantityForLevel(1, target = 10))
        assertEquals(1, DevSeeder.quantityForLevel(1, target = 1))
        assertEquals(1, DevSeeder.quantityForLevel(1, target = 100))
    }

    @Test
    fun `quantityForLevel L4 returns the full target`() {
        assertEquals(10, DevSeeder.quantityForLevel(4, target = 10))
        assertEquals(1, DevSeeder.quantityForLevel(4, target = 1))
    }

    @Test
    fun `quantityForLevel L2 lands inside bucket 2 when target permits`() {
        // target=10 → bareMin=1, full=10, span=9, third=3, mid1=4, mid2=7
        // Bucket 2: pointsCapped in [mid1, mid2) = [4, 7).
        val qty = DevSeeder.quantityForLevel(2, target = 10)
        assertTrue(qty >= 4, "qty $qty must be ≥ 4")
        assertTrue(qty < 7, "qty $qty must be < 7")
    }

    @Test
    fun `quantityForLevel L3 lands inside bucket 3 when target permits`() {
        // target=10 → mid2=7, full=10. Bucket 3: pointsCapped in [7, 10).
        val qty = DevSeeder.quantityForLevel(3, target = 10)
        assertTrue(qty >= 7, "qty $qty must be ≥ 7")
        assertTrue(qty < 10, "qty $qty must be < 10")
    }

    @Test
    fun `plan with no gaps yields all complete days at chosen level`() {
        val result = DevSeeder.plan(input(days = 14, constantLevel = 4), today, seededRng).getOrThrow()
        assertEquals(14, result.size)
        assertTrue(result.all { it.kind is DaySlotKind.Complete })
        assertTrue(result.all { (it.kind as DaySlotKind.Complete).level == 4 })
    }

    @Test
    fun `plan dates run from today minus N inclusive to today exclusive`() {
        val result = DevSeeder.plan(input(days = 3), today, seededRng).getOrThrow()
        assertEquals(3, result.size)
        // Sorted ascending, oldest first.
        val dates = result.map { it.date }
        assertEquals(LocalDate(2026, 5, 29), dates[0])
        assertEquals(LocalDate(2026, 5, 30), dates[1])
        assertEquals(LocalDate(2026, 5, 31), dates[2])
    }

    @Test
    fun `plan with freeze and broken counts placement correctly`() {
        val result = DevSeeder.plan(
            input(days = 14, freezeCount = 2, brokenCount = 1),
            today, seededRng,
        ).getOrThrow()
        assertEquals(14, result.size)
        val frozen = result.count { it.kind == DaySlotKind.Frozen }
        val broken = result.count { it.kind == DaySlotKind.Broken }
        val complete = result.count { it.kind is DaySlotKind.Complete }
        assertEquals(2, frozen)
        assertEquals(2, broken)  // 1 broken pair = 2 broken slots
        assertEquals(10, complete)
    }

    @Test
    fun `plan rejects when freeze plus broken pairs fill the window`() {
        val result = DevSeeder.plan(
            input(days = 6, freezeCount = 2, brokenCount = 2),  // 2 + 2*2 = 6, no complete left
            today, seededRng,
        )
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `plan rejects when freeze exceeds window`() {
        val result = DevSeeder.plan(
            input(days = 5, freezeCount = 10),
            today, seededRng,
        )
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `plan random mode uses levels 1 through 4 only`() {
        repeat(50) { seedSeed ->
            val result = DevSeeder.plan(
                input(days = 30, mode = SeedMode.Random),
                today, Random(seedSeed.toLong()),
            ).getOrThrow()
            for (slot in result) {
                val complete = slot.kind as? DaySlotKind.Complete ?: continue
                assertTrue(complete.level in 1..4, "level ${complete.level} out of range")
            }
        }
    }

    @Test
    fun `plan broken pairs do not overlap`() {
        val result = DevSeeder.plan(
            input(days = 14, brokenCount = 3),
            today, seededRng,
        ).getOrThrow()
        // Broken slots are placed in pairs of adjacent days. Find runs of Broken
        // and assert each run length is exactly 2 (no overlapping pairs).
        val brokenIndices = result.withIndex()
            .filter { it.value.kind == DaySlotKind.Broken }
            .map { it.index }
        assertEquals(6, brokenIndices.size)
        val sorted = brokenIndices.sorted()
        val runs = mutableListOf<MutableList<Int>>()
        for (i in sorted) {
            val last = runs.lastOrNull()?.lastOrNull()
            if (last != null && i == last + 1) runs.last().add(i)
            else runs.add(mutableListOf(i))
        }
        // Three pairs, each length 2.
        assertEquals(3, runs.size)
        assertTrue(runs.all { it.size == 2 })
    }

    @Test
    fun `expectedRateForCompleteCount maps tier ladder`() {
        // 0 → 1.0×; 7 → 1.1×; 14 → 1.2×; 30 → 1.4×.
        assertEquals(1.0, DevSeeder.expectedRateForCompleteCount(0), 0.0)
        assertEquals(1.1, DevSeeder.expectedRateForCompleteCount(7), 0.0)
        assertEquals(1.2, DevSeeder.expectedRateForCompleteCount(14), 0.0)
        assertEquals(1.4, DevSeeder.expectedRateForCompleteCount(30), 0.0)
    }
}
