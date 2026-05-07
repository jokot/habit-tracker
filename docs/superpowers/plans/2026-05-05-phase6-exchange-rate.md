# Phase 6 — Exchange Rate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add stepped 5-tier exchange-rate multiplier on Want spending keyed off user-level streak. Pure rate calculator + historical streak lookup + integration into existing want spend math + dedicated `ExchangeRate` screen with hero + tier ladder + comparison list.

**Architecture:** Stateless `ExchangeRateCalculator` is single source of truth for the formula. `GetUserStreakOnDayUseCase` derives historical streak by walking `ComputeStreakUseCase` per-day state machine output. Three existing use cases (`LogWantUseCase`, `GetPointBalanceUseCase`, `GetDayPointsUseCase`) integrate the rate via the calculator. New `ExchangeRateScreen` reachable from YouHub row + Home Balance card tap.

**Tech Stack:** Kotlin Multiplatform shared module (`commonMain` + `commonTest`), Compose Material 3 (`androidMain`), kotlinx-datetime, kotlinx-coroutines (StateFlow + runTest). No new schema, no new dependencies.

---

## Spec

`docs/superpowers/specs/2026-05-05-phase6-exchange-rate-design.md`

## File Structure

| Layer | File | Responsibility |
|---|---|---|
| Domain model | `RateTier.kt` | Plain data class for one tier (level, rate, minStreak, maxStreak) |
| Domain calc | `ExchangeRateCalculator.kt` | Pure object — `tiers`, `rateFor`, `tierFor`, `daysToNextTier` |
| Domain use case | `GetUserStreakOnDayUseCase.kt` | Walks `ComputeStreakUseCase` output to count consecutive COMPLETE days ending on a given date |
| Domain use case | `LogWantUseCase.kt` | (modified) Apply rate at log-time using today's streak |
| Domain use case | `GetPointBalanceUseCase.kt` | (modified) Apply rate-at-log-day per want log |
| Domain use case | `GetDayPointsUseCase.kt` | (modified) Apply rate-at-day to all want logs on that day |
| ViewModel | `ExchangeRateViewModel.kt` | Reactive state from streak + wants → tier ladder + comparison |
| Screen | `ExchangeRateScreen.kt` | Compose UI: hero + tier ladder + comparison list |
| Wiring | `AppContainer.kt` | Construct + expose new use cases |
| Wiring | `AppNavigation.kt` | `Screen.ExchangeRate` route + composable mount |
| Entry | `YouHubScreen.kt` | "Earn & spend" section, "Point exchange rate" row |
| Entry | `HomeScreen.kt` + `HomeViewModel.kt` | Balance card tap nav, expose `currentRate` |

---

