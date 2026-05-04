# Phase 5e-2 Habit Browse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Read-only Habit list + Habit detail screens. Builds new `ComputePerHabitStreakUseCase` (mirrors user-level engine semantics scoped to one habit). YouHub gains a "Habits" entry row. No mutation UI.

**Architecture:** New domain use case + result model in commonMain. Two new ViewModels + two new Composable screens in androidApp. Two new `Screen` routes mounted in `AppNavigation`. `AppContainer` wires the use case. `YouHubScreen` gains one row. No schema work — relies on 5e-1's `effective_from` / `effective_to` already in place.

**Tech Stack:** Kotlin Multiplatform, Compose Material 3, kotlinx.datetime, kotlinx-coroutines (Flow / SharedFlow), kotlin.test + kotlinx-coroutines-test (commonTest), JUnit (androidApp test).

**Worktree:** `.worktrees/phase5e-2-habit-browse`. **Branch:** `feature/phase5e-2-habit-browse`. **Spec:** `docs/superpowers/specs/2026-05-03-phase5e-2-habit-browse-design.md`.

---

## File Map

**Create (shared/commonMain):**
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/PerHabitStreakResult.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputePerHabitStreakUseCase.kt`

**Create (shared/commonTest):**
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputePerHabitStreakUseCaseTest.kt`

**Create (androidApp):**
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListViewModel.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListScreen.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailViewModel.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailScreen.kt`

**Modify (androidApp):**
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt`

---

## Task 1: PerHabitStreakResult model + ComputePerHabitStreakUseCase + tests

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/PerHabitStreakResult.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputePerHabitStreakUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputePerHabitStreakUseCaseTest.kt`

- [ ] **Step 1: Create the result model**

Create `PerHabitStreakResult.kt`:

```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class PerHabitStreakResult(
    val habitId: String,
    val totalLogs: Int,
    val pointsEarned: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val firstLogDate: LocalDate?,
    val last30Days: List<PerHabitDayState>,
) {
    companion object {
        fun emptyFor(habitId: String, today: LocalDate): PerHabitStreakResult {
            val start = today.minus(29, DateTimeUnit.DAY)
            val cells = (0 until 30).map { offset ->
                PerHabitDayState(start.plus(offset, DateTimeUnit.DAY), StreakDayState.EMPTY)
            }
            return PerHabitStreakResult(
                habitId = habitId,
                totalLogs = 0,
                pointsEarned = 0,
                currentStreak = 0,
                longestStreak = 0,
                firstLogDate = null,
                last30Days = cells,
            )
        }
    }
}

data class PerHabitDayState(
    val date: LocalDate,
    val state: StreakDayState,
)
```

`StreakDayState` already exists at `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/StreakDayState.kt` from 5b. Verify by reading it briefly:

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase5e-2-habit-browse
cat mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/StreakDayState.kt
```

Expected enum values include: COMPLETE, FROZEN, BROKEN, TODAY_PENDING, EMPTY, FUTURE.

- [ ] **Step 2: Write failing tests**

First, read the existing `ComputeStreakUseCaseTest.kt` to copy fixture style:

```bash
head -50 mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCaseTest.kt
```

Note its `userId`, `habitRepo` (FakeHabitRepository or InMemoryRepo pattern), `habitLogRepo` patterns + helper functions (e.g. `makeHabit`, `makeLog`).

