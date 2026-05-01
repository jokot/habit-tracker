# Phase 5c-1: Identity Management Screens (read-only) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ship read-only multi-identity management — IdentityList + IdentityDetail screens with real per-identity stats, plus YouHub identity card upgrade and tap wiring from Home identity strip / YouHub card. No write actions.

**Architecture:** No schema or sync changes. Two new domain use cases compute per-identity stats from existing logs. Two new screens wired via new `IdentityList` / `IdentityDetail` Screen routes. Existing 5b inert components (`IdentityChip`, `IdentityStrip`, `IdentityHubCard`) gain `onClick` callbacks; `IdentityHubCard` body replaced with canvas-style rich card.

**Tech Stack:** Kotlin Multiplatform, SQLDelight (no schema change), Compose Material 3, Robolectric for Android tests, kotlin.test for common tests.

**Spec:** `docs/superpowers/specs/2026-04-30-phase5c-identity-management-design.md`

**Worktree:** `.worktrees/phase5c-identity-management`
**Branch:** `feature/phase5c-identity-management` (already checked out off latest main)

---

## File Structure

### Created

| File | Responsibility |
|---|---|
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/IdentityStats.kt` | `IdentityStats` + `IdentityWithStats` |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCase.kt` | Per-identity stats compute |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ObserveUserIdentitiesWithStatsUseCase.kt` | List screen aggregate |
| `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCaseTest.kt` | |
| `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ObserveUserIdentitiesWithStatsUseCaseTest.kt` | |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListViewModel.kt` | |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListScreen.kt` | |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModel.kt` | |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailScreen.kt` | |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentitySparkline.kt` | 14-cell sparkline used on IdentityList |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHeatGrid.kt` | 90-cell grid used on IdentityDetail |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListViewModelTest.kt` | |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModelTest.kt` | |

### Modified

| File | What changes |
|---|---|
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` | Wire 2 new use cases |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` | Add `IdentityList` + `IdentityDetail` to `Screen` sealed class + `NavHost` composables |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityChip.kt` | Add `onClick: () -> Unit` |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityStrip.kt` | Add `onChipClick: (Identity) -> Unit` + `onMoreClick: () -> Unit` |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHubCard.kt` | Replace stub body with canvas-style rich card; add `onClick: () -> Unit` |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt` | Pass nav callbacks into `IdentityStrip` |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt` | Pass nav callback into `IdentityHubCard` |

---

## Conventions

- **Working dir:** `/Users/jokot/dev/habit-tracker/.worktrees/phase5c-identity-management`. All bash commands assume this `cwd` (or absolute paths).
- **Build commands:** `rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid` for shared compile; `rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid` for app compile; `rtk ./gradlew :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest` for tests; `rtk ./gradlew :mobile:androidApp:assembleDebug` for full APK.
- **Commit per task** for atomic history.
- **Test pattern:** common tests use Fakes (no in-memory SQLDelight in this codebase). Android tests use Robolectric. Match existing fixture style — see `ComputeStreakUseCaseTest.kt` and `StreakHistoryViewModelTest.kt` for the templates.
- **Imports:** plan only shows new imports. Engineer adds others as needed.

---

## Tasks

### Task 1: Domain model — IdentityStats + IdentityWithStats

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/IdentityStats.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.habittracker.domain.model

data class IdentityStats(
    val identityId: String,
    val currentStreak: Int,
    val daysActive: Int,
    val habitCount: Int,
    val last14Heat: List<Int>,
    val last90Heat: List<Int>,
) {
    init {
        require(last14Heat.size == 14) { "last14Heat must have 14 entries" }
        require(last90Heat.size == 90) { "last90Heat must have 90 entries" }
    }

    companion object {
        val Empty = IdentityStats(
            identityId = "",
            currentStreak = 0,
            daysActive = 0,
            habitCount = 0,
            last14Heat = List(14) { 0 },
            last90Heat = List(90) { 0 },
        )
        fun emptyFor(identityId: String) = Empty.copy(identityId = identityId)
    }
}

