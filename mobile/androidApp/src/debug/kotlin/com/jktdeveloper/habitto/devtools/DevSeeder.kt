package com.jktdeveloper.habitto.devtools

import com.habittracker.domain.usecase.ExchangeRateCalculator
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.random.Random

enum class SeedMode { Constant, Random }

data class SeedInput(
    val days: Int,
    val mode: SeedMode,
    val constantLevel: Int,
    val freezeCount: Int,
    val brokenCount: Int,
)

sealed interface DaySlotKind {
    data class Complete(val level: Int) : DaySlotKind
    data object Frozen : DaySlotKind
    data object Broken : DaySlotKind
}

data class DaySlot(val date: LocalDate, val kind: DaySlotKind)

object DevSeeder {

    /**
     * Map a heat level (1..4) to per-habit log quantity.
     *
     * The streak engine's `bucketFor` (in `ComputeStreakUseCase`) divides the day's
     * pointsCapped sum into 4 buckets between bareMin (=active habit count) and
     * full (=sum of dailyTargets). For a single-habit day with target T:
     *   bareMin = 1, full = T, span = T - 1, third = (T - 1) / 3
     *   bucket 1: [1, 1 + third)
     *   bucket 2: [1 + third, 1 + 2*third)
     *   bucket 3: [1 + 2*third, T)
     *   bucket 4: [T, ...)
     *
     * Per-habit qty is chosen so the day-total bucket roughly matches L. For
     * L=2 we use `1 + third + 1` clamped below `target - 1` to land inside the
     * bucket-2 range for typical targets ≥ 4.
     *
     * Degenerate case: target = 1 collapses (bareMin == full). All non-zero
     * quantities yield bucket 4 in this case; we still return 1.
     */
    fun quantityForLevel(level: Int, target: Int): Int {
        require(level in 1..4) { "level must be 1..4, was $level" }
        require(target >= 1) { "target must be ≥ 1, was $target" }
        if (target == 1) return 1
        val span = target - 1
        val third = span / 3
        return when (level) {
            1 -> 1
            2 -> (1 + third + 1).coerceAtMost(target - 1)
            3 -> (1 + 2 * third + 1).coerceAtMost(target - 1)
            4 -> target
            else -> error("unreachable")
        }
    }

    /** Lookup helper for confirm-dialog UI. */
    fun expectedRateForCompleteCount(completeDaysEndingToday: Int): Double =
        ExchangeRateCalculator.rateFor(completeDaysEndingToday)

    /**
     * Returns N day slots from oldest to newest covering `[today - days, today)`,
     * with `freezeCount` slots marked Frozen and `brokenCount` pairs of adjacent
     * slots marked Broken. Remaining slots are Complete with the chosen level
     * (or random level 1..4 in Random mode).
     */
    fun plan(input: SeedInput, today: LocalDate, rng: Random): Result<List<DaySlot>> = runCatching {
        require(input.days in 1..35) { "days must be 1..35, was ${input.days}" }
        require(input.freezeCount >= 0) { "freezeCount must be ≥ 0" }
        require(input.brokenCount >= 0) { "brokenCount must be ≥ 0" }
        if (input.mode == SeedMode.Constant) {
            require(input.constantLevel in 1..4) {
                "constantLevel must be 1..4, was ${input.constantLevel}"
            }
        }
        val gapSlots = input.freezeCount + 2 * input.brokenCount
        if (gapSlots >= input.days) {
            error("Gaps fill window. Add ≥1 complete day.")
        }

        val kinds = MutableList<DaySlotKind?>(input.days) { null }

        // 1. Place broken pairs: pick non-overlapping 2-slot windows.
        val available = (0 until input.days).toMutableSet()
        repeat(input.brokenCount) {
            val candidates = available.filter { it + 1 in available }
            check(candidates.isNotEmpty()) { "no room for broken pair (internal)" }
            val start = candidates.random(rng)
            kinds[start] = DaySlotKind.Broken
            kinds[start + 1] = DaySlotKind.Broken
            available.remove(start)
            available.remove(start + 1)
        }

        // 2. Place freeze slots at any remaining position.
        repeat(input.freezeCount) {
            check(available.isNotEmpty()) { "no room for freeze (internal)" }
            val pos = available.random(rng)
            kinds[pos] = DaySlotKind.Frozen
            available.remove(pos)
        }

        // 3. Remaining = Complete with chosen level.
        for (i in 0 until input.days) {
            if (kinds[i] == null) {
                val level = when (input.mode) {
                    SeedMode.Constant -> input.constantLevel
                    SeedMode.Random -> (1..4).random(rng)
                }
                kinds[i] = DaySlotKind.Complete(level)
            }
        }

        // 4. Map index → date. Index 0 = oldest = today - days; index N-1 = today - 1.
        List(input.days) { i ->
            val offset = input.days - i
            DaySlot(date = today.minus(offset, DateTimeUnit.DAY), kind = kinds[i]!!)
        }
    }
}