Create `ComputePerHabitStreakUseCaseTest.kt` mirroring those patterns:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.StreakDayState
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ComputePerHabitStreakUseCaseTest {
    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 5, 10)
    private val fakeClock = object : Clock {
        override fun now(): Instant = today.atStartOfDayIn(tz).plus(12, DateTimeUnit.HOUR, tz)
    }
    private val userId = "u1"
    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()

    private fun useCase() = ComputePerHabitStreakUseCase(habitLogRepo, habitRepo, tz, fakeClock)

    private fun seedHabit(
        id: String = "h1",
        threshold: Double = 1.0,
        target: Int = 1,
        effectiveFrom: Instant? = LocalDate(2026, 4, 1).atStartOfDayIn(tz),
        effectiveTo: Instant? = null,
    ) {
        val now = today.atStartOfDayIn(tz)
        habitRepo.saveHabit(
            Habit(
                id = id, userId = userId, templateId = "t", name = "H",
                unit = "p", thresholdPerPoint = threshold, dailyTarget = target,
                createdAt = now, updatedAt = now,
                effectiveFrom = effectiveFrom, effectiveTo = effectiveTo,
            )
        )
    }

    private suspend fun seedLog(habitId: String, date: LocalDate, quantity: Double = 1.0) {
        habitLogRepo.insertLog(
            id = "log-$habitId-$date",
            userId = userId,
            habitId = habitId,
            quantity = quantity,
            loggedAt = date.atStartOfDayIn(tz).plus(10, DateTimeUnit.HOUR, tz),
        )
    }

    @Test
    fun `unknown habit returns empty result`() = runTest {
        val result = useCase().computeNow(userId, habitId = "doesNotExist")
        assertEquals("doesNotExist", result.habitId)
        assertEquals(0, result.totalLogs)
        assertEquals(0, result.currentStreak)
        assertEquals(0, result.longestStreak)
        assertNull(result.firstLogDate)
        assertEquals(30, result.last30Days.size)
        assertEquals(today, result.last30Days.last().date)
    }

    @Test
    fun `totalLogs counts logs for habit only`() = runTest {
        seedHabit("h1")
        seedHabit("h2")
        seedLog("h1", today.minus(1, DateTimeUnit.DAY))
        seedLog("h1", today.minus(2, DateTimeUnit.DAY))
        seedLog("h2", today)
        val result = useCase().computeNow(userId, "h1")
        assertEquals(2, result.totalLogs)
    }

    @Test
    fun `pointsEarned sums respecting threshold`() = runTest {
        seedHabit("h1", threshold = 2.0)
        seedLog("h1", today.minus(1, DateTimeUnit.DAY), quantity = 6.0) // 3 pts
        seedLog("h1", today.minus(2, DateTimeUnit.DAY), quantity = 1.0) // 0 pts (below threshold)
        val result = useCase().computeNow(userId, "h1")
        assertEquals(3, result.pointsEarned)
    }

    @Test
    fun `firstLogDate equals earliest log date`() = runTest {
        seedHabit("h1")
        seedLog("h1", today.minus(5, DateTimeUnit.DAY))
        seedLog("h1", today.minus(3, DateTimeUnit.DAY))
        val result = useCase().computeNow(userId, "h1")
        assertEquals(today.minus(5, DateTimeUnit.DAY), result.firstLogDate)
    }

    @Test
    fun `consecutive complete days produce currentStreak`() = runTest {
        seedHabit("h1")
        // log today and 4 prior days
        for (i in 0..4) seedLog("h1", today.minus(i, DateTimeUnit.DAY))
        val result = useCase().computeNow(userId, "h1")
        assertEquals(5, result.currentStreak)
        assertEquals(5, result.longestStreak)
    }

    @Test
    fun `today not yet logged with yesterday logged keeps streak via TODAY_PENDING`() = runTest {
        seedHabit("h1")
        seedLog("h1", today.minus(1, DateTimeUnit.DAY))
        seedLog("h1", today.minus(2, DateTimeUnit.DAY))
        val result = useCase().computeNow(userId, "h1")
        // currentStreak = 2 (yesterday + day before). Today is TODAY_PENDING (not COMPLETE), but doesn't break.
        assertEquals(2, result.currentStreak)
        val todayCell = result.last30Days.last { it.date == today }
        assertEquals(StreakDayState.TODAY_PENDING, todayCell.state)
    }

    @Test
    fun `longest streak finds max run across history`() = runTest {
        seedHabit("h1")
        // Run 1: 3 days (10, 9, 8 days ago). Then gap. Run 2: 5 days (5,4,3,2,1 days ago).
        for (offset in listOf(10, 9, 8, 5, 4, 3, 2, 1)) seedLog("h1", today.minus(offset, DateTimeUnit.DAY))
        val result = useCase().computeNow(userId, "h1")
        assertEquals(5, result.longestStreak)
    }

    @Test
    fun `last30Days has 30 entries ending on today`() = runTest {
        seedHabit("h1")
        seedLog("h1", today)
        val result = useCase().computeNow(userId, "h1")
        assertEquals(30, result.last30Days.size)
        assertEquals(today, result.last30Days.last().date)
        assertEquals(today.minus(29, DateTimeUnit.DAY), result.last30Days.first().date)
    }

    @Test
    fun `past day before habit existed renders as EMPTY not BROKEN`() = runTest {
        // Habit effective from 5 days ago. Day 10 days ago = before habit existed.
        val effectiveFrom = today.minus(5, DateTimeUnit.DAY).atStartOfDayIn(tz)
        seedHabit("h1", effectiveFrom = effectiveFrom)
        // Log it today + yesterday so it has an effective firstLog date
        seedLog("h1", today)
        seedLog("h1", today.minus(1, DateTimeUnit.DAY))
        val result = useCase().computeNow(userId, "h1")
        // Find a day before effectiveFrom in last30Days — should be EMPTY, not BROKEN.
        val tenDaysAgo = today.minus(10, DateTimeUnit.DAY)
        val cell = result.last30Days.firstOrNull { it.date == tenDaysAgo }
        assertNotNull(cell)
        assertEquals(StreakDayState.EMPTY, cell.state)
    }

    @Test
    fun `partial-quantity log still counts as complete day`() = runTest {
        seedHabit("h1", threshold = 5.0) // need 5 to get 1 pt
        seedLog("h1", today.minus(1, DateTimeUnit.DAY), quantity = 1.0) // 0 pts but log exists
        seedLog("h1", today, quantity = 1.0)
        val result = useCase().computeNow(userId, "h1")
        // 2 consecutive days each with ≥1 log → currentStreak counts both via TODAY_PENDING semantics
        // Yesterday COMPLETE (because log exists), today TODAY_PENDING (today, log exists too — actually COMPLETE since cursor==today and in loggedDays).
        assertEquals(2, result.currentStreak)
    }
}
```

(Adapt `seedLog` to whatever method `FakeHabitLogRepository` actually exposes — read the file first to confirm signature.)

- [ ] **Step 3: Run tests to verify they FAIL**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase5e-2-habit-browse
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "com.habittracker.domain.usecase.ComputePerHabitStreakUseCaseTest"
```

Expected: compile error — `ComputePerHabitStreakUseCase` undefined.

- [ ] **Step 4: Implement the use case**