data class IdentityWithStats(
    val identity: Identity,
    val stats: IdentityStats,
)
```

- [ ] **Step 2: Build (shared module)**

```bash
rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid 2>&1 | tail -3
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/IdentityStats.kt
rtk git commit -m 'feat(model): IdentityStats + IdentityWithStats'
```

---

### Task 2: ComputeIdentityStatsUseCase + tests

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

class ComputeIdentityStatsUseCaseTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 5, 1)
    private val userId = "u1"
    private val identityId = "ident-1"
    private val seedIdentity = Identity(identityId, "Reader", "", "")

    @Test
    fun zeroHabitsReturnsEmpty() = runTest {
        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        // No habits linked
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(emptyList()),
            identityRepo = repo,
            timeZone = tz,
            clock = FixedClock(today),
        )
        val out = sut.computeNow(userId, identityId)
        assertEquals(0, out.habitCount)
        assertEquals(0, out.currentStreak)
        assertEquals(0, out.daysActive)
        assertEquals(List(14) { 0 }, out.last14Heat)
        assertEquals(List(90) { 0 }, out.last90Heat)
    }

    @Test
    fun singleHabitLoggedTodayYieldsStreakOne() = runTest {
        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        val habit = makeHabit("h1")
        repo.seedHabit(habit)
        repo.linkHabitToIdentities(habit.id, setOf(identityId))
        val logs = listOf(makeLog("h1", today))
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(logs),
            identityRepo = repo,
            timeZone = tz,
            clock = FixedClock(today),
        )
        val out = sut.computeNow(userId, identityId)
        assertEquals(1, out.habitCount)
        assertEquals(1, out.currentStreak)
        assertEquals(1, out.daysActive)
        assertEquals(4, out.last14Heat.last())  // ratio 1.0 → bucket 4
    }

    @Test
    fun twoHabitsPartialDayDoesNotIncrementStreak() = runTest {
        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        val h1 = makeHabit("h1")
        val h2 = makeHabit("h2")
        repo.seedHabit(h1); repo.seedHabit(h2)
        repo.linkHabitToIdentities(h1.id, setOf(identityId))
        repo.linkHabitToIdentities(h2.id, setOf(identityId))
        // Only h1 logged today
        val logs = listOf(makeLog("h1", today))
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(logs),
            identityRepo = repo,
            timeZone = tz,
            clock = FixedClock(today),
        )
        val out = sut.computeNow(userId, identityId)
        assertEquals(2, out.habitCount)
        assertEquals(0, out.currentStreak)        // not all logged
        assertEquals(1, out.daysActive)            // ≥1 logged
    }

    @Test
    fun heatBucketBoundaries() = runTest {
        // 4 habits linked. Day with ratio 0.50 → bucket 2.
        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        val habits = (1..4).map { makeHabit("h$it") }
        habits.forEach {
            repo.seedHabit(it)
            repo.linkHabitToIdentities(it.id, setOf(identityId))
        }
        // Log h1 + h2 today (each meets dailyTarget=1) → 2 of 4 targets met → ratio 0.5
        val logs = listOf(makeLog("h1", today), makeLog("h2", today))
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(logs),
            identityRepo = repo,
            timeZone = tz,
            clock = FixedClock(today),
        )
        val out = sut.computeNow(userId, identityId)
        assertEquals(2, out.last14Heat.last())
    }

    @Test
    fun last14And90LengthsExact() = runTest {
        val repo = FakeIdentityRepository(seed = listOf(seedIdentity))
        val sut = ComputeIdentityStatsUseCase(
            habitLogRepo = AllLogsRepo(emptyList()),
            identityRepo = repo,
            timeZone = tz,
            clock = FixedClock(today),
        )
        val out = sut.computeNow(userId, identityId)
        assertEquals(14, out.last14Heat.size)
        assertEquals(90, out.last90Heat.size)
    }

    // --- helpers ---

    private fun makeHabit(id: String, dailyTarget: Int = 1, threshold: Double = 1.0) =
        Habit(
            id = id, userId = userId, templateId = "t-$id", name = id, unit = "x",
            thresholdPerPoint = threshold, dailyTarget = dailyTarget,
            createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
        )

    private fun makeLog(habitId: String, date: LocalDate, qty: Double = 1.0) = HabitLog(
        id = "log-$habitId-$date",
        userId = userId,
        habitId = habitId,
        quantity = qty,
        loggedAt = LocalDateTime(date, LocalTime(12, 0)).toInstant(tz),
        deletedAt = null,
        syncedAt = null,
    )
}

private class FixedClock(private val date: LocalDate) : Clock {
    override fun now(): Instant =
        LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.UTC)
}

/** HabitLogRepository fake exposing only the methods this use case calls. */
private class AllLogsRepo(private val logs: List<HabitLog>) : HabitLogRepository {
    override fun observeActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Flow<List<HabitLog>> = flowOf(emptyList())
    override suspend fun countActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Int = 0
    override suspend fun firstActiveLogAt(userId: String): Instant? = null
    override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant) = error("unused")
    override suspend fun softDelete(logId: String, userId: String) = error("unused")
    override fun observeActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
    override suspend fun getActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
    override fun observeAllActiveLogsForUser(userId: String): Flow<List<HabitLog>> = flowOf(logs.filter { it.userId == userId && it.deletedAt == null })
    override suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog> = logs.filter { it.userId == userId && it.deletedAt == null }
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulled(row: HabitLog) = error("unused")
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*ComputeIdentityStatsUseCaseTest*" 2>&1 | tail -10
```
Expected: compile failure on `ComputeIdentityStatsUseCase`.

- [ ] **Step 3: Implement the use case**