## Task 1: `RateTier` Model + `ExchangeRateCalculator`

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/RateTier.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculator.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculatorTest.kt`

- [ ] **Step 1: Write the failing tests**

`mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculatorTest.kt`:

```kotlin
package com.habittracker.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExchangeRateCalculatorTest {
    @Test fun `rateFor 0 returns 1_0`() = assertEquals(1.0, ExchangeRateCalculator.rateFor(0))
    @Test fun `rateFor 6 returns 1_0`() = assertEquals(1.0, ExchangeRateCalculator.rateFor(6))
    @Test fun `rateFor 7 returns 1_1`() = assertEquals(1.1, ExchangeRateCalculator.rateFor(7))
    @Test fun `rateFor 13 returns 1_1`() = assertEquals(1.1, ExchangeRateCalculator.rateFor(13))
    @Test fun `rateFor 14 returns 1_2`() = assertEquals(1.2, ExchangeRateCalculator.rateFor(14))
    @Test fun `rateFor 20 returns 1_2`() = assertEquals(1.2, ExchangeRateCalculator.rateFor(20))
    @Test fun `rateFor 21 returns 1_3`() = assertEquals(1.3, ExchangeRateCalculator.rateFor(21))
    @Test fun `rateFor 29 returns 1_3`() = assertEquals(1.3, ExchangeRateCalculator.rateFor(29))
    @Test fun `rateFor 30 returns 1_4`() = assertEquals(1.4, ExchangeRateCalculator.rateFor(30))
    @Test fun `rateFor 100 returns 1_4`() = assertEquals(1.4, ExchangeRateCalculator.rateFor(100))

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
```

- [ ] **Step 2: Run test to verify it fails (compile error — symbols don't exist yet)**

Run from `/Users/jokot/dev/habit-tracker/.worktrees/phase6-exchange-rate`:

```
rtk ./gradlew :mobile:shared:compileTestKotlinMetadata 2>&1 | tail -10
```

Expected: unresolved reference `ExchangeRateCalculator`.

- [ ] **Step 3: Create `RateTier` model**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/RateTier.kt`:

```kotlin
package com.habittracker.domain.model

/**
 * One step on the exchange-rate ladder. The rate applies to every Want spend made
 * while the user-level streak falls within `minStreak..maxStreak` (inclusive). A
 * `maxStreak` of `null` denotes the top tier (no upper bound).
 */
data class RateTier(
    val level: Int,
    val rate: Double,
    val minStreak: Int,
    val maxStreak: Int?,
)
```

- [ ] **Step 4: Create `ExchangeRateCalculator`**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculator.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.domain.model.RateTier

/**
 * Stepped tier ladder for the Phase 6 exchange rate. Pure + stateless.
 *
 * Rate applies to Want spending only — habit earning is unchanged.
 */
object ExchangeRateCalculator {
    val tiers: List<RateTier> = listOf(
        RateTier(level = 1, rate = 1.0, minStreak = 0,  maxStreak = 6),
        RateTier(level = 2, rate = 1.1, minStreak = 7,  maxStreak = 13),
        RateTier(level = 3, rate = 1.2, minStreak = 14, maxStreak = 20),
        RateTier(level = 4, rate = 1.3, minStreak = 21, maxStreak = 29),
        RateTier(level = 5, rate = 1.4, minStreak = 30, maxStreak = null),
    )

    fun rateFor(streak: Int): Double = tierFor(streak).rate

    fun tierFor(streak: Int): RateTier = tiers.first { tier ->
        streak >= tier.minStreak && (tier.maxStreak == null || streak <= tier.maxStreak)
    }

    /** Days remaining until the user moves to the next tier. Null at the top tier. */
    fun daysToNextTier(streak: Int): Int? {
        val current = tierFor(streak)
        val next = tiers.firstOrNull { it.level == current.level + 1 } ?: return null
        return next.minStreak - streak
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*ExchangeRateCalculator*" 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run full shared suite (no regressions)**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/RateTier.kt \
    mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculator.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculatorTest.kt
rtk git commit -m "feat(rate): ExchangeRateCalculator stepped 5-tier ladder + RateTier model"
```

---

## Task 2: `GetUserStreakOnDayUseCase`

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetUserStreakOnDayUseCase.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetUserStreakOnDayUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habittracker.domain.usecase

// Reuse existing in-memory fakes used by ComputeStreakUseCaseTest.
// If the actual class names differ, run:
//   rtk grep -rn "class MutableInMemoryHabit" mobile/shared/src/commonTest --include="*.kt"
// to find the package + class names, then update imports.
import com.habittracker.data.repository.MutableInMemoryHabitLogRepo
import com.habittracker.data.repository.MutableInMemoryHabitRepo
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetUserStreakOnDayUseCaseTest {
    private val tz = TimeZone.UTC
    private val userId = "u1"

    @Test
    fun `empty logs return 0 for any date`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val sut = makeSut(today, habits = emptyList(), logs = emptyList())
        assertEquals(0, sut.execute(userId, today))
        assertEquals(0, sut.execute(userId, today.minus(10, DateTimeUnit.DAY)))
    }

    @Test
    fun `5 consecutive complete days ending today returns 5 querying today`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val habits = listOf(makeHabit("h1"))
        val logs = (4 downTo 0).map { offset ->
            log("h1", today.minus(offset, DateTimeUnit.DAY))
        }
        val sut = makeSut(today, habits, logs)
        assertEquals(5, sut.execute(userId, today))
    }

    @Test
    fun `5 consecutive complete days ending today returns 4 querying yesterday`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val habits = listOf(makeHabit("h1"))
        val logs = (4 downTo 0).map { offset ->
            log("h1", today.minus(offset, DateTimeUnit.DAY))
        }
        val sut = makeSut(today, habits, logs)
        assertEquals(4, sut.execute(userId, today.minus(1, DateTimeUnit.DAY)))
    }

    @Test
    fun `gap in middle resets streak count`() = runTest {
        // Mon-Wed COMPLETE, Thu missed, Fri-Sat COMPLETE → query Sat → 2; query Wed → 3
        val sat = LocalDate(2026, 5, 9)
        val mon = sat.minus(5, DateTimeUnit.DAY)
        val habits = listOf(makeHabit("h1"))
        val days = listOf(mon, mon.plus(1, DateTimeUnit.DAY), mon.plus(2, DateTimeUnit.DAY), mon.plus(4, DateTimeUnit.DAY), mon.plus(5, DateTimeUnit.DAY))
        val logs = days.map { log("h1", it) }
        val sut = makeSut(today = sat, habits, logs)
        assertEquals(2, sut.execute(userId, sat))
        assertEquals(3, sut.execute(userId, mon.plus(2, DateTimeUnit.DAY)))
    }

    @Test
    fun `today not complete returns 0 even if yesterday was`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val habits = listOf(makeHabit("h1"))
        val logs = listOf(log("h1", today.minus(1, DateTimeUnit.DAY)))
        val sut = makeSut(today, habits, logs)
        assertEquals(0, sut.execute(userId, today))
        assertEquals(1, sut.execute(userId, today.minus(1, DateTimeUnit.DAY)))
    }

    private fun makeSut(
        today: LocalDate,
        habits: List<Habit>,
        logs: List<HabitLog>,
    ): GetUserStreakOnDayUseCase {
        val now = LocalDateTime(today, LocalTime(12, 0)).toInstant(tz)
        val clock = object : Clock { override fun now(): Instant = now }
        val habitRepo = MutableInMemoryHabitRepo()
        habits.forEach { habitRepo.saveHabit(it) }
        val logRepo = MutableInMemoryHabitLogRepo(tz)
        logs.forEach { logRepo.insertLog(it.id, it.userId, it.habitId, it.quantity, it.loggedAt) }
        val streak = ComputeStreakUseCase(logRepo, habitRepo, tz, clock)
        return GetUserStreakOnDayUseCase(streak, tz)
    }

    private fun makeHabit(id: String) = Habit(
        id = id, userId = userId, templateId = null, name = id, unit = "x",
        thresholdPerPoint = 1.0, dailyTarget = 1,
        createdAt = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz),
        updatedAt = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz),
        effectiveFrom = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz),
    )

    private fun log(habitId: String, date: LocalDate) = HabitLog(
        id = "log-$habitId-$date",
        userId = userId,
        habitId = habitId,
        quantity = 1.0,
        loggedAt = LocalDateTime(date, LocalTime(10, 0)).toInstant(tz),
        deletedAt = null,
        syncedAt = null,
    )
}
```

- [ ] **Step 2: Run test to verify compile fail**

Run: `rtk ./gradlew :mobile:shared:compileTestKotlinMetadata 2>&1 | tail -10`

Expected: unresolved reference `GetUserStreakOnDayUseCase`.

- [ ] **Step 3: Implement `GetUserStreakOnDayUseCase`**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetUserStreakOnDayUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.StreakDayState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Returns the user-level streak ending on `date` — count of consecutive COMPLETE
 * days going backward from `date` inclusive. Returns 0 if `date` itself is not
 * COMPLETE or the user has no relevant logs.
 *
 * Used by GetPointBalanceUseCase + GetDayPointsUseCase to apply rate-at-log-day
 * for past Want spends (Phase 6).
 */
class GetUserStreakOnDayUseCase(
    private val streakUseCase: ComputeStreakUseCase,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend fun execute(userId: String, date: LocalDate): Int {
        val window = DateRange(
            start = date.minus(365, DateTimeUnit.DAY),
            endExclusive = date.plus(1, DateTimeUnit.DAY),
        )
        val result = streakUseCase.computeNow(userId, window)
        val byDate = result.days.associateBy { it.date }
        var run = 0
        var cursor = date
        while (true) {
            val state = byDate[cursor]?.state ?: break
            if (state == StreakDayState.COMPLETE) {
                run += 1
                cursor = cursor.minus(1, DateTimeUnit.DAY)
            } else break
        }
        return run
    }
}
```

> Verify `DateRange` import: `rtk grep -rn "data class DateRange" mobile/shared/src/commonMain --include="*.kt"`. Adjust if needed.

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*GetUserStreakOnDay*" 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run full shared suite**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetUserStreakOnDayUseCase.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetUserStreakOnDayUseCaseTest.kt
rtk git commit -m "feat(rate): GetUserStreakOnDayUseCase — historical streak count from engine"
```

---

## Task 3: Apply Rate in `LogWantUseCase`

`LogWantUseCase` currently computes `pointsSpent(quantity, costPerUnit)`. After this task it multiplies by today's rate before checking balance / inserting log.

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LogWantUseCase.kt`
- Create or modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LogWantUseCaseRateTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.MutableInMemoryHabitLogRepo
import com.habittracker.data.repository.MutableInMemoryHabitRepo
import com.habittracker.data.repository.MutableInMemoryWantActivityRepo
import com.habittracker.data.repository.MutableInMemoryWantLogRepo
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class LogWantUseCaseRateTest {
    private val tz = TimeZone.UTC
    private val userId = "u1"

    @Test
    fun `streak 0 applies rate 1_0`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val sut = makeSut(today, completeDaysEndingToday = 0)
        seedActivity(sut.wantActivityRepo, "a1", costPerUnit = 1.0)
        seedEarn(sut.habitLogRepo, sut.habitRepo, today, points = 100)
        val result = sut.useCase.execute(userId, "a1", quantity = 10.0, deviceMode = DeviceMode.MOBILE).getOrThrow()
        assertEquals(10, result.pointsSpent)
    }

    @Test
    fun `streak 14 applies rate 1_2 — 10 cost 1 = 12 pts`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val sut = makeSut(today, completeDaysEndingToday = 14)
        seedActivity(sut.wantActivityRepo, "a1", costPerUnit = 1.0)
        seedEarn(sut.habitLogRepo, sut.habitRepo, today, points = 100)
        val result = sut.useCase.execute(userId, "a1", quantity = 10.0, deviceMode = DeviceMode.MOBILE).getOrThrow()
        assertEquals(12, result.pointsSpent)
    }

    @Test
    fun `streak 30 applies rate 1_4 — 10 cost 1 = 14 pts`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val sut = makeSut(today, completeDaysEndingToday = 30)
        seedActivity(sut.wantActivityRepo, "a1", costPerUnit = 1.0)
        seedEarn(sut.habitLogRepo, sut.habitRepo, today, points = 100)
        val result = sut.useCase.execute(userId, "a1", quantity = 10.0, deviceMode = DeviceMode.MOBILE).getOrThrow()
        assertEquals(14, result.pointsSpent)
    }

    private data class Sut(
        val useCase: LogWantUseCase,
        val habitRepo: MutableInMemoryHabitRepo,
        val habitLogRepo: MutableInMemoryHabitLogRepo,
        val wantActivityRepo: MutableInMemoryWantActivityRepo,
        val wantLogRepo: MutableInMemoryWantLogRepo,
    )

    private fun makeSut(today: LocalDate, completeDaysEndingToday: Int): Sut {
        val nowInstant = LocalDateTime(today, LocalTime(12, 0)).toInstant(tz)
        val clock = object : Clock { override fun now(): Instant = nowInstant }
        val habitRepo = MutableInMemoryHabitRepo()
        val habitLogRepo = MutableInMemoryHabitLogRepo(tz)
        val wantActivityRepo = MutableInMemoryWantActivityRepo()
        val wantLogRepo = MutableInMemoryWantLogRepo()

        if (completeDaysEndingToday > 0) {
            val anchor = LocalDateTime(LocalDate(2025, 1, 1), LocalTime(0, 0)).toInstant(tz)
            habitRepo.saveHabit(
                Habit(
                    id = "h1", userId = userId, templateId = null, name = "h1", unit = "x",
                    thresholdPerPoint = 1.0, dailyTarget = 1,
                    createdAt = anchor, updatedAt = anchor, effectiveFrom = anchor,
                )
            )
            (0 until completeDaysEndingToday).forEach { offset ->
                val d = today.minus(offset, DateTimeUnit.DAY)
                habitLogRepo.insertLog(
                    id = "l-$d", userId = userId, habitId = "h1",
                    quantity = 1.0,
                    loggedAt = LocalDateTime(d, LocalTime(10, 0)).toInstant(tz),
                )
            }
        }

        val balance = GetPointBalanceUseCase(
            habitLogRepo = habitLogRepo,
            wantLogRepo = wantLogRepo,
            habitRepo = habitRepo,
            wantActivityRepo = wantActivityRepo,
            timeZone = tz,
            clock = clock,
        )
        val streak = ComputeStreakUseCase(habitLogRepo, habitRepo, tz, clock)
        val streakOnDay = GetUserStreakOnDayUseCase(streak, tz)
        val useCase = LogWantUseCase(
            wantLogRepository = wantLogRepo,
            wantActivityRepository = wantActivityRepo,
            getPointBalanceUseCase = balance,
            getUserStreakOnDayUseCase = streakOnDay,
            clock = clock,
            timeZone = tz,
        )
        return Sut(useCase, habitRepo, habitLogRepo, wantActivityRepo, wantLogRepo)
    }

    private fun seedActivity(repo: MutableInMemoryWantActivityRepo, id: String, costPerUnit: Double) {
        repo.upsertActivity(
            WantActivity(id = id, userId = userId, name = id, unit = "u", costPerUnit = costPerUnit)
        )
    }

    private fun seedEarn(
        habitLogRepo: MutableInMemoryHabitLogRepo,
        habitRepo: MutableInMemoryHabitRepo,
        today: LocalDate,
        points: Int,
    ) {
        val anchor = LocalDateTime(LocalDate(2024, 1, 1), LocalTime(0, 0)).toInstant(tz)
        habitRepo.saveHabit(
            Habit(
                id = "h-spend", userId = userId, templateId = null, name = "spend", unit = "x",
                thresholdPerPoint = 1.0, dailyTarget = points * 2,
                createdAt = anchor, updatedAt = anchor, effectiveFrom = anchor,
            )
        )
        habitLogRepo.insertLog(
            id = "l-spend", userId = userId, habitId = "h-spend",
            quantity = points.toDouble(),
            loggedAt = LocalDateTime(today, LocalTime(8, 0)).toInstant(tz),
        )
    }
}
```

> If `MutableInMemoryWantActivityRepo` / `MutableInMemoryWantLogRepo` don't exist by those exact names, run `rtk grep -rn "class.*WantActivityRepository\|class.*WantLogRepository" mobile/shared/src/commonTest --include="*.kt"` to discover the actual fake names. If `upsertActivity` / `insertLog` differ, mirror the actual API.

- [ ] **Step 2: Run test (compile fail — `LogWantUseCase` constructor signature changed)**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*LogWantUseCaseRateTest*" 2>&1 | tail -10`

Expected: compile error.

- [ ] **Step 3: Update `LogWantUseCase`**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LogWantUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class LogWantResult(val log: WantLog, val pointsSpent: Int)

class InsufficientPointsException(
    val available: Int,
    val required: Int,
) : Exception("Not enough points: need $required, have $available")

class LogWantUseCase(
    private val wantLogRepository: WantLogRepository,
    private val wantActivityRepository: WantActivityRepository,
    private val getPointBalanceUseCase: GetPointBalanceUseCase,
    private val getUserStreakOnDayUseCase: GetUserStreakOnDayUseCase,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(
        userId: String,
        activityId: String,
        quantity: Double,
        deviceMode: DeviceMode,
    ): Result<LogWantResult> = runCatching {
        val activity = wantActivityRepository.getWantActivities(userId)
            .firstOrNull { it.id == activityId }
            ?: error("Activity $activityId not found")

        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val streakOnDay = getUserStreakOnDayUseCase.execute(userId, today)
        val rate = ExchangeRateCalculator.rateFor(streakOnDay)
        val points = pointsSpentWithRate(quantity, activity.costPerUnit, rate)

        val balance = getPointBalanceUseCase.execute(userId).getOrThrow().balance
        if (points > balance) throw InsufficientPointsException(balance, points)

        val id = Uuid.random().toString()
        val log = wantLogRepository.insertLog(id, userId, activityId, quantity, deviceMode, now)
        LogWantResult(log, points)
    }

    /** Cost × rate, rounded up, with `1pt` minimum if any quantity was consumed. */
    internal fun pointsSpentWithRate(quantity: Double, costPerUnit: Double, rate: Double): Int {
        if (quantity <= 0.0 || costPerUnit <= 0.0) return 0
        return ceil(quantity * costPerUnit * rate).toInt().coerceAtLeast(1)
    }
}
```

- [ ] **Step 4: Run rate test**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*LogWantUseCase*" 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Update existing pre-Phase-6 LogWantUseCase tests if they break**

If any existing test constructed `LogWantUseCase(...)` with the old 3-param constructor, add the two new params using fakes:

```kotlin
val streak = ComputeStreakUseCase(habitLogRepo, habitRepo, tz, clock)
val streakOnDay = GetUserStreakOnDayUseCase(streak, tz)
LogWantUseCase(
    wantLogRepository = wantLogRepo,
    wantActivityRepository = wantActivityRepo,
    getPointBalanceUseCase = balance,
    getUserStreakOnDayUseCase = streakOnDay,
    clock = clock,
    timeZone = tz,
)
```

If a pre-existing test didn't have habit fakes set up at all, the streak will be 0 → rate 1.0 → original points unchanged. Old assertions hold.

- [ ] **Step 6: Run full shared suite**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LogWantUseCase.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LogWantUseCaseRateTest.kt
# Plus any pre-existing test fixups
rtk git commit -m "feat(rate): LogWantUseCase applies rate-at-log-time to spend points"
```

---

## Task 4: Apply Rate-at-Log-Day in `GetPointBalanceUseCase`

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseRateTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.MutableInMemoryHabitLogRepo
import com.habittracker.data.repository.MutableInMemoryHabitRepo
import com.habittracker.data.repository.MutableInMemoryWantActivityRepo
import com.habittracker.data.repository.MutableInMemoryWantLogRepo
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetPointBalanceUseCaseRateTest {
    private val tz = TimeZone.UTC
    private val userId = "u1"

    @Test
    fun `want logs use rate-at-log-day`() = runTest {
        // Setup: today = Sun May 31 2026. Habits logged Mon May 25..Sun May 31 (7 days).
        // Mon May 25 = streak 1 → rate 1.0
        // Sun May 31 = streak 7 → rate 1.1
        // Spend on each → assert balance reflects different rates per log day.
        val today = LocalDate(2026, 5, 31)
        val mon = today.minus(6, DateTimeUnit.DAY)
        val now = LocalDateTime(today, LocalTime(20, 0)).toInstant(tz)
        val clock = object : Clock { override fun now(): Instant = now }

        val habitRepo = MutableInMemoryHabitRepo()
        val habitLogRepo = MutableInMemoryHabitLogRepo(tz)
        val wantActivityRepo = MutableInMemoryWantActivityRepo()
        val wantLogRepo = MutableInMemoryWantLogRepo()

        val anchor = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz)
        habitRepo.saveHabit(
            Habit(
                id = "h1", userId = userId, templateId = null, name = "h", unit = "x",
                thresholdPerPoint = 1.0, dailyTarget = 1,
                createdAt = anchor, updatedAt = anchor, effectiveFrom = anchor,
            )
        )
        (0..6).forEach { offset ->
            val d = today.minus(offset, DateTimeUnit.DAY)
            habitLogRepo.insertLog(
                id = "l-$d", userId = userId, habitId = "h1",
                quantity = 1.0,
                loggedAt = LocalDateTime(d, LocalTime(10, 0)).toInstant(tz),
            )
        }

        wantActivityRepo.upsertActivity(
            WantActivity(id = "a1", userId = userId, name = "a", unit = "u", costPerUnit = 5.0)
        )
        // Mon spend: streak 1 → rate 1.0 → ceil(5) = 5
        wantLogRepo.insertLog(
            id = "w-mon", userId = userId, activityId = "a1", quantity = 1.0,
            deviceMode = DeviceMode.MOBILE,
            loggedAt = LocalDateTime(mon, LocalTime(11, 0)).toInstant(tz),
        )
        // Sun spend: streak 7 → rate 1.1 → ceil(5.5) = 6
        wantLogRepo.insertLog(
            id = "w-sun", userId = userId, activityId = "a1", quantity = 1.0,
            deviceMode = DeviceMode.MOBILE,
            loggedAt = LocalDateTime(today, LocalTime(11, 0)).toInstant(tz),
        )

        val streak = ComputeStreakUseCase(habitLogRepo, habitRepo, tz, clock)
        val streakOnDay = GetUserStreakOnDayUseCase(streak, tz)
        val sut = GetPointBalanceUseCase(
            habitLogRepo = habitLogRepo,
            wantLogRepo = wantLogRepo,
            habitRepo = habitRepo,
            wantActivityRepo = wantActivityRepo,
            timeZone = tz,
            clock = clock,
            getUserStreakOnDayUseCase = streakOnDay,
        )

        val balance = sut.execute(userId).getOrThrow()
        // Spent total = 5 (Mon @ 1.0) + 6 (Sun @ 1.1) = 11
        assertEquals(11, balance.spent)
    }

    @Test
    fun `habit earning is NOT rate-multiplied`() = runTest {
        val today = LocalDate(2026, 5, 5)
        val now = LocalDateTime(today, LocalTime(20, 0)).toInstant(tz)
        val clock = object : Clock { override fun now(): Instant = now }
        val habitRepo = MutableInMemoryHabitRepo()
        val habitLogRepo = MutableInMemoryHabitLogRepo(tz)
        val wantActivityRepo = MutableInMemoryWantActivityRepo()
        val wantLogRepo = MutableInMemoryWantLogRepo()

        val anchor = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz)
        habitRepo.saveHabit(
            Habit(
                id = "h1", userId = userId, templateId = null, name = "h", unit = "x",
                thresholdPerPoint = 1.0, dailyTarget = 5,
                createdAt = anchor, updatedAt = anchor, effectiveFrom = anchor,
            )
        )
        (0..13).forEach { offset ->
            val d = today.minus(offset, DateTimeUnit.DAY)
            habitLogRepo.insertLog(
                id = "l-$d", userId = userId, habitId = "h1",
                quantity = 5.0,  // earn 5 pts per day
                loggedAt = LocalDateTime(d, LocalTime(10, 0)).toInstant(tz),
            )
        }

        val streak = ComputeStreakUseCase(habitLogRepo, habitRepo, tz, clock)
        val streakOnDay = GetUserStreakOnDayUseCase(streak, tz)
        val sut = GetPointBalanceUseCase(
            habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo, tz, clock, streakOnDay,
        )

        val balance = sut.execute(userId).getOrThrow()
        // earnedToday must remain 5, not 5×1.2=6
        assertEquals(5, balance.earnedToday)
    }
}
```

- [ ] **Step 2: Run test (will fail — constructor doesn't take `getUserStreakOnDayUseCase`)**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*GetPointBalanceUseCaseRateTest*" 2>&1 | tail -10`

Expected: compile error.

- [ ] **Step 3: Update `GetPointBalanceUseCase`**

Replace the file contents:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.PointBalance
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil

class GetPointBalanceUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val wantLogRepo: WantLogRepository,
    private val habitRepo: HabitRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
    private val getUserStreakOnDayUseCase: GetUserStreakOnDayUseCase? = null,
) {
    suspend fun execute(userId: String): Result<PointBalance> = runCatching {
        val today = clock.now().toLocalDateTime(timeZone).date
        val weekStartDate = currentWeekStartDate()
        val habits = habitRepo.getHabitsForUser(userId).associateBy { it.id }
        val activities = wantActivityRepo.getWantActivities(userId).associateBy { it.id }
        val dailyEarnCap = habits.values.sumOf { it.dailyTarget }
        val rolloverCap = dailyEarnCap * 2

        val weekStartInstant = weekStartDate.atStartOfDayIn(timeZone)
        val habitLogs = habitLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= weekStartInstant }
        val wantLogs = wantLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= weekStartInstant }

        var balance = 0
        var totalEarned = 0
        var totalSpent = 0
        var earnedToday = 0
        var spentToday = 0

        var day = weekStartDate
        while (day <= today) {
            val earnedThisDay = earnedOnDay(day, habitLogs, habits)
            val spentThisDay = spentOnDay(userId, day, wantLogs, activities)
            if (day != weekStartDate) balance = minOf(balance, rolloverCap)
            balance = (balance + earnedThisDay - spentThisDay).coerceAtLeast(0)
            totalEarned += earnedThisDay
            totalSpent += spentThisDay
            if (day == today) {
                earnedToday = earnedThisDay
                spentToday = spentThisDay
            }
            day = day.plus(1, DateTimeUnit.DAY)
        }

        PointBalance(
            earned = totalEarned,
            spent = totalSpent,
            balance = balance,
            earnedToday = earnedToday,
            spentToday = spentToday,
        )
    }

    private fun earnedOnDay(
        day: LocalDate,
        weekHabitLogs: List<HabitLog>,
        habits: Map<String, Habit>,
    ): Int {
        val dayStart = day.atStartOfDayIn(timeZone)
        val nextDayStart = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        val byHabit = weekHabitLogs
            .filter { it.loggedAt >= dayStart && it.loggedAt < nextDayStart }
            .groupBy { it.habitId }
        return byHabit.entries.sumOf { (habitId, logs) ->
            val habit = habits[habitId] ?: return@sumOf 0
            logs.sumOf { PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint) }
                .coerceAtMost(habit.dailyTarget)
        }
    }

    private suspend fun spentOnDay(
        userId: String,
        day: LocalDate,
        weekWantLogs: List<WantLog>,
        activities: Map<String, WantActivity>,
    ): Int {
        val dayStart = day.atStartOfDayIn(timeZone)
        val nextDayStart = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        val rate = if (getUserStreakOnDayUseCase != null) {
            ExchangeRateCalculator.rateFor(getUserStreakOnDayUseCase.execute(userId, day))
        } else {
            1.0
        }
        return weekWantLogs
            .filter { it.loggedAt >= dayStart && it.loggedAt < nextDayStart }
            .sumOf { log ->
                val activity = activities[log.activityId] ?: return@sumOf 0
                pointsSpentWithRate(log.quantity, activity.costPerUnit, rate)
            }
    }

    /** Cost × rate, rounded up, with `1pt` minimum if any quantity was consumed. */
    internal fun pointsSpentWithRate(quantity: Double, costPerUnit: Double, rate: Double): Int {
        if (quantity <= 0.0 || costPerUnit <= 0.0) return 0
        return ceil(quantity * costPerUnit * rate).toInt().coerceAtLeast(1)
    }

    /** Local Monday 00:00 of the current week, as a LocalDate. */
    internal fun currentWeekStartDate(): LocalDate {
        val today = clock.now().toLocalDateTime(timeZone).date
        val daysFromMonday = (today.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7
        return today.minus(daysFromMonday, DateTimeUnit.DAY)
    }

    /** Backwards-compatible: weekStart as Instant in local TZ. */
    internal fun currentWeekStart(): Instant =
        currentWeekStartDate().atStartOfDayIn(timeZone)
}
```

> The `getUserStreakOnDayUseCase` param defaults to `null` for migration safety. Pre-Phase-6 callers and tests get the old behavior (rate=1.0).

- [ ] **Step 4: Run rate test**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*GetPointBalanceUseCaseRateTest*" 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run full shared suite**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseRateTest.kt
rtk git commit -m "feat(rate): GetPointBalanceUseCase applies rate-at-log-day to want spends"
```