Create `ComputePerHabitStreakUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.PerHabitDayState
import com.habittracker.domain.model.PerHabitStreakResult
import com.habittracker.domain.model.StreakDayState
import kotlinx.coroutines.flow.Flow
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

class ComputePerHabitStreakUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val habitRepo: HabitRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun observe(userId: String, habitId: String): Flow<PerHabitStreakResult> =
        habitLogRepo.observeAllActiveLogsForUser(userId).map { allLogs ->
            val habit = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
                ?: return@map PerHabitStreakResult.emptyFor(habitId, todayLocal())
            compute(habit, allLogs.filter { it.habitId == habitId })
        }

    suspend fun computeNow(userId: String, habitId: String): PerHabitStreakResult {
        val habit = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
            ?: return PerHabitStreakResult.emptyFor(habitId, todayLocal())
        val logs = habitLogRepo.observeAllActiveLogsForUser(userId).first()
            .filter { it.habitId == habitId }
        return compute(habit, logs)
    }

    private fun compute(habit: Habit, logs: List<HabitLog>): PerHabitStreakResult {
        val today = todayLocal()
        val totalLogs = logs.size
        val pointsEarned = logs.sumOf {
            PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint)
        }
        val firstLogDate = logs.minByOrNull { it.loggedAt }?.loggedAt?.toLocalDate()
        if (firstLogDate == null) {
            return PerHabitStreakResult(
                habitId = habit.id,
                totalLogs = 0,
                pointsEarned = 0,
                currentStreak = 0,
                longestStreak = 0,
                firstLogDate = null,
                last30Days = thirtyDayWindow(today, emptyMap()),
            )
        }

        val loggedDays: Set<LocalDate> = logs.map { it.loggedAt.toLocalDate() }.toSet()

        val perDay = mutableMapOf<LocalDate, StreakDayState>()
        var prev: StreakDayState? = null
        var run = 0
        var longest = 0
        var cursor = firstLogDate
        while (cursor <= today) {
            val dayStart = cursor.atStartOfDayIn(timeZone)
            val active = habitActiveOn(habit, dayStart)
            val state = when {
                !active -> StreakDayState.EMPTY
                cursor in loggedDays -> StreakDayState.COMPLETE
                cursor == today -> StreakDayState.TODAY_PENDING
                prev == StreakDayState.COMPLETE -> StreakDayState.FROZEN
                prev == StreakDayState.FROZEN -> StreakDayState.BROKEN
                prev == StreakDayState.BROKEN -> StreakDayState.BROKEN
                prev == StreakDayState.TODAY_PENDING -> StreakDayState.FROZEN
                else -> StreakDayState.EMPTY
            }
            perDay[cursor] = state
            when (state) {
                StreakDayState.COMPLETE -> {
                    run += 1
                    if (run > longest) longest = run
                }
                StreakDayState.FROZEN -> Unit
                StreakDayState.BROKEN -> { run = 0 }
                StreakDayState.TODAY_PENDING, StreakDayState.EMPTY, StreakDayState.FUTURE -> Unit
            }
            prev = state
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }

        return PerHabitStreakResult(
            habitId = habit.id,
            totalLogs = totalLogs,
            pointsEarned = pointsEarned,
            currentStreak = run,
            longestStreak = longest,
            firstLogDate = firstLogDate,
            last30Days = thirtyDayWindow(today, perDay),
        )
    }

    private fun thirtyDayWindow(
        today: LocalDate,
        perDay: Map<LocalDate, StreakDayState>,
    ): List<PerHabitDayState> {
        val start = today.minus(29, DateTimeUnit.DAY)
        return (0 until 30).map { offset ->
            val d = start.plus(offset, DateTimeUnit.DAY)
            PerHabitDayState(d, perDay[d] ?: StreakDayState.EMPTY)
        }
    }

    private fun habitActiveOn(habit: Habit, dayStart: Instant): Boolean =
        (habit.effectiveFrom?.let { it <= dayStart } ?: true) &&
        (habit.effectiveTo?.let { it > dayStart } ?: true)

    private fun Instant.toLocalDate(): LocalDate = toLocalDateTime(timeZone).date

    private fun todayLocal(): LocalDate = clock.now().toLocalDateTime(timeZone).date
}
```

- [ ] **Step 5: Run tests to verify they PASS**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "com.habittracker.domain.usecase.ComputePerHabitStreakUseCaseTest"
```

Expected: all tests pass.

If any fail, read the failure carefully. Likely culprits:
- `FakeHabitLogRepository.insertLog` signature differs from assumed — adapt the test helper.
- `Habit` constructor field order differs — adapt the `seedHabit` helper.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/PerHabitStreakResult.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputePerHabitStreakUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputePerHabitStreakUseCaseTest.kt
rtk git commit -m "$(cat <<'EOF'
feat(usecase): ComputePerHabitStreakUseCase + PerHabitStreakResult model

Per-habit streak rule: day complete iff ≥1 log exists for habit AND
habit was active that day. Mirrors ComputeStreakUseCase semantics
scoped to one habit. FROZEN/BROKEN/TODAY_PENDING follow same logic.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: AppContainer wiring + Screen routes (placeholder composables)

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Wire `ComputePerHabitStreakUseCase` in `AppContainer`**

Add import to `AppContainer.kt`:

```kotlin
import com.habittracker.domain.usecase.ComputePerHabitStreakUseCase
```

Add the use case as a public field (alongside other use cases like `computeIdentityStatsUseCase`):

```kotlin
val computePerHabitStreakUseCase = ComputePerHabitStreakUseCase(habitLogRepository, habitRepository)
```

- [ ] **Step 2: Add `Screen.HabitList` and `Screen.HabitDetail` routes**

In `AppNavigation.kt`, find the `sealed class Screen(val route: String) { ... }` block. Add adjacent to other identity routes:

```kotlin
object HabitList : Screen("habit_list")
object HabitDetail : Screen("habit_detail/{habitId}") {
    const val ARG_ID = "habitId"
    fun route(id: String) = "habit_detail/$id"
}
```

DO NOT add `composable(...)` blocks yet — they reference VMs and Screens that don't exist until later tasks. Step 3 below skips that intentionally.

- [ ] **Step 3: Build to verify routes compile**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase5e-2-habit-browse
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL (the new routes are static — they don't depend on VMs/Screens).

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "$(cat <<'EOF'
feat(container): wire ComputePerHabitStreakUseCase + Screen.HabitList/HabitDetail routes

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: HabitListViewModel + test

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListViewModel.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListViewModelTest.kt`