Create `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.IdentityStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class ComputeIdentityStatsUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val identityRepo: IdentityRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun observe(userId: String, identityId: String): Flow<IdentityStats> {
        val habitsFlow = identityRepo.observeHabitsForIdentity(userId, identityId)
        val logsFlow = habitLogRepo.observeAllActiveLogsForUser(userId)
        return combine(habitsFlow, logsFlow) { habits, logs -> compute(identityId, habits, logs) }
    }

    suspend fun computeNow(userId: String, identityId: String): IdentityStats {
        val habits = identityRepo.observeHabitsForIdentity(userId, identityId).first()
        val logs = habitLogRepo.getAllActiveLogsForUser(userId)
        return compute(identityId, habits, logs)
    }

    private fun compute(identityId: String, habits: List<Habit>, allLogs: List<HabitLog>): IdentityStats {
        if (habits.isEmpty()) return IdentityStats.emptyFor(identityId)
        val today = clock.now().toLocalDateTime(timeZone).date
        val habitIds = habits.map { it.id }.toSet()
        val logsForIdentity = allLogs.filter { it.habitId in habitIds }
        val targetSum = habits.sumOf { it.dailyTarget }.coerceAtLeast(1)

        // Per-day distinct habit ids logged + per-day points (capped per habit at dailyTarget)
        val pointsByDay = mutableMapOf<LocalDate, Int>()
        val loggedHabitsByDay = mutableMapOf<LocalDate, MutableSet<String>>()
        // Also accumulate per-(day,habit) raw points so we can cap per habit
        val rawByDayHabit = mutableMapOf<Pair<LocalDate, String>, Int>()
        logsForIdentity.forEach { log ->
            val date = log.loggedAt.toLocalDateTime(timeZone).date
            val pts = PointCalculator.pointsEarned(log.quantity, habits.first { it.id == log.habitId }.thresholdPerPoint)
            val key = date to log.habitId
            rawByDayHabit[key] = (rawByDayHabit[key] ?: 0) + pts
            loggedHabitsByDay.getOrPut(date) { mutableSetOf() }.add(log.habitId)
        }
        rawByDayHabit.forEach { (key, pts) ->
            val (date, habitId) = key
            val target = habits.first { it.id == habitId }.dailyTarget
            pointsByDay[date] = (pointsByDay[date] ?: 0) + pts.coerceAtMost(target)
        }

        // Streak: consecutive days where ALL habits logged, ending today (or yesterday if today incomplete).
        val streak = computeStreak(today, habits, loggedHabitsByDay)
        val daysActive = loggedHabitsByDay.keys.count { it <= today }
        val last14 = buildHeatList(today, 14, pointsByDay, targetSum)
        val last90 = buildHeatList(today, 90, pointsByDay, targetSum)

        return IdentityStats(
            identityId = identityId,
            currentStreak = streak,
            daysActive = daysActive,
            habitCount = habits.size,
            last14Heat = last14,
            last90Heat = last90,
        )
    }

    private fun computeStreak(today: LocalDate, habits: List<Habit>, loggedHabitsByDay: Map<LocalDate, Set<String>>): Int {
        val habitIds = habits.map { it.id }.toSet()
        val isComplete: (LocalDate) -> Boolean = { d ->
            val logged = loggedHabitsByDay[d].orEmpty()
            habitIds.all { it in logged }
        }
        // Walk back from today (or yesterday if today incomplete)
        var run = 0
        var cursor = today
        // Today partially logged → don't reset; start from yesterday
        if (!isComplete(cursor)) cursor = cursor.minus(1, DateTimeUnit.DAY)
        while (isComplete(cursor)) {
            run++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        return run
    }

    private fun buildHeatList(today: LocalDate, length: Int, pointsByDay: Map<LocalDate, Int>, targetSum: Int): List<Int> {
        val list = ArrayList<Int>(length)
        var cursor = today.minus(length - 1, DateTimeUnit.DAY)
        while (list.size < length) {
            val pts = pointsByDay[cursor] ?: 0
            val ratio = pts.toDouble() / targetSum
            list += heatBucket(ratio)
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return list
    }

    private fun heatBucket(ratio: Double): Int = when {
        ratio <= 0.0 -> 0
        ratio <= 0.25 -> 1
        ratio <= 0.5 -> 2
        ratio <= 0.75 -> 3
        else -> 4
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*ComputeIdentityStatsUseCaseTest*" 2>&1 | tail -10
```
Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCaseTest.kt
rtk git commit -m 'feat(usecase): ComputeIdentityStatsUseCase'
```

---

### Task 3: ObserveUserIdentitiesWithStatsUseCase + tests

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ObserveUserIdentitiesWithStatsUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ObserveUserIdentitiesWithStatsUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveUserIdentitiesWithStatsUseCaseTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 5, 1)
    private val userId = "u1"

    @Test
    fun emptyUserIdentitiesEmitsEmpty() = runTest {
        val repo = FakeIdentityRepository(seed = listOf(Identity("a", "A", "", "")))
        val stats = ComputeIdentityStatsUseCase(EmptyLogRepo(), repo, tz, FixedClock(today))
        val sut = ObserveUserIdentitiesWithStatsUseCase(repo, stats)
        assertEquals(emptyList(), sut.execute(userId).first())
    }

    @Test
    fun emitsOneEntryPerUserIdentityWithStats() = runTest {
        val seedA = Identity("a", "A", "", "")
        val seedB = Identity("b", "B", "", "")
        val repo = FakeIdentityRepository(seed = listOf(seedA, seedB))
        repo.setUserIdentities(userId, setOf("a", "b"))
        val stats = ComputeIdentityStatsUseCase(EmptyLogRepo(), repo, tz, FixedClock(today))
        val sut = ObserveUserIdentitiesWithStatsUseCase(repo, stats)
        val out = sut.execute(userId).first()
        assertEquals(2, out.size)
        assertEquals(setOf("a", "b"), out.map { it.identity.id }.toSet())
    }

    private class FixedClock(private val date: LocalDate) : Clock {
        override fun now(): Instant =
            LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.UTC)
    }

    private class EmptyLogRepo : HabitLogRepository {
        override fun observeActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Flow<List<HabitLog>> = flowOf(emptyList())
        override suspend fun countActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Int = 0
        override suspend fun firstActiveLogAt(userId: String): Instant? = null
        override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant) = error("unused")
        override suspend fun softDelete(logId: String, userId: String) = error("unused")
        override fun observeActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
        override suspend fun getActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) = error("unused")
        override fun observeAllActiveLogsForUser(userId: String): Flow<List<HabitLog>> = flowOf(emptyList())
        override suspend fun getAllActiveLogsForUser(userId: String): List<HabitLog> = emptyList()
        override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
        override suspend fun clearForUser(userId: String) = error("unused")
        override suspend fun getUnsyncedFor(userId: String) = error("unused")
        override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
        override suspend fun mergePulled(row: HabitLog) = error("unused")
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*ObserveUserIdentitiesWithStatsUseCaseTest*" 2>&1 | tail -10
```

- [ ] **Step 3: Implement**

Create `ObserveUserIdentitiesWithStatsUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.IdentityWithStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveUserIdentitiesWithStatsUseCase(
    private val identityRepo: IdentityRepository,
    private val statsUseCase: ComputeIdentityStatsUseCase,
) {
    fun execute(userId: String): Flow<List<IdentityWithStats>> =
        identityRepo.observeUserIdentities(userId).map { identities ->
            identities.map { identity ->
                IdentityWithStats(
                    identity = identity,
                    stats = statsUseCase.computeNow(userId, identity.id),
                )
            }
        }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*ObserveUserIdentitiesWithStatsUseCaseTest*" 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ObserveUserIdentitiesWithStatsUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ObserveUserIdentitiesWithStatsUseCaseTest.kt
rtk git commit -m 'feat(usecase): ObserveUserIdentitiesWithStatsUseCase'
```

---

### Task 4: AppContainer wiring

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`

- [ ] **Step 1: Add imports**

Find the `import com.habittracker.domain.usecase.*` block. Add:
```kotlin
import com.habittracker.domain.usecase.ComputeIdentityStatsUseCase
import com.habittracker.domain.usecase.ObserveUserIdentitiesWithStatsUseCase
```

- [ ] **Step 2: Wire use cases**

Find where existing use cases are declared (around the `getUserIdentitiesUseCase = ...` block from 5b). Add:

```kotlin
    val computeIdentityStatsUseCase = ComputeIdentityStatsUseCase(
        habitLogRepo = habitLogRepository,
        identityRepo = identityRepository,
    )
    val observeUserIdentitiesWithStatsUseCase = ObserveUserIdentitiesWithStatsUseCase(
        identityRepo = identityRepository,
        statsUseCase = computeIdentityStatsUseCase,
    )