---

## Task 5: Apply Rate-at-Day in `GetDayPointsUseCase`

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCaseRateTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.MutableInMemoryHabitLogRepo
import com.habittracker.data.repository.MutableInMemoryHabitRepo
import com.habittracker.data.repository.MutableInMemoryWantActivityRepo
import com.habittracker.data.repository.MutableInMemoryWantLogRepo
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetDayPointsUseCaseRateTest {
    private val tz = TimeZone.UTC
    private val userId = "u1"

    @Test
    fun `day with streak 14 applies rate 1_2 to want spend on that day`() = runTest {
        val today = LocalDate(2026, 5, 30)
        val now = LocalDateTime(today, LocalTime(20, 0)).toInstant(tz)
        val clock = object : Clock { override fun now(): Instant = now }
        val habitRepo = MutableInMemoryHabitRepo()
        val habitLogRepo = MutableInMemoryHabitLogRepo(tz)
        val wantActivityRepo = MutableInMemoryWantActivityRepo()
        val wantLogRepo = MutableInMemoryWantLogRepo()

        val anchor = LocalDateTime(LocalDate(2026, 1, 1), LocalTime(0, 0)).toInstant(tz)
        habitRepo.saveHabit(
            Habit(
                id = "h1", userId = userId, templateId = null, name = "h", unit = "x",
                thresholdPerPoint = 1.0, dailyTarget = 5,
                createdAt = anchor, updatedAt = anchor, effectiveFrom = anchor,
            )
        )
        (0..13).forEach { offset ->
            val d = today.minus(offset, DateTimeUnit.DAY)
            habitLogRepo.insertLog(
                id = "l-$d", userId = userId, habitId = "h1",
                quantity = 1.0,
                loggedAt = LocalDateTime(d, LocalTime(10, 0)).toInstant(tz),
            )
        }
        wantActivityRepo.upsertActivity(
            WantActivity(id = "a1", userId = userId, name = "a", unit = "u", costPerUnit = 5.0)
        )
        // Spend today: qty 1 × cost 5 × rate 1.2 = ceil(6.0) = 6
        wantLogRepo.insertLog(
            id = "w-today", userId = userId, activityId = "a1", quantity = 1.0,
            deviceMode = DeviceMode.MOBILE,
            loggedAt = LocalDateTime(today, LocalTime(11, 0)).toInstant(tz),
        )

        val streak = ComputeStreakUseCase(habitLogRepo, habitRepo, tz, clock)
        val streakOnDay = GetUserStreakOnDayUseCase(streak, tz)
        val sut = GetDayPointsUseCase(
            habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo, tz, streakOnDay,
        )

        val day = sut.execute(userId, today).getOrThrow()
        assertEquals(6, day.spent)
        assertEquals(1, day.earned)  // earn unaffected by rate
    }
}
```

- [ ] **Step 2: Run test (compile fail)**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*GetDayPointsUseCaseRateTest*" 2>&1 | tail -10`