- [ ] **Step 1: Read existing ViewModel pattern + repos**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase5e-2-habit-browse
cat mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListViewModel.kt
cat mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitRepository.kt
cat mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt | head -50
```

Identify:
- Pattern: private primary constructor + secondary `(container: AppContainer)` constructor + `companion object { fun forTest(...) }`.
- `HabitRepository.observeHabitsForUser(userId): Flow<List<Habit>>` — the source flow.
- `IdentityRepository` methods for resolving habit→identity links + identity names.

The simplest path to derive `identityNames`:
1. `identityRepo.observeUserIdentities(userId)` → `Flow<List<Identity>>` for the user's active identities (filters removed).
2. For each habit, fetch its linked identity ids — likely via `q.getHabitIdentities(habitId)` exposed through some method. Read `LocalIdentityRepository` to find the public method.

If no public method exists for "get habit_identities for a single habit," you may need to use `getUnsyncedHabitIdentitiesFor(userId)` (returns ALL links for the user) and filter in-memory by habitId. That works but is loose. Better: add a new query.

For 5e-2 simplicity, do this in the VM:
- Get all `LocalHabitIdentity` rows for the user (one query).
- Get all user identities.
- Build a `Map<habitId, List<identityId>>` once per emission.
- Resolve `identityId` → `identityName` from the user's identity list.

If `IdentityRepository` doesn't already expose "get all habit-identity rows for a user" you can add one. For 5e-2, prefer adding a small new method:

```kotlin
// In IdentityRepository.kt interface:
suspend fun getHabitIdentityLinksForUser(userId: String): List<HabitIdentityRow>
```

```kotlin
// In LocalIdentityRepository.kt:
override suspend fun getHabitIdentityLinksForUser(userId: String): List<HabitIdentityRow> =
    q.getHabitIdentitiesForUser(userId).executeAsList().map {
        HabitIdentityRow(
            habitId = it.habitId,
            identityId = it.identityId,
            addedAt = Instant.fromEpochMilliseconds(it.addedAt),
            syncedAt = it.syncedAt?.let(Instant::fromEpochMilliseconds),
            effectiveFrom = it.effectiveFrom?.let(Instant::fromEpochMilliseconds),
            effectiveTo = it.effectiveTo?.let(Instant::fromEpochMilliseconds),
        )
    }
```

Add the SQL query to `HabitTrackerDatabase.sq`:

```sql
getHabitIdentitiesForUser:
SELECT li.*
FROM LocalHabitIdentity li
INNER JOIN LocalHabit h ON h.id = li.habitId
WHERE h.userId = ?;
```

Add to `FakeIdentityRepository`:

```kotlin
override suspend fun getHabitIdentityLinksForUser(userId: String): List<HabitIdentityRow> {
    val userHabitIds = habits.value.filter { it.userId == userId }.map { it.id }.toSet()
    return habitIdentities.value.filter { it.habitId in userHabitIds }
}
```

(Read `FakeIdentityRepository` to confirm the `habits` and `habitIdentities` field names.)

- [ ] **Step 2: Write `HabitListViewModelTest` (failing)**

Create `HabitListViewModelTest.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.habit

import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HabitListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val userId = "u1"
    private val habitRepo = FakeHabitRepository()
    private val identityRepo = FakeIdentityRepository(
        seed = listOf(
            Identity(id = "athlete", name = "Athlete", description = "", icon = ""),
            Identity(id = "reader", name = "Reader", description = "", icon = ""),
        ),
    )

    @Before fun setUp() { kotlinx.coroutines.Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { kotlinx.coroutines.Dispatchers.resetMain() }

    @Test
    fun `loads habits sorted alphabetically with identity names`() = runTest {
        val now = Clock.System.now()
        habitRepo.saveHabit(Habit(
            id = "h2", userId = userId, templateId = "t", name = "Run",
            unit = "min", thresholdPerPoint = 1.0, dailyTarget = 1,
            createdAt = now, updatedAt = now, effectiveFrom = now,
        ))
        habitRepo.saveHabit(Habit(
            id = "h1", userId = userId, templateId = "t", name = "Read",
            unit = "p", thresholdPerPoint = 1.0, dailyTarget = 1,
            createdAt = now, updatedAt = now, effectiveFrom = now,
        ))
        identityRepo.linkHabitToIdentities("h2", setOf("athlete"))
        identityRepo.linkHabitToIdentities("h1", setOf("reader"))
        identityRepo.seedUserIdentity(userId, "athlete")
        identityRepo.seedUserIdentity(userId, "reader")

        val vm = HabitListViewModel.forTest(habitRepo, identityRepo) { userId }
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("Expected Loaded but was $state", state is HabitListState.Loaded)
        val loaded = state as HabitListState.Loaded
        assertEquals(2, loaded.habits.size)
        // Sorted alphabetically: Read, Run
        assertEquals("Read", loaded.habits[0].habit.name)
        assertEquals(listOf("Reader"), loaded.habits[0].identityNames)
        assertEquals("Run", loaded.habits[1].habit.name)
        assertEquals(listOf("Athlete"), loaded.habits[1].identityNames)
    }

    @Test
    fun `filters out habits with effectiveTo set`() = runTest {
        val now = Clock.System.now()
        habitRepo.saveHabit(Habit(
            id = "h1", userId = userId, templateId = "t", name = "Active",
            unit = "p", thresholdPerPoint = 1.0, dailyTarget = 1,
            createdAt = now, updatedAt = now, effectiveFrom = now, effectiveTo = null,
        ))
        habitRepo.saveHabit(Habit(
            id = "h2", userId = userId, templateId = "t", name = "Deleted",
            unit = "p", thresholdPerPoint = 1.0, dailyTarget = 1,
            createdAt = now, updatedAt = now, effectiveFrom = now, effectiveTo = now,
        ))

        val vm = HabitListViewModel.forTest(habitRepo, identityRepo) { userId }
        advanceUntilIdle()

        val loaded = vm.state.value as HabitListState.Loaded
        assertEquals(1, loaded.habits.size)
        assertEquals("Active", loaded.habits.first().habit.name)
    }

    @Test
    fun `empty user produces Loaded with empty list`() = runTest {
        val vm = HabitListViewModel.forTest(habitRepo, identityRepo) { userId }
        advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state is HabitListState.Loaded && state.habits.isEmpty())
    }
}
```

- [ ] **Step 3: Run test to verify FAIL**

```bash
rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.ui.habit.HabitListViewModelTest"
```

Expected: compile error — `HabitListViewModel` undefined.

- [ ] **Step 4: Implement HabitListViewModel**

Create `HabitListViewModel.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface HabitListState {
    data object Loading : HabitListState
    data class Loaded(val habits: List<HabitRowItem>) : HabitListState
}