```

- [ ] **Step 3: Build**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt
rtk git commit -m 'feat(container): wire identity stats use cases'
```

---

### Task 5: Screen routes + AppNavigation composables (placeholders)

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Extend Screen sealed class**

Find the existing `sealed class Screen(val route: String) { ... }` block. Replace with:

```kotlin
sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Settings : Screen("settings")
    object StreakHistory : Screen("streak-history")
    object You : Screen("you")
    object IdentityList : Screen("identity_list")
    object IdentityDetail : Screen("identity_detail/{identityId}") {
        const val ARG_ID = "identityId"
        fun route(id: String) = "identity_detail/$id"
    }
}
```

- [ ] **Step 2: Add NavHost composables (placeholder bodies for now)**

In the `NavHost` block, after the existing `composable(Screen.You.route) { ... }` (or wherever the You composable ends), add:

```kotlin
            composable(Screen.IdentityList.route) {
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.identity.IdentityListViewModel(container)
                }
                com.jktdeveloper.habitto.ui.identity.IdentityListScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onIdentityClick = { id -> navController.navigate(Screen.IdentityDetail.route(id)) },
                )
            }

            composable(
                route = Screen.IdentityDetail.route,
                arguments = listOf(
                    androidx.navigation.navArgument(Screen.IdentityDetail.ARG_ID) {
                        type = androidx.navigation.NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString(Screen.IdentityDetail.ARG_ID) ?: return@composable
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.identity.IdentityDetailViewModel(container, id)
                }
                com.jktdeveloper.habitto.ui.identity.IdentityDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
```

- [ ] **Step 3: Skip build for now (ViewModels + Screens defined in later tasks)**

The build will fail referencing missing classes. That's expected — Tasks 7–10 add them. Don't commit yet; combine with the next compilable state.

Actually, to keep history clean: stub the new composables with placeholders. Replace bodies temporarily:

```kotlin
            composable(Screen.IdentityList.route) {
                androidx.compose.material3.Text("IdentityList placeholder")
            }
            composable(
                route = Screen.IdentityDetail.route,
                arguments = listOf(
                    androidx.navigation.navArgument(Screen.IdentityDetail.ARG_ID) {
                        type = androidx.navigation.NavType.StringType
                    },
                ),
            ) {
                androidx.compose.material3.Text("IdentityDetail placeholder")
            }
```

Build:
```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m 'feat(nav): IdentityList + IdentityDetail routes (placeholders)'
```

---