Expected: compile error.

- [ ] **Step 3: Update `GetDayPointsUseCase`**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.DayPoints
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.math.ceil

class GetDayPointsUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val wantLogRepo: WantLogRepository,
    private val habitRepo: HabitRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val getUserStreakOnDayUseCase: GetUserStreakOnDayUseCase? = null,
) {
    suspend fun execute(userId: String, day: LocalDate): Result<DayPoints> = runCatching {
        val dayStart = day.atStartOfDayIn(timeZone)
        val nextDayStart = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        val habits = habitRepo.getHabitsForUser(userId).associateBy { it.id }
        val activities = wantActivityRepo.getWantActivities(userId).associateBy { it.id }

        val earned = habitLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= dayStart && it.loggedAt < nextDayStart }
            .groupBy { it.habitId }
            .entries.sumOf { (habitId, logs) ->
                val habit = habits[habitId] ?: return@sumOf 0
                logs.sumOf { PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint) }
                    .coerceAtMost(habit.dailyTarget)
            }

        val rate = if (getUserStreakOnDayUseCase != null) {
            ExchangeRateCalculator.rateFor(getUserStreakOnDayUseCase.execute(userId, day))
        } else {
            1.0
        }

        val spent = wantLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= dayStart && it.loggedAt < nextDayStart }
            .sumOf { log ->
                val activity = activities[log.activityId] ?: return@sumOf 0
                pointsSpentWithRate(log.quantity, activity.costPerUnit, rate)
            }

        DayPoints(earned = earned, spent = spent)
    }

    /** Cost × rate, rounded up, with `1pt` minimum if any quantity was consumed. */
    internal fun pointsSpentWithRate(quantity: Double, costPerUnit: Double, rate: Double): Int {
        if (quantity <= 0.0 || costPerUnit <= 0.0) return 0
        return ceil(quantity * costPerUnit * rate).toInt().coerceAtLeast(1)
    }
}
```

- [ ] **Step 4: Run rate test**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*GetDayPointsUseCaseRateTest*" 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run full shared suite**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCase.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCaseRateTest.kt
rtk git commit -m "feat(rate): GetDayPointsUseCase applies rate-at-day to want spends"
```