data class HabitRowItem(
    val habit: Habit,
    val identityNames: List<String>,
)

class HabitListViewModel private constructor(
    private val habitRepo: HabitRepository,
    private val identityRepo: IdentityRepository,
    private val userIdProvider: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow<HabitListState>(HabitListState.Loading)
    val state: StateFlow<HabitListState> = _state.asStateFlow()

    private var job: Job? = null

    constructor(container: AppContainer) : this(
        habitRepo = container.habitRepository,
        identityRepo = container.identityRepository,
        userIdProvider = { container.currentUserId() },
    )

    init { observe() }

    private fun observe() {
        job?.cancel()
        job = viewModelScope.launch {
            val userId = userIdProvider()
            habitRepo.observeHabitsForUser(userId).collect { habits ->
                val activeHabits = habits.filter { it.effectiveTo == null }
                val sortedHabits = activeHabits.sortedBy { it.habit.name.lowercase() } // see helper note
                val identities = identityRepo.observeUserIdentities(userId).first()
                val identityById = identities.associateBy { it.id }
                val links = identityRepo.getHabitIdentityLinksForUser(userId)
                    .filter { it.effectiveTo == null }
                    .groupBy { it.habitId }

                val rows = activeHabits
                    .sortedBy { it.name.lowercase() }
                    .map { habit ->
                        val identityIds = links[habit.id]?.map { it.identityId }.orEmpty()
                        val names = identityIds.mapNotNull { identityById[it]?.name }
                        HabitRowItem(habit = habit, identityNames = names)
                    }

                _state.value = HabitListState.Loaded(rows)
            }
        }
    }

    companion object {
        fun forTest(
            habitRepo: HabitRepository,
            identityRepo: IdentityRepository,
            userIdProvider: () -> String,
        ) = HabitListViewModel(habitRepo, identityRepo, userIdProvider)
    }
}

// Note: the helper `activeHabits.sortedBy { it.habit.name.lowercase() }` should reference
// the habit directly: `habits.sortedBy { it.name.lowercase() }`. Drop the extra var
// `sortedHabits` if unused.
```

Clean up the leftover `sortedHabits` variable from the example before finalizing — only keep one sort path.

- [ ] **Step 5: Run test to verify PASS**

```bash
rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.ui.habit.HabitListViewModelTest"
```

Expected: 3 tests pass.

If `getHabitIdentityLinksForUser` is missing from the repository (Step 1 above), implement it first (per Step 1's instructions for adding the new SQL query + repo method + Fake stub). Re-run.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListViewModel.kt \
            mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListViewModelTest.kt
# also stage SQL/repo/Fake additions if any:
rtk git add mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt \
            mobile/shared/src/commonMain/sqldelight/databases/ 2>/dev/null
rtk git commit -m "$(cat <<'EOF'
feat(ui): HabitListViewModel — sorted alphabetically with identity names

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: HabitListScreen UI

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListScreen.kt`

- [ ] **Step 1: Implement the screen**

Create `HabitListScreen.kt`. Follow design canvas at `/tmp/habitto-design/habitto/project/screens.jsx:942` (HabitCRUD), stripped of FAB + drag handles.

```kotlin
package com.jktdeveloper.habitto.ui.habit

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.habitIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onBack: () -> Unit,
    onHabitClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habits", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            HabitListState.Loading -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is HabitListState.Loaded -> {
                if (s.habits.isEmpty()) {
                    Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No habits yet.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Add some via Identities.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        items(s.habits, key = { it.habit.id }) { row ->
                            HabitRow(row = row, onClick = { onHabitClick(row.habit.id) })
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitRow(row: HabitRowItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            val firstIdentityName = row.identityNames.firstOrNull() ?: ""
            val hue = if (firstIdentityName.isNotEmpty())
                IdentityHue.forIdentityId(firstIdentityName.lowercase())
            else 0f
            HabitGlyph(
                icon = habitIcon(row.habit.name),
                hue = hue,
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.habit.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val subtitle = buildString {
                    append(row.identityNames.joinToString(" · ").ifBlank { "Unlinked" })
                    append(" · target ")
                    append(row.habit.dailyTarget)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

(Adapt `IdentityHue.forIdentityId(...)` invocation to whatever the existing signature is — verify in `HabitGlyph.kt`.)

- [ ] **Step 2: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL. The screen isn't mounted in NavHost yet (Task 7), so it'll compile but won't be reachable.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListScreen.kt
rtk git commit -m "$(cat <<'EOF'
feat(ui): HabitListScreen — flat list, alphabetical, no FAB

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: HabitDetailViewModel + test

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailViewModel.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailViewModelTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.jktdeveloper.habitto.ui.habit

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.ComputePerHabitStreakUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val userId = "u1"
    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()
    private val identityRepo = FakeIdentityRepository(
        seed = listOf(Identity(id = "athlete", name = "Athlete", description = "", icon = "")),
    )
    private val useCase = ComputePerHabitStreakUseCase(habitLogRepo, habitRepo, TimeZone.UTC, Clock.System)

    @Before fun setUp() { kotlinx.coroutines.Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { kotlinx.coroutines.Dispatchers.resetMain() }

    @Test
    fun `unknown habitId emits NotFound`() = runTest {
        val vm = HabitDetailViewModel.forTest(habitRepo, identityRepo, useCase, { userId }, "unknown")
        advanceUntilIdle()
        assertTrue(vm.state.value is HabitDetailState.NotFound)
    }

    @Test
    fun `Loaded combines habit + identityNames + streak`() = runTest {
        val now = Clock.System.now()
        habitRepo.saveHabit(Habit(
            id = "h1", userId = userId, templateId = "t", name = "Run",
            unit = "min", thresholdPerPoint = 1.0, dailyTarget = 1,
            createdAt = now, updatedAt = now, effectiveFrom = now,
        ))
        identityRepo.linkHabitToIdentities("h1", setOf("athlete"))
        identityRepo.seedUserIdentity(userId, "athlete")

        val vm = HabitDetailViewModel.forTest(habitRepo, identityRepo, useCase, { userId }, "h1")
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is HabitDetailState.Loaded)
        val loaded = state as HabitDetailState.Loaded
        assertEquals("Run", loaded.habit.name)
        assertEquals(listOf("Athlete"), loaded.identityNames)
        assertEquals("h1", loaded.streak.habitId)
        assertEquals(0, loaded.streak.totalLogs)
    }
}
```