### Task 6: Wire IdentityChip + IdentityStrip + IdentityHubCard click callbacks

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityChip.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityStrip.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt`

(IdentityHubCard upgrade is bigger — Task 12. Here we only add a tap callback to the existing stub so it compiles end-to-end; full visual upgrade lands in Task 12.)

- [ ] **Step 1: IdentityChip — add onClick**

Replace `IdentityChip` composable signature + body. Add `clickable` modifier:

```kotlin
@Composable
fun IdentityChip(identity: Identity, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HabitGlyph(
                icon = identityIcon(identity.name),
                hue = IdentityHue.forIdentityId(identity.name.lowercase()),
                size = 22.dp,
            )
            Text(
                text = identity.name.split(" ").first(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
```

Add import: `import androidx.compose.foundation.clickable`.

For `IdentityMorePill`, add `onClick`:

```kotlin
@Composable
fun IdentityMorePill(extraCount: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = "+$extraCount more",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 2: IdentityStrip — add onChipClick + onMoreClick**

Replace the `IdentityStrip` composable:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentityStrip(
    identities: List<Identity>,
    onChipClick: (Identity) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (identities.isEmpty()) return
    val visible = identities.take(3)
    val extra = identities.size - visible.size
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "I AM",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 4.dp)
                .align(Alignment.CenterVertically),
            letterSpacing = 0.3.sp,
        )
        visible.forEach { identity -> IdentityChip(identity, onClick = { onChipClick(identity) }) }
        if (extra > 0) IdentityMorePill(extra, onClick = onMoreClick)
    }
}
```

- [ ] **Step 3: HomeScreen — pass callbacks**

Find the `IdentityStrip(identities = identities)` invocation in HomeScreen. The screen needs `onIdentityClick: (String) -> Unit` and `onIdentitiesClick: () -> Unit` parameters threaded from AppNavigation. First, add to HomeScreen signature:

```kotlin
fun HomeScreen(
    viewModel: HomeViewModel,
    onSignIn: () -> Unit,
    onOpenStreakHistory: () -> Unit,
    onIdentityClick: (String) -> Unit,
    onIdentitiesClick: () -> Unit,
)
```

Update the IdentityStrip call:
```kotlin
IdentityStrip(
    identities = identities,
    onChipClick = { onIdentityClick(it.id) },
    onMoreClick = onIdentitiesClick,
)
```

In `AppNavigation.kt` find the `composable(Screen.Home.route)` block and update the `HomeScreen(...)` call:

```kotlin
HomeScreen(
    viewModel = vm,
    onSignIn = { navController.navigate(Screen.Auth.route) },
    onOpenStreakHistory = { navController.navigate(Screen.StreakHistory.route) },
    onIdentityClick = { id -> navController.navigate(Screen.IdentityDetail.route(id)) },
    onIdentitiesClick = { navController.navigate(Screen.IdentityList.route) },
)
```

- [ ] **Step 4: IdentityHubCard — add onClick (full upgrade in Task 12)**

In `IdentityHubCard.kt`, find the existing `IdentityHubCard(identities: List<Identity>)` composable. Update the signature:

```kotlin
@Composable
fun IdentityHubCard(identities: List<Identity>, onClick: () -> Unit) {
```

Wrap the outer `Surface` with `Modifier.clickable(onClick = onClick)` (or pass a clickable modifier). Add import `androidx.compose.foundation.clickable`.

- [ ] **Step 5: YouHubScreen — pass callback**

Find `YouHubScreen` signature. Add `onOpenIdentities: () -> Unit` parameter. Find the `IdentityHubCard(identities = identities)` call site, replace with:
```kotlin
IdentityHubCard(identities = identities, onClick = onOpenIdentities)
```

In `AppNavigation.kt`, find `composable(Screen.You.route)` block. Update the `YouHubScreen(...)` call to pass `onOpenIdentities = { navController.navigate(Screen.IdentityList.route) }`.

- [ ] **Step 6: Build**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityChip.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityStrip.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHubCard.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m 'feat(ui): wire identity strip + hub card click callbacks'
```

---

### Task 7: IdentityListViewModel + tests

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListViewModel.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jktdeveloper.habitto.ui.identity

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.ComputeIdentityStatsUseCase
import com.habittracker.domain.usecase.ObserveUserIdentitiesWithStatsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityListViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(testDispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun emitsLoadedListAfterCollect() = runTest(testDispatcher) {
        val repo = FakeIdentityRepository(seed = listOf(Identity("a", "A", "", "")))
        repo.setUserIdentities("u1", setOf("a"))
        val statsUc = ComputeIdentityStatsUseCase(EmptyLogRepoForVm(), repo)
        val aggregate = ObserveUserIdentitiesWithStatsUseCase(repo, statsUc)
        // Construct VM via test-friendly constructor (see impl)
        val vm = IdentityListViewModel.forTest(aggregate, userIdProvider = { "u1" })
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(1, (state as? IdentityListState.Loaded)?.items?.size)
    }
}
```

The fake `EmptyLogRepoForVm` is in this same file:

```kotlin
private class EmptyLogRepoForVm : com.habittracker.data.repository.HabitLogRepository {
    override fun observeActiveLogsBetween(userId: String, startInclusive: kotlinx.datetime.Instant, endExclusive: kotlinx.datetime.Instant) = kotlinx.coroutines.flow.flowOf<List<com.habittracker.domain.model.HabitLog>>(emptyList())
    override suspend fun countActiveLogsBetween(userId: String, startInclusive: kotlinx.datetime.Instant, endExclusive: kotlinx.datetime.Instant) = 0
    override suspend fun firstActiveLogAt(userId: String): kotlinx.datetime.Instant? = null
    override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: kotlinx.datetime.Instant) = error("unused")
    override suspend fun softDelete(logId: String, userId: String) = error("unused")
    override fun observeActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: kotlinx.datetime.Instant, dayEnd: kotlinx.datetime.Instant) = error("unused")
    override suspend fun getActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: kotlinx.datetime.Instant, dayEnd: kotlinx.datetime.Instant) = error("unused")
    override fun observeAllActiveLogsForUser(userId: String) = kotlinx.coroutines.flow.flowOf<List<com.habittracker.domain.model.HabitLog>>(emptyList())
    override suspend fun getAllActiveLogsForUser(userId: String) = emptyList<com.habittracker.domain.model.HabitLog>()
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: kotlinx.datetime.Instant) = error("unused")
    override suspend fun mergePulled(row: com.habittracker.domain.model.HabitLog) = error("unused")
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*IdentityListViewModelTest*" 2>&1 | tail -10
```

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.jktdeveloper.habitto.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.IdentityWithStats
import com.habittracker.domain.usecase.ObserveUserIdentitiesWithStatsUseCase
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class IdentityListState {
    object Loading : IdentityListState()
    data class Loaded(val items: List<IdentityWithStats>) : IdentityListState()
}

class IdentityListViewModel private constructor(
    private val aggregate: ObserveUserIdentitiesWithStatsUseCase,
    private val userIdProvider: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow<IdentityListState>(IdentityListState.Loading)
    val state: StateFlow<IdentityListState> = _state.asStateFlow()

    private var job: Job? = null

    constructor(container: AppContainer) : this(
        aggregate = container.observeUserIdentitiesWithStatsUseCase,
        userIdProvider = { container.currentUserId() },
    )

    init { refresh() }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            aggregate.execute(userIdProvider()).collect { items ->
                _state.value = IdentityListState.Loaded(items)
            }
        }
    }

    companion object {
        /** Test entry — bypasses AppContainer. */
        fun forTest(
            aggregate: ObserveUserIdentitiesWithStatsUseCase,
            userIdProvider: () -> String,
        ) = IdentityListViewModel(aggregate, userIdProvider)
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*IdentityListViewModelTest*" 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListViewModel.kt \
            mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListViewModelTest.kt
rtk git commit -m 'feat(identity): IdentityListViewModel'
```

---

### Task 8: IdentityListScreen + sparkline component

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentitySparkline.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListScreen.kt`

- [ ] **Step 1: IdentitySparkline component**

```kotlin
package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.theme.HeatL0
import com.jktdeveloper.habitto.ui.theme.HeatL1
import com.jktdeveloper.habitto.ui.theme.HeatL2
import com.jktdeveloper.habitto.ui.theme.HeatL3
import com.jktdeveloper.habitto.ui.theme.HeatL4

@Composable
fun IdentitySparkline(heat: List<Int>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(24.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
        heat.forEach { level ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .androidx.compose.ui.unit.dp.let { Modifier }
                    .androidx.compose.foundation.layout.weight(1f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(heatColor(level)),
            )
        }
    }
}

private fun heatColor(level: Int) = when (level) {
    1 -> HeatL1
    2 -> HeatL2
    3 -> HeatL3
    4 -> HeatL4
    else -> HeatL0
}
```

NOTE: That `Modifier.androidx...` pattern won't compile. Replace with the correct idiom — the goal is each cell has `Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(3.dp)).background(heatColor(level))`. To use `weight` you need to be inside a `RowScope`. The Row block already provides that. Rewrite:

```kotlin
package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.theme.HeatL0
import com.jktdeveloper.habitto.ui.theme.HeatL1
import com.jktdeveloper.habitto.ui.theme.HeatL2
import com.jktdeveloper.habitto.ui.theme.HeatL3
import com.jktdeveloper.habitto.ui.theme.HeatL4