---

## Task 6: `ExchangeRateViewModel`

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModel.kt`
- Test: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jktdeveloper.habitto.ui.exchange

import com.habittracker.domain.model.StreakSummary
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeRateViewModelTest {
    @Before
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `streak 0 → tier 1 rate 1_0 daysToNext 7`() = runTest {
        val vm = ExchangeRateViewModel(
            userIdProvider = { "u1" },
            streakFlow = { flowOf(StreakSummary(currentStreak = 0, longestStreak = 0, totalDaysComplete = 0, lastCompleteDate = null)) },
            wantActivitiesProvider = { listOf(makeActivity("a1", 5.0)) },
        )
        val state = vm.state.first { !it.isLoading }
        assertEquals(0, state.currentStreak)
        assertEquals(1.0, state.currentRate)
        assertEquals(1, state.currentTier.level)
        assertEquals(7, state.daysToNext)
        assertEquals(1, state.comparison.size)
        assertEquals(5.0, state.comparison.first().baseCostPerUnit)
        assertEquals(5.0, state.comparison.first().currentCostPerUnit)
    }

    @Test
    fun `streak 22 → tier 4 rate 1_3 daysToNext 8`() = runTest {
        val vm = ExchangeRateViewModel(
            userIdProvider = { "u1" },
            streakFlow = { flowOf(StreakSummary(currentStreak = 22, longestStreak = 22, totalDaysComplete = 22, lastCompleteDate = null)) },
            wantActivitiesProvider = { listOf(makeActivity("a1", 5.0)) },
        )
        val state = vm.state.first { !it.isLoading }
        assertEquals(4, state.currentTier.level)
        assertEquals(1.3, state.currentRate)
        assertEquals(8, state.daysToNext)
        assertEquals(6.5, state.comparison.first().currentCostPerUnit, 0.001)
    }

    @Test
    fun `streak 100 → tier 5 rate 1_4 daysToNext null`() = runTest {
        val vm = ExchangeRateViewModel(
            userIdProvider = { "u1" },
            streakFlow = { flowOf(StreakSummary(currentStreak = 100, longestStreak = 100, totalDaysComplete = 100, lastCompleteDate = null)) },
            wantActivitiesProvider = { emptyList() },
        )
        val state = vm.state.first { !it.isLoading }
        assertEquals(5, state.currentTier.level)
        assertEquals(1.4, state.currentRate)
        assertNull(state.daysToNext)
        assertEquals(0, state.comparison.size)
    }

    private fun makeActivity(id: String, costPerUnit: Double) = WantActivity(
        id = id, userId = "u1", name = id, unit = "u", costPerUnit = costPerUnit,
    )
}
```