- [ ] **Step 2: Run test to verify FAIL**

```bash
rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.ui.habit.HabitDetailViewModelTest"
```

Expected: compile error — `HabitDetailViewModel` undefined.

- [ ] **Step 3: Implement HabitDetailViewModel**

Create `HabitDetailViewModel.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.PerHabitStreakResult
import com.habittracker.domain.usecase.ComputePerHabitStreakUseCase
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface HabitDetailState {
    data object Loading : HabitDetailState
    data object NotFound : HabitDetailState
    data class Loaded(
        val habit: Habit,
        val identityNames: List<String>,
        val streak: PerHabitStreakResult,
    ) : HabitDetailState
}

class HabitDetailViewModel private constructor(
    private val habitRepo: HabitRepository,
    private val identityRepo: IdentityRepository,
    private val streakUseCase: ComputePerHabitStreakUseCase,
    private val userIdProvider: () -> String,
    private val habitId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<HabitDetailState>(HabitDetailState.Loading)
    val state: StateFlow<HabitDetailState> = _state.asStateFlow()

    private var job: Job? = null

    constructor(container: AppContainer, habitId: String) : this(
        habitRepo = container.habitRepository,
        identityRepo = container.identityRepository,
        streakUseCase = container.computePerHabitStreakUseCase,
        userIdProvider = { container.currentUserId() },
        habitId = habitId,
    )

    init { observe() }

    private fun observe() {
        job?.cancel()
        job = viewModelScope.launch {
            val userId = userIdProvider()
            streakUseCase.observe(userId, habitId).collect { streak ->
                val habit = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
                if (habit == null) {
                    _state.value = HabitDetailState.NotFound
                    return@collect
                }
                val identities = identityRepo.observeUserIdentities(userId).first()
                val identityById = identities.associateBy { it.id }
                val links = identityRepo.getHabitIdentityLinksForUser(userId)
                    .filter { it.habitId == habitId && it.effectiveTo == null }
                val identityNames = links.mapNotNull { identityById[it.identityId]?.name }
                _state.value = HabitDetailState.Loaded(habit, identityNames, streak)
            }
        }
    }

    companion object {
        fun forTest(
            habitRepo: HabitRepository,
            identityRepo: IdentityRepository,
            streakUseCase: ComputePerHabitStreakUseCase,
            userIdProvider: () -> String,
            habitId: String,
        ) = HabitDetailViewModel(habitRepo, identityRepo, streakUseCase, userIdProvider, habitId)
    }
}
```

- [ ] **Step 4: Run test to verify PASS**

```bash
rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.ui.habit.HabitDetailViewModelTest"
```

Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailViewModel.kt \
            mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailViewModelTest.kt
rtk git commit -m "$(cat <<'EOF'
feat(ui): HabitDetailViewModel — combines habit + identity names + per-habit streak

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: HabitDetailScreen UI

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailScreen.kt`

- [ ] **Step 1: Implement the screen**

Create `HabitDetailScreen.kt`. Follow canvas at `/tmp/habitto-design/habitto/project/screens.jsx:876`.

```kotlin
package com.jktdeveloper.habitto.ui.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.PerHabitDayState
import com.habittracker.domain.model.StreakDayState
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.habitIcon
import com.jktdeveloper.habitto.ui.streak.BrokenOverlay
import com.jktdeveloper.habitto.ui.streak.FrozenOverlay
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.StreakBrokenBg
import com.jktdeveloper.habitto.ui.theme.StreakFrozenBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    viewModel: HabitDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            HabitDetailState.Loading -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HabitDetailState.NotFound -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Habit not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is HabitDetailState.Loaded -> LoadedContent(state = s, contentPadding = padding)
        }
    }
}