@Composable
fun IdentitySparkline(heat: List<Int>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        heat.forEach { level ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(heatColor(level)),
            )
        }
    }
}

private fun heatColor(level: Int) = when (level) {
    1 -> HeatL1
    2 -> HeatL2
    3 -> HeatL3
    4 -> HeatL4
    else -> HeatL0
}
```

- [ ] **Step 2: IdentityListScreen**

```kotlin
package com.jktdeveloper.habitto.ui.identity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.IdentityWithStats
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.IdentitySparkline
import com.jktdeveloper.habitto.ui.components.identityIcon
import com.jktdeveloper.habitto.ui.theme.FlameOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityListScreen(
    viewModel: IdentityListViewModel,
    onBack: () -> Unit,
    onIdentityClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identities", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
    ) { padding ->
        val items = (state as? IdentityListState.Loaded)?.items.orEmpty()
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = "You're committing to ${items.size} ${if (items.size == 1) "identity" else "identities"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                )
            }
            items(items, key = { it.identity.id }) { iws ->
                IdentityCard(iws, onClick = { onIdentityClick(iws.identity.id) })
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun IdentityCard(iws: IdentityWithStats, onClick: () -> Unit) {
    val identity = iws.identity
    val stats = iws.stats
    val hue = IdentityHue.forIdentityId(identity.name.lowercase())
    val isDark = isSystemInDarkTheme()
    val gradientStart = if (isDark) Color.hsl(hue, 0.30f, 0.18f) else Color.hsl(hue, 0.30f, 0.92f)
    val gradientEnd = MaterialTheme.colorScheme.surface

    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colorStops = arrayOf(0f to gradientStart, 0.75f to gradientEnd),
                ),
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                HabitGlyph(
                    icon = identityIcon(identity.name),
                    hue = hue,
                    size = 48.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(identity.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        identity.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    StatsRow(stats.currentStreak, stats.habitCount, stats.daysActive)
                    Spacer(Modifier.height(12.dp))
                    IdentitySparkline(stats.last14Heat)
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsRow(streak: Int, habitCount: Int, daysActive: Int) {
    val divider = MaterialTheme.colorScheme.outlineVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(14.dp))
            Text(streak.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("day streak", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.width(1.dp).height(12.dp).background(divider))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(habitCount.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(if (habitCount == 1) "habit" else "habits", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.width(1.dp).height(12.dp).background(divider))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(daysActive.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("days as", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

- [ ] **Step 3: Swap placeholder in AppNavigation for real screen**

In `AppNavigation.kt`, replace the placeholder for `Screen.IdentityList.route`:

```kotlin
composable(Screen.IdentityList.route) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel {
        com.jktdeveloper.habitto.ui.identity.IdentityListViewModel(container)
    }
    com.jktdeveloper.habitto.ui.identity.IdentityListScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        onIdentityClick = { id -> navController.navigate(Screen.IdentityDetail.route(id)) },
    )
}
```

- [ ] **Step 4: Build**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentitySparkline.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListScreen.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m 'feat(identity): IdentityListScreen with rich cards and sparkline'
```

---

### Task 9: IdentityDetailViewModel + tests

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModel.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModelTest.kt`

- [ ] **Step 1: Test**

```kotlin
package com.jktdeveloper.habitto.ui.identity

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.ComputeIdentityStatsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdentityDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(testDispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun loadedStateForKnownIdentity() = runTest(testDispatcher) {
        val repo = FakeIdentityRepository(seed = listOf(Identity("a", "A", "", "")))
        repo.setUserIdentities("u1", setOf("a"))
        val statsUc = ComputeIdentityStatsUseCase(EmptyLogRepoForVm(), repo)
        val vm = IdentityDetailViewModel.forTest(
            identityRepo = repo,
            statsUseCase = statsUc,
            userIdProvider = { "u1" },
            identityId = "a",
        )
        advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state is IdentityDetailState.Loaded)
        assertEquals("a", (state as IdentityDetailState.Loaded).identity.id)
    }

    @Test fun unknownIdentityYieldsNotFound() = runTest(testDispatcher) {
        val repo = FakeIdentityRepository(seed = listOf(Identity("a", "A", "", "")))
        // No user identities — id "z" not in user's set
        val statsUc = ComputeIdentityStatsUseCase(EmptyLogRepoForVm(), repo)
        val vm = IdentityDetailViewModel.forTest(
            identityRepo = repo,
            statsUseCase = statsUc,
            userIdProvider = { "u1" },
            identityId = "z",
        )
        advanceUntilIdle()
        assertTrue(vm.state.value is IdentityDetailState.NotFound)
    }
}
```

(Reuse `EmptyLogRepoForVm` from Task 7's test file by copy-paste — keep tests self-contained and independent.)

- [ ] **Step 2: Run — expect compile failure**

```bash
rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*IdentityDetailViewModelTest*" 2>&1 | tail -10
```

- [ ] **Step 3: Implement**

```kotlin
package com.jktdeveloper.habitto.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.IdentityStats
import com.habittracker.domain.usecase.ComputeIdentityStatsUseCase
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class IdentityDetailState {
    object Loading : IdentityDetailState()
    data class Loaded(
        val identity: Identity,
        val stats: IdentityStats,
        val habits: List<Habit>,
    ) : IdentityDetailState()
    object NotFound : IdentityDetailState()
}

class IdentityDetailViewModel private constructor(
    private val identityRepo: IdentityRepository,
    private val statsUseCase: ComputeIdentityStatsUseCase,
    private val userIdProvider: () -> String,
    private val identityId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<IdentityDetailState>(IdentityDetailState.Loading)
    val state: StateFlow<IdentityDetailState> = _state.asStateFlow()

    private var job: Job? = null

    constructor(container: AppContainer, identityId: String) : this(
        identityRepo = container.identityRepository,
        statsUseCase = container.computeIdentityStatsUseCase,
        userIdProvider = { container.currentUserId() },
        identityId = identityId,
    )

    init { refresh() }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val userId = userIdProvider()
            val userIdentities = identityRepo.observeUserIdentities(userId).first()
            val identity = userIdentities.firstOrNull { it.id == identityId }
            if (identity == null) {
                _state.value = IdentityDetailState.NotFound
                return@launch
            }
            val stats = statsUseCase.computeNow(userId, identityId)
            val habits = identityRepo.observeHabitsForIdentity(userId, identityId).first()
            _state.value = IdentityDetailState.Loaded(identity, stats, habits)
        }
    }

    companion object {
        fun forTest(
            identityRepo: IdentityRepository,
            statsUseCase: ComputeIdentityStatsUseCase,
            userIdProvider: () -> String,
            identityId: String,
        ) = IdentityDetailViewModel(identityRepo, statsUseCase, userIdProvider, identityId)
    }
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*IdentityDetailViewModelTest*" 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModel.kt \
            mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModelTest.kt
rtk git commit -m 'feat(identity): IdentityDetailViewModel'
```

---

### Task 10: IdentityHeatGrid component + IdentityDetailScreen

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHeatGrid.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailScreen.kt`

- [ ] **Step 1: IdentityHeatGrid**

```kotlin
package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.theme.HeatL0
import com.jktdeveloper.habitto.ui.theme.HeatL1
import com.jktdeveloper.habitto.ui.theme.HeatL2
import com.jktdeveloper.habitto.ui.theme.HeatL3
import com.jktdeveloper.habitto.ui.theme.HeatL4

@Composable
fun IdentityHeatGrid(heat: List<Int>, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(15),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        userScrollEnabled = false,
    ) {
        items(heat) { level ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(heatColor(level)),
            )
        }
    }
}

private fun heatColor(level: Int) = when (level) {
    1 -> HeatL1
    2 -> HeatL2
    3 -> HeatL3
    4 -> HeatL4
    else -> HeatL0
}
```

- [ ] **Step 2: IdentityDetailScreen**

```kotlin
package com.jktdeveloper.habitto.ui.identity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHeatGrid
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.identityIcon
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.NumeralStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityDetailScreen(
    viewModel: IdentityDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
    ) { padding ->
        when (val s = state) {
            IdentityDetailState.Loading -> Box(modifier = Modifier.padding(padding).fillMaxSize())
            IdentityDetailState.NotFound -> Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Identity not found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is IdentityDetailState.Loaded -> Body(s, padding)
        }
    }
}

@Composable
private fun Body(state: IdentityDetailState.Loaded, padding: PaddingValues) {
    val identity = state.identity
    val stats = state.stats
    val hue = IdentityHue.forIdentityId(identity.name.lowercase())
    val isDark = isSystemInDarkTheme()
    val gradStart = if (isDark) Color.hsl(hue, 0.30f, 0.18f) else Color.hsl(hue, 0.30f, 0.92f)
    val gradEnd = MaterialTheme.colorScheme.surface

    LazyColumn(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Hero
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Box(
                    modifier = Modifier.background(
                        Brush.linearGradient(
                            colorStops = arrayOf(0f to gradStart, 0.75f to gradEnd),
                        ),
                    ),
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        HabitGlyph(icon = identityIcon(identity.name), hue = hue, size = 64.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            identity.name,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            identity.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            HeroStat(value = stats.currentStreak, label = "STREAK · DAYS", color = FlameOrange)
                            HeroStat(value = stats.daysActive, label = "TOTAL DAYS")
                            HeroStat(value = stats.habitCount, label = "HABITS")
                        }
                    }
                }
            }
        }
        // Activity 90d
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Activity · 90 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "logged a ${identity.name.lowercase()} habit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        IdentityHeatGrid(stats.last90Heat)
                    }
                }
            }
        }
        // Habits
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("Habits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                Text(
                    "What I do because I'm a ${identity.name.lowercase()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (state.habits.isEmpty()) {
                    Text(
                        "No habits linked yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.habits.forEach { habit ->
                            HabitRow(habit, hue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(value: Int, label: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(value.toString(), style = NumeralStyle.copy(fontSize = 26.sp), color = color)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HabitRow(habit: Habit, hue: Float) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HabitGlyph(icon = identityIcon(habit.name), hue = hue, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Only this identity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
```

NOTE: The "Also: X · Y" overlap chip rendering for habits linked to multiple identities is deferred to a follow-up task — the current row always shows "Only this identity". To upgrade later, query other identities for each habit via `IdentityRepository.observeHabitsForIdentity` reverse path. Acceptable for first ship of 5c-1.

- [ ] **Step 3: Swap placeholder in AppNavigation**

In `AppNavigation.kt`, replace the `Screen.IdentityDetail.route` placeholder with the real screen wiring (already shown in Task 5 — paste it back over the placeholder text).

- [ ] **Step 4: Build**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHeatGrid.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailScreen.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m 'feat(identity): IdentityDetailScreen with hero card + 90d heat + habits'
```

---

### Task 11: IdentityHubCard upgrade (canvas-style)

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHubCard.kt`

- [ ] **Step 1: Replace IdentityHubCard body**

Replace the entire `IdentityHubCard` composable with the canvas-style rich card:

```kotlin
package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.Identity

@Composable
fun IdentityHubCard(identities: List<Identity>, onClick: () -> Unit) {
    if (identities.isEmpty()) return
    val firstHue = IdentityHue.forIdentityId(identities.first().name.lowercase())
    val isDark = isSystemInDarkTheme()
    val gradStart = if (isDark) Color.hsl(firstHue, 0.30f, 0.18f) else Color.hsl(firstHue, 0.30f, 0.92f)
    val gradEnd = MaterialTheme.colorScheme.surface

    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(colorStops = arrayOf(0f to gradStart, 0.8f to gradEnd)),
            ),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "IDENTITIES · ${identities.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                StackedAvatars(identities)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = buildIAmCopy(identities),
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tap to manage",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StackedAvatars(identities: List<Identity>) {
    val visible = identities.take(4)
    Box(modifier = Modifier.height(40.dp)) {
        visible.forEachIndexed { i, identity ->
            Box(
                modifier = Modifier
                    .offset(x = (16 * i).dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
            ) {
                HabitGlyph(
                    icon = identityIcon(identity.name),
                    hue = IdentityHue.forIdentityId(identity.name.lowercase()),
                    size = 40.dp,
                )
            }
        }
    }
}

@Composable
private fun buildIAmCopy(identities: List<Identity>) = buildAnnotatedString {
    append("I am a ")
    identities.forEachIndexed { i, identity ->
        val hue = IdentityHue.forIdentityId(identity.name.lowercase())
        val tint = Color.hsl(hue, 0.50f, 0.32f)
        if (i > 0) {
            append(if (i == identities.lastIndex) " & a " else ", a ")
        }
        withStyle(SpanStyle(color = tint)) { append(identity.name.lowercase()) }
    }
    append(".")
}
```

- [ ] **Step 2: Build**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHubCard.kt
rtk git commit -m 'feat(you): IdentityHubCard canvas-style upgrade'
```

---

### Task 12: Manual smoke + PR

**Files:** none (smoke + git operations).

- [ ] **Step 1: Full build + tests**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest :mobile:androidApp:assembleDebug 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Smoke**

Install APK. Verify:

- Home identity strip chip tap → IdentityDetail opens for that identity. Hero stats correct, 90d heat renders, linked habits list shows.
- Home strip "+N more" pill (only when ≥4 identities) → IdentityList opens.
- YouHub identity card → IdentityList opens. Card shows gradient, stacked avatars, "I am a X, a Y, & a Z." copy, "Tap to manage" footer. NO "X pinned".
- IdentityList row tap → IdentityDetail.
- Back arrow on either screen pops correctly.
- Bottom-nav Today/Streak/You tap from List or Detail → lands on the tab cleanly.
- Log a habit on Home → return to IdentityList → stats reflect new log.
- Identity with 0 habits → "No habits linked yet." text + zero stats.
- Light + dark mode both render correctly (gradients tinted to identity hue).

- [ ] **Step 3: Push**

```bash
rtk git push -u origin feature/phase5c-identity-management 2>&1 | tail -3
```

- [ ] **Step 4: Create PR**

```bash
rtk gh pr create --base main --head feature/phase5c-identity-management --title "Phase 5c-1: read-only identity management screens" --body "$(cat <<'EOF'
## Summary

Ship read-only multi-identity management surfaces. IdentityList + IdentityDetail screens with real per-identity stats. YouHub identity card upgraded from 5b stub to canvas-style rich card. All taps wired from Home strip and YouHub.

5c-1 unblocks 5b's inert chips and YouHub card. 5c-2 (later) ships AddIdentityFlow + custom + pin + remove.

## Domain
- `IdentityStats` + `IdentityWithStats` model
- `ComputeIdentityStatsUseCase` — per-identity streak / daysActive / habitCount + 14d sparkline + 90d heat
- `ObserveUserIdentitiesWithStatsUseCase` — IdentityList aggregate
- Heat formula: `sum(min(points, dailyTarget)) / sum(dailyTarget)` per identity's habits, bucketed 0–4
- Strict streak rule (matches 5b-fix): all habits for the identity must have ≥1 log on a day to count

## UI
- `IdentityListScreen` — gradient cards per identity (hue-tinted), 14d sparkline, stats row
- `IdentityDetailScreen` — hero card (avatar 64dp, 3 stats), 90d heat grid, linked habits read-only
- `IdentityHubCard` upgrade — gradient bg, stacked avatars (40dp w/ ring), "I am a X, a Y, & a Z." copy
- Tap wiring: Home strip chip → Detail; "+N more" → List; YouHub card → List; List row → Detail
- Refresh-on-resume on both new screens

## Out of scope (5c-2 + later)
- Add identity flow + custom identity creation
- Pin / Unpin
- Remove identity (soft delete) + Past identities collapsed section
- "Why this identity" reflection card + edit
- Linked-habit row navigation (gated on Habit detail screen)
- "Also: X · Y" overlap chips on linked habits (follow-up; current shows "Only this identity")

## Test plan
- [x] `:mobile:shared:testDebugUnitTest` green (5 + 2 new)
- [x] `:mobile:androidApp:testDebugUnitTest` green (2 new VM tests)
- [x] `assembleDebug` green
- [x] Manual smoke (light + dark): tap flows, refresh-on-resume, gradients, empty states
EOF
)" 2>&1 | tail -3
```

Return PR URL.

---

## Self-Review

**1. Spec coverage:**
- IdentityStats domain → Task 1 ✓
- ComputeIdentityStatsUseCase → Task 2 ✓
- ObserveUserIdentitiesWithStatsUseCase → Task 3 ✓
- AppContainer wiring → Task 4 ✓
- Screen routes + AppNavigation → Task 5 + revisited in 8/10 ✓
- Click callback wiring on existing components → Task 6 ✓
- IdentityListViewModel + Screen + sparkline → Tasks 7-8 ✓
- IdentityDetailViewModel + Screen + heat grid → Tasks 9-10 ✓
- IdentityHubCard upgrade → Task 11 ✓
- Refresh-on-resume → in Tasks 8 + 10 ✓
- Manual smoke + PR → Task 12 ✓

**2. Placeholder scan:**
- Note in Task 10 step 2 about "Also: X · Y" overlap chips deferred to follow-up — explicit deferral with reasoning, not a placeholder.
- Tasks include exact code for every step. No "TBD" or "implement later" left.

**3. Type consistency:**
- `IdentityStats` fields stable across tasks: `identityId`, `currentStreak`, `daysActive`, `habitCount`, `last14Heat`, `last90Heat`. Used in Tasks 1, 2, 7, 8, 10. ✓
- `IdentityWithStats` (identity + stats) used in Tasks 1, 3, 7, 8 ✓
- `IdentityListState.Loaded(items: List<IdentityWithStats>)` used in 7+8 ✓
- `IdentityDetailState.Loaded(identity, stats, habits)` used in 9+10 ✓
- `Screen.IdentityDetail.route(id: String)` used in Tasks 5, 6, 8, 10 ✓