> If `StreakSummary` constructor differs (e.g. different field names), adjust. The fields per `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/StreakDay.kt` are `currentStreak`, `longestStreak`, `totalDaysComplete`, plus a 4th nullable.

- [ ] **Step 2: Run test (compile fail)**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*ExchangeRateViewModel*" 2>&1 | tail -10`

Expected: unresolved reference `ExchangeRateViewModel`.

- [ ] **Step 3: Implement `ExchangeRateViewModel`**

```kotlin
package com.jktdeveloper.habitto.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.RateTier
import com.habittracker.domain.model.StreakSummary
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.usecase.ExchangeRateCalculator
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComparisonRow(
    val activityId: String,
    val name: String,
    val unit: String,
    val baseCostPerUnit: Double,
    val currentCostPerUnit: Double,
)

data class ExchangeRateState(
    val isLoading: Boolean = true,
    val currentStreak: Int = 0,
    val currentRate: Double = 1.0,
    val currentTier: RateTier = ExchangeRateCalculator.tiers.first(),
    val daysToNext: Int? = 7,
    val comparison: List<ComparisonRow> = emptyList(),
)

class ExchangeRateViewModel(
    private val userIdProvider: () -> String,
    private val streakFlow: () -> Flow<StreakSummary>,
    private val wantActivitiesProvider: suspend (String) -> List<WantActivity>,
) : ViewModel() {

    constructor(container: AppContainer) : this(
        userIdProvider = { container.currentUserId() },
        streakFlow = { container.computeStreakUseCase.observeCurrent(container.currentUserId()) },
        wantActivitiesProvider = { userId -> container.wantActivityRepository.getWantActivities(userId) },
    )

    private val _state = MutableStateFlow(ExchangeRateState())
    val state: StateFlow<ExchangeRateState> = _state.asStateFlow()

    init {
        viewModelScope.launch { observe() }
    }

    private suspend fun observe() {
        val userId = userIdProvider()
        streakFlow().collect { summary ->
            val activities = wantActivitiesProvider(userId)
            val rate = ExchangeRateCalculator.rateFor(summary.currentStreak)
            val tier = ExchangeRateCalculator.tierFor(summary.currentStreak)
            val daysToNext = ExchangeRateCalculator.daysToNextTier(summary.currentStreak)
            val comparison = activities.map { activity ->
                ComparisonRow(
                    activityId = activity.id,
                    name = activity.name,
                    unit = activity.unit,
                    baseCostPerUnit = activity.costPerUnit,
                    currentCostPerUnit = activity.costPerUnit * rate,
                )
            }
            _state.update {
                ExchangeRateState(
                    isLoading = false,
                    currentStreak = summary.currentStreak,
                    currentRate = rate,
                    currentTier = tier,
                    daysToNext = daysToNext,
                    comparison = comparison,
                )
            }
        }
    }
}
```