@Composable
private fun LoadedContent(state: HabitDetailState.Loaded, contentPadding: PaddingValues) {
    val firstIdentityName = state.identityNames.firstOrNull().orEmpty()
    val hue = if (firstIdentityName.isNotEmpty())
        IdentityHue.forIdentityId(firstIdentityName.lowercase())
    else 0f

    LazyColumn(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                HabitGlyph(icon = habitIcon(state.habit.name), hue = hue, size = 56.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.habit.name,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 44.sp,
                )
                Spacer(Modifier.height(4.dp))
                val identityLabel = state.identityNames.joinToString(", ").ifBlank { "Unlinked" }
                Text(
                    text = "$identityLabel · ${formatThreshold(state.habit.thresholdPerPoint)} ${state.habit.unit} per pt · target ${state.habit.dailyTarget}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            StatsGrid(state = state, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        item {
            ThirtyDayCard(
                cells = state.streak.last30Days,
                hue = hue,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun StatsGrid(state: HabitDetailState.Loaded, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Per-habit streak",
                value = state.streak.currentStreak.toString(),
                suffix = "days",
                tint = FlameOrange,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Total logs",
                value = state.streak.totalLogs.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Longest streak",
                value = state.streak.longestStreak.toString(),
                suffix = "days",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Points earned",
                value = state.streak.pointsEarned.toString(),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    suffix: String? = null,
    tint: Color? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    value,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tint ?: MaterialTheme.colorScheme.onSurface,
                    lineHeight = 32.sp,
                )
                if (suffix != null) {
                    Text(suffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ThirtyDayCard(cells: List<PerHabitDayState>, hue: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            "Last 30 days",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 3 rows × 10 cols
                cells.chunked(10).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { cell ->
                            DayCell(state = cell.state, hue = hue, modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(state: StreakDayState, hue: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val (bg, accent) = when (state) {
        StreakDayState.COMPLETE -> Color.hsl(hue, 0.50f, 0.50f) to null
        StreakDayState.FROZEN -> StreakFrozenBg to Color(0xFF00838F)  // matches StreakFrozen
        StreakDayState.BROKEN -> StreakBrokenBg to Color(0xFFE53935)
        StreakDayState.TODAY_PENDING -> Color.Transparent to MaterialTheme.colorScheme.primary
        StreakDayState.EMPTY -> MaterialTheme.colorScheme.surfaceContainerLow to null
        StreakDayState.FUTURE -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f) to null
    }
    val baseModifier = modifier.clip(shape).background(bg)
    val finalModifier = when (state) {
        StreakDayState.TODAY_PENDING -> baseModifier.border(2.dp, accent ?: Color.Transparent, shape)
        else -> baseModifier
    }
    Box(modifier = finalModifier, contentAlignment = Alignment.Center) {
        when (state) {
            StreakDayState.FROZEN -> if (accent != null) FrozenOverlay(color = accent)
            StreakDayState.BROKEN -> if (accent != null) BrokenOverlay(color = accent)
            else -> Unit
        }
    }
}

private fun formatThreshold(value: Double): String {
    val rounded = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    return rounded
}
```

(Adapt color tokens — `FlameOrange`, `StreakFrozenBg`, `StreakBrokenBg` — to whatever your theme exposes. If those exact names don't exist, find equivalents in `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/theme/Color.kt`.)

- [ ] **Step 2: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL. Resolve any missing imports or token names.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailScreen.kt
rtk git commit -m "$(cat <<'EOF'
feat(ui): HabitDetailScreen — hero + 4 stat tiles + 30-day mini heatmap

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: NavHost mounts + YouHub Habits row

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt`

- [ ] **Step 1: Mount HabitList + HabitDetail composables in NavHost**

In `AppNavigation.kt`, find the `NavHost(...) { ... }` block. Add:

```kotlin
composable(Screen.HabitList.route) {
    val vm = viewModel { HabitListViewModel(container) }
    HabitListScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        onHabitClick = { id -> navController.navigate(Screen.HabitDetail.route(id)) },
    )
}
composable(
    route = Screen.HabitDetail.route,
    arguments = listOf(navArgument(Screen.HabitDetail.ARG_ID) { type = NavType.StringType }),
) { entry ->
    val habitId = entry.arguments?.getString(Screen.HabitDetail.ARG_ID).orEmpty()
    val vm = viewModel { HabitDetailViewModel(container, habitId) }
    HabitDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
}
```

Add imports:

```kotlin
import com.jktdeveloper.habitto.ui.habit.HabitListViewModel
import com.jktdeveloper.habitto.ui.habit.HabitListScreen
import com.jktdeveloper.habitto.ui.habit.HabitDetailViewModel
import com.jktdeveloper.habitto.ui.habit.HabitDetailScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
```

- [ ] **Step 2: Add `onHabitsClick` callback to YouHubScreen + wire from AppNavigation**

In `YouHubScreen.kt`:

Add the parameter to the Composable signature:

```kotlin
@Composable
fun YouHubScreen(
    viewModel: YouHubViewModel,
    onOpenSettings: () -> Unit,
    onSignIn: () -> Unit,
    onSignOutComplete: () -> Unit,
    onOpenIdentities: () -> Unit,
    onHabitsClick: () -> Unit,  // new
) {
```

Add a `Habits` row in the LazyColumn between the IdentityHubCard and "Account" section. Use the existing `ListItem` + `clickable` pattern matching other rows in the file:

```kotlin
item { SectionHeader("Tracking") }
item {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onHabitsClick() },
        leadingContent = {
            Icon(
                Icons.Outlined.TaskAlt,
                contentDescription = null,
            )
        },
        headlineContent = { Text("Habits") },
        supportingContent = { Text("Manage what you track") },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        },
    )
}
```

Add the import:

```kotlin
import androidx.compose.material.icons.outlined.TaskAlt
```

(If `TaskAlt` is not available in `material-icons-extended`, fall back to `Icons.Outlined.Checklist` or `Icons.Outlined.CheckCircleOutline`.)

In `AppNavigation.kt`, find the `composable(Screen.You.route)` (or wherever `YouHubScreen` is mounted). Pass the new callback:

```kotlin
YouHubScreen(
    viewModel = vm,
    onOpenSettings = { navController.navigate(Screen.Settings.route) },
    onSignIn = { navController.navigate(Screen.Auth.route) },
    onSignOutComplete = { /* existing */ },
    onOpenIdentities = { navController.navigate(Screen.IdentityList.route) },
    onHabitsClick = { navController.navigate(Screen.HabitList.route) },
)
```

(Adapt to whatever shape exists in the YouHub composable block.)

- [ ] **Step 3: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL + all tests pass.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt
rtk git commit -m "$(cat <<'EOF'
feat(nav): mount HabitList + HabitDetail + YouHub Habits row entry

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Manual smoke + PR

**Files:** none (validation + git ops).

- [ ] **Step 1: Run full test suite**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase5e-2-habit-browse
rtk ./gradlew :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL + all tests pass (incl. new `ComputePerHabitStreakUseCaseTest`, `HabitListViewModelTest`, `HabitDetailViewModelTest`).

- [ ] **Step 2: Install + smoke**

```bash
rtk ./gradlew :mobile:androidApp:installDebug
```

Smoke checklist:

- [ ] Navigate to You hub → "Habits" row visible under "Tracking" section → tap → HabitList opens.
- [ ] HabitList shows all user habits, sorted alphabetically, with identity-name subtitles + target.
- [ ] Tap a habit → HabitDetail opens with hero (avatar 56dp + 40sp name + subtitle line) + 4 stat tiles (Per-habit streak / Total logs / Longest streak / Points earned) + "Last 30 days" mini heatmap (3×10 cells).
- [ ] Per-habit streak value = consecutive days with ≥1 log for THAT habit only (independent of user-level streak).
- [ ] Total logs / Points earned numbers match expected counts for that habit.
- [ ] Log a habit from Today screen → return to its HabitDetail → totalLogs +1, currentStreak updated, today cell turns COMPLETE within ~1s (Flow reactivity).
- [ ] Multi-identity habit: subtitle shows all linked identity names, comma-separated.
- [ ] Habit linked to a since-removed identity: still appears in HabitList (orphan, per 5c-2 C1).
- [ ] HabitDetail with bogus habitId (try `habit_detail/bogus` deeplink if routes allow): renders "Habit not found" message, no crash.
- [ ] Empty user (skipped onboarding): HabitList shows "No habits yet. Add some via Identities." message.
- [ ] Light + dark mode: hero, stat tiles, heatmap cells all render correctly in both modes.
- [ ] No regression: existing flows still work (Today log+commit, IdentityList, Streak history).

- [ ] **Step 3: Push branch**

```bash
rtk git push -u origin feature/phase5e-2-habit-browse
```

- [ ] **Step 4: Create PR**

```bash
rtk gh pr create --base main --head feature/phase5e-2-habit-browse \
    --title "Phase 5e-2: Habit browse (read-only) + per-habit streak compute" \
    --body "$(cat <<'BODY'
## Summary

Read-only Habit list + Habit detail screens, plus a new `ComputePerHabitStreakUseCase` that mirrors the user-level streak engine semantics scoped to one habit. Builds on 5e-1's effective-window foundation.

- New use case: `ComputePerHabitStreakUseCase` returns `PerHabitStreakResult(totalLogs, pointsEarned, currentStreak, longestStreak, firstLogDate, last30Days)`.
- Per-habit rule: day complete iff ≥1 log exists for habit AND habit was active that day. FROZEN/BROKEN/TODAY_PENDING follow the same single-grace-day semantics as the user-level engine.
- New screens: `HabitListScreen` (alphabetical, identity-name subtitles, no FAB) + `HabitDetailScreen` (hero, 4 stat tiles, 30-day mini heatmap).
- Entry point: You hub → "Habits" row → HabitList → tap row → HabitDetail.

## Out of scope

- Habit form (add / edit) — 5e-3
- Habit delete UI — 5e-3 (the `markHabitDeleted` SQL query already lives from 5e-1)
- App bar `edit` + `more_vert` icons on HabitDetail — 5e-3
- FAB on HabitList — 5e-3
- Drag-reorder habits — undesigned, separate phase
- Group HabitList by identity — flat list ships, grouping deferred
- Tap-from-Today habit cards → HabitDetail — would conflict with existing tap-to-log; defer

## Test plan
- [x] `:mobile:shared:testDebugUnitTest` green (incl. 10 new `ComputePerHabitStreakUseCaseTest` cases)
- [x] `:mobile:androidApp:testDebugUnitTest` green (incl. new VM tests)
- [ ] Manual: You hub → Habits → HabitList → tap → HabitDetail
- [ ] Manual: log a habit → revisit detail → totalLogs + streak update reactively
- [ ] Manual: multi-identity habit shows all linked identity names in subtitle
- [ ] Manual: orphan habit (identity removed) still appears in HabitList
- [ ] Manual: bogus habitId deeplink → "Habit not found" state
- [ ] Manual: empty user → empty state on HabitList
- [ ] Manual: light + dark mode both render correctly

🤖 Generated with [Claude Code](https://claude.com/claude-code)
BODY
)"
```

---

## Self-Review

**Spec coverage:**
- `PerHabitStreakResult` model → Task 1 step 1
- `ComputePerHabitStreakUseCase` + tests → Task 1
- `AppContainer` wiring → Task 2
- Two new `Screen` routes → Task 2
- `getHabitIdentityLinksForUser` repo helper → Task 3 (added inline)
- `HabitListViewModel` + tests → Task 3
- `HabitListScreen` UI → Task 4
- `HabitDetailViewModel` + tests → Task 5
- `HabitDetailScreen` UI → Task 6
- NavHost composables for HabitList + HabitDetail → Task 7
- YouHub "Habits" row → Task 7
- Manual smoke + PR → Task 8

**Placeholder scan:** none.

**Type consistency:**
- `PerHabitStreakResult` field names (`habitId`, `totalLogs`, `pointsEarned`, `currentStreak`, `longestStreak`, `firstLogDate`, `last30Days`) used identically in model, use case, ViewModels, and Screens.
- `HabitRowItem(habit, identityNames)` used identically in `HabitListViewModel` state and `HabitListScreen` consumer.
- `HabitDetailState.Loaded(habit, identityNames, streak)` used identically across VM and Screen.
- `Screen.HabitList.route` and `Screen.HabitDetail.route(id)` consistent in `Screen` declaration (Task 2) and `composable(...)` mounts (Task 7).
- `getHabitIdentityLinksForUser(userId)` signature consistent across interface, Local impl, Fake impl.