> Verify AppContainer property names: `rtk grep -n "computeStreakUseCase\|wantActivityRepository\|wantActivityRepo" mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`. If differ, adjust the secondary constructor.

- [ ] **Step 4: Run test**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*ExchangeRateViewModel*" 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run full android tests**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModel.kt \
    mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModelTest.kt
rtk git commit -m "feat(rate): ExchangeRateViewModel — tier ladder + comparison state"
```

---

## Task 7: `ExchangeRateScreen`

UI per canvas. No unit tests for Compose UI (manual smoke).

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateScreen.kt`

- [ ] **Step 1: Implement screen**

```kotlin
package com.jktdeveloper.habitto.ui.exchange

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.RateTier
import com.habittracker.domain.usecase.ExchangeRateCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeRateScreen(
    viewModel: ExchangeRateViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Exchange rate",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item { Hero(state) }
            item { TierLadder(state.currentTier) }
            if (state.comparison.isNotEmpty()) {
                item { ComparisonHeader() }
                items(state.comparison) { row -> ComparisonRowView(row) }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun Hero(state: ExchangeRateState) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            "TODAY",
            fontSize = 11.sp,
            letterSpacing = 0.4.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatRate(state.currentRate) + "×",
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            lineHeight = 56.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = heroSubtitle(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun heroSubtitle(state: ExchangeRateState): String {
    val daysToNext = state.daysToNext
    val nextLevel = state.currentTier.level + 1
    return if (daysToNext == null) {
        "Top tier reached."
    } else {
        val nextRate = ExchangeRateCalculator.tiers
            .firstOrNull { it.level == nextLevel }
            ?.rate
        val nextRateLabel = nextRate?.let { formatRate(it) + "×" } ?: ""
        "You're at Tier ${state.currentTier.level} of 5. $daysToNext days to $nextRateLabel."
    }
}

@Composable
private fun TierLadder(currentTier: RateTier) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ExchangeRateCalculator.tiers.reversed().forEach { tier ->
            TierRow(
                tier = tier,
                isCurrent = tier.level == currentTier.level,
                isPassed = tier.level < currentTier.level,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TierRow(tier: RateTier, isCurrent: Boolean, isPassed: Boolean) {
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val labelColor = when {
        isCurrent || isPassed -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rangeText = if (tier.maxStreak == null) "${tier.minStreak}+ days"
    else "${tier.minStreak}–${tier.maxStreak} days"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isCurrent) 2.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPassed || isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isPassed) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        tier.level.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tier ${tier.level} · ${formatRate(tier.rate)}×",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor,
                )
                Text(
                    rangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ComparisonHeader() {
    Text(
        "What it costs now",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
private fun ComparisonRowView(row: ComparisonRow) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "per ${row.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (row.baseCostPerUnit != row.currentCostPerUnit) {
                    Text(
                        formatNumber(row.baseCostPerUnit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                    Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(
                    formatNumber(row.currentCostPerUnit),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (row.currentCostPerUnit > row.baseCostPerUnit)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "pt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatRate(value: Double): String {
    val rounded = (value * 10).toInt()
    val whole = rounded / 10
    val frac = rounded % 10
    return "$whole.$frac"
}

private fun formatNumber(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        val rounded = (value * 10).toInt() / 10.0
        rounded.toString()
    }
}
```

- [ ] **Step 2: Build**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateScreen.kt
rtk git commit -m "feat(rate): ExchangeRateScreen — hero + tier ladder + comparison"
```

---

## Task 8: Wire `AppContainer` + `AppNavigation`

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Add use cases to `AppContainer`**

In `AppContainer.kt`, locate where existing use cases are constructed (`computeStreakUseCase`, `getPointBalanceUseCase`, etc). Add `getUserStreakOnDayUseCase`, then update the three integrated use cases to receive it.

Imports to add:

```kotlin
import com.habittracker.domain.usecase.GetUserStreakOnDayUseCase
```

Constructor block (insert near other use cases — adapt property names + style to existing file):

```kotlin
val getUserStreakOnDayUseCase = GetUserStreakOnDayUseCase(computeStreakUseCase)
```

Then update construction of the three existing use cases — pass `getUserStreakOnDayUseCase`:

```kotlin
val getPointBalanceUseCase = GetPointBalanceUseCase(
    habitLogRepo = habitLogRepository,
    wantLogRepo = wantLogRepository,
    habitRepo = habitRepository,
    wantActivityRepo = wantActivityRepository,
    timeZone = TimeZone.currentSystemDefault(),
    clock = Clock.System,
    getUserStreakOnDayUseCase = getUserStreakOnDayUseCase,
)

val getDayPointsUseCase = GetDayPointsUseCase(
    habitLogRepo = habitLogRepository,
    wantLogRepo = wantLogRepository,
    habitRepo = habitRepository,
    wantActivityRepo = wantActivityRepository,
    timeZone = TimeZone.currentSystemDefault(),
    getUserStreakOnDayUseCase = getUserStreakOnDayUseCase,
)

val logWantUseCase = LogWantUseCase(
    wantLogRepository = wantLogRepository,
    wantActivityRepository = wantActivityRepository,
    getPointBalanceUseCase = getPointBalanceUseCase,
    getUserStreakOnDayUseCase = getUserStreakOnDayUseCase,
)
```

(Use the file's actual property names + arg style — positional vs named. The signatures match what Tasks 3-5 introduced.)

- [ ] **Step 2: Add `Screen.ExchangeRate` route**

In `AppNavigation.kt`, after another sealed `Screen` object (e.g. `Screen.HabitForm`):

```kotlin
object ExchangeRate : Screen("exchange_rate")
```

- [ ] **Step 3: Mount the composable**

In `NavHost { ... }` after another existing composable:

```kotlin
composable(Screen.ExchangeRate.route) {
    val vm = viewModel { com.jktdeveloper.habitto.ui.exchange.ExchangeRateViewModel(container) }
    com.jktdeveloper.habitto.ui.exchange.ExchangeRateScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 4: Build**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run android tests**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "feat(nav): ExchangeRate route + AppContainer wires use cases"
```

---

## Task 9: YouHub "Earn & spend" Section

Add a new section to YouHub, "Point exchange rate" row showing current rate, tap → ExchangeRate screen.

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Inspect YouHubViewModel**

Run: `rtk grep -n "currentStreak\|computeStreakUseCase\|observeCurrent\|StreakSummary" mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubViewModel.kt`

If a streak source is already observed, reuse. Otherwise add one.

- [ ] **Step 2: Expose rate state on YouHubViewModel**

Add fields (adapt to existing class shape):

```kotlin
import com.habittracker.domain.usecase.ExchangeRateCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

val currentRate: StateFlow<Double> = container.computeStreakUseCase
    .observeCurrent(container.currentUserId())
    .map { ExchangeRateCalculator.rateFor(it.currentStreak) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)

val currentStreak: StateFlow<Int> = container.computeStreakUseCase
    .observeCurrent(container.currentUserId())
    .map { it.currentStreak }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
```

- [ ] **Step 3: Add "Earn & spend" section to `YouHubScreen`**

Add `onOpenExchangeRate: () -> Unit` parameter to `YouHubScreen`. After the existing "Tracking" section (or wherever sections end), add:

```kotlin
SectionHeader("Earn & spend")
val rate by viewModel.currentRate.collectAsState()
val streak by viewModel.currentStreak.collectAsState()
ListItem(
    modifier = Modifier.fillMaxWidth().clickable { onOpenExchangeRate() },
    leadingContent = {
        Icon(Icons.Outlined.TrendingUp, contentDescription = null)
    },
    headlineContent = { Text("Point exchange rate") },
    supportingContent = {
        val rateLabel = ((rate * 10).toInt() / 10.0).toString()
        Text("${rateLabel}× · earned by ${streak}-day streak")
    },
    trailingContent = {
        Icon(Icons.Default.ChevronRight, contentDescription = null)
    },
)
```

Add imports:
- `androidx.compose.material.icons.outlined.TrendingUp`
- `androidx.compose.material.icons.filled.ChevronRight` (if not already present)
- `androidx.compose.runtime.collectAsState`
- `androidx.compose.runtime.getValue`

(`SectionHeader` name — match existing.)

- [ ] **Step 4: Wire callback in `AppNavigation`**

Update YouHub composable:

```kotlin
com.jktdeveloper.habitto.ui.you.YouHubScreen(
    viewModel = vm,
    // ...existing params...
    onOpenExchangeRate = { navController.navigate(Screen.ExchangeRate.route) },
)
```

- [ ] **Step 5: Build**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubViewModel.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "feat(rate): YouHub Earn & spend section opens ExchangeRate"
```

---

## Task 10: Home Balance Card Tap → ExchangeRate

Make the Home "Balance · pts" card tappable; route to ExchangeRate.

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Inspect Home Balance card**

Run: `rtk grep -n "BALANCE\|Balance\|earnedToday\|spentToday" mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt | head -15`

Find the composable rendering the balance card.

- [ ] **Step 2: Add `onOpenExchangeRate: () -> Unit = {}` to HomeScreen signature**

Wire it to the Balance card via `Modifier.clickable { onOpenExchangeRate() }` on the card's outer `Surface` — or use `Card(onClick = ...)` if the existing layout uses `Card`.

- [ ] **Step 3: Wire in `AppNavigation`**

In `Screen.Home.route` composable:

```kotlin
HomeScreen(
    viewModel = vm,
    // ...existing params...
    onOpenExchangeRate = { navController.navigate(Screen.ExchangeRate.route) },
)
```

- [ ] **Step 4: Build**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run android tests**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "feat(rate): Home Balance card tap navigates to ExchangeRate"
```

---

## Task 11: Final Smoke + Verification

- [ ] **Step 1: Run full shared test suite**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run full android tests**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Build APK**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual smoke checklist**

- [ ] Fresh install + new account → onboard → log all habits → balance reflects 1.0× rate (no streak yet, day 1)
- [ ] After 7-day streak (test fixture or simulate via clock manipulation), tap Balance card → ExchangeRate screen → hero shows "1.1×" and "Tier 2 of 5"
- [ ] Tier ladder: Tier 1 shows checked, Tier 2 highlighted, Tiers 3-5 grayed
- [ ] Comparison list: each WantActivity row shows `baseCost (strikethrough) → currentCost`
- [ ] YouHub → "Earn & spend" → "Point exchange rate · 1.1× · earned by 7-day streak" tap → ExchangeRate screen
- [ ] Log a Want today → Home Balance card spent value reflects rate-applied cost
- [ ] Past Want logs (earlier in week, when streak was lower) preserve their original rate-applied cost
- [ ] 30+ day streak → hero shows "1.4×" + "Top tier reached." sub-line, no "days to next" indicator

- [ ] **Step 5: Push branch + open PR**

```bash
rtk git push -u origin feature/phase6-exchange-rate
gh pr create --title "Phase 6: Exchange Rate (stepped tier multiplier on Want spending)" \
  --body "Adds stepped 5-tier exchange rate driven by user-level streak. Wants cost more as streak grows; habit earning unchanged. Past spends use rate-at-log-day. New ExchangeRateScreen reachable from YouHub + Home Balance card. No schema migration."
```

---

## Self-Review

**Spec coverage:**
- ✅ `ExchangeRateCalculator` (Task 1)
- ✅ `RateTier` model (Task 1)
- ✅ `GetUserStreakOnDayUseCase` (Task 2)
- ✅ `LogWantUseCase` rate integration (Task 3)
- ✅ `GetPointBalanceUseCase` rate integration (Task 4)
- ✅ `GetDayPointsUseCase` rate integration (Task 5)
- ✅ `ExchangeRateViewModel` (Task 6)
- ✅ `ExchangeRateScreen` (Task 7)
- ✅ `AppContainer` + `AppNavigation` wiring (Task 8)
- ✅ YouHub entry (Task 9)
- ✅ Home Balance entry (Task 10)
- ✅ Tests for calculator, streak-on-day, log-want rate, balance rate, day-points rate, viewmodel
- ✅ Final smoke + PR (Task 11)
- ✅ Spec deferrals (tier-change notification, Home banner, per-identity, past-spend tooltip, caching, Want CRUD) explicitly out of scope

**Type consistency:**
- `rateFor(streak: Int): Double` — consistent in calculator, all use cases, VM
- `RateTier(level, rate, minStreak, maxStreak)` — consistent in calculator + VM state + screen
- `ComparisonRow(activityId, name, unit, baseCostPerUnit, currentCostPerUnit)` — defined VM-side, consumed by screen
- `GetUserStreakOnDayUseCase.execute(userId: String, date: LocalDate): Int` — consistent across consumers
- `pointsSpentWithRate(quantity, costPerUnit, rate): Int` — duplicated method in `LogWantUseCase`, `GetPointBalanceUseCase`, `GetDayPointsUseCase` — same logic on purpose (small duplication beats a new shared util at this scope)

**Risk:**
- Tasks 4/5 add `GetUserStreakOnDayUseCase?` as nullable parameter to preserve back-compat for any pre-Phase-6 test fakes. Default `null` keeps them with rate=1.0.
- `ComputeStreakUseCase.computeNow(userId, range)` over a 365-day window may be slow for users with very long histories. Acceptable for v1; cache later.
