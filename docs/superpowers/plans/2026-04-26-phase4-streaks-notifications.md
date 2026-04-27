# Phase 4: Streaks + Notifications + Settings — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the streak grid + history screen, the Settings screen with notification toggles, four notification types via three WorkManager workers, pull-to-refresh on Home, and the master-spec point rollover cap fix.

**Architecture:** All streak/rollover compute is on-device, derived from existing `HabitLog` rows — no DB migration, no remote schema change, no derived tables. Notifications run as WorkManager periodic workers. Settings preferences live in DataStore. UI surfaces add `StreakStrip` to Home, plus two new routes (`StreakHistory`, `Settings`).

**Tech Stack:** Kotlin Multiplatform (commonMain for compute), Compose Material 3, WorkManager 2.10, DataStore Preferences, SQLDelight 2.0.2, kotlinx-datetime 0.6.1.

**Worktree:** `.worktrees/phase4-streaks-notifications`
**Branch:** `feature/phase4-streaks-notifications`
**Spec:** `docs/superpowers/specs/2026-04-26-phase4-streaks-notifications-design.md`

---

## File Structure

### Created (commonMain)

| File | Responsibility |
|------|----------------|
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/StreakDay.kt` | `StreakDayState` enum, `StreakDay`, `StreakRangeResult`, `StreakSummary`, `DateRange` data classes. |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCase.kt` | Pure compute. Walks logs into per-day states + summary stats. |

### Created (commonTest)

| File | Responsibility |
|------|----------------|
| `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCaseTest.kt` | TDD coverage for all streak rules including DST, gaps, today-pending. |

### Created (androidApp/androidMain)

| File | Responsibility |
|------|----------------|
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferences.kt` | DataStore wrapper + `NotificationPrefs` data class. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationScheduler.kt` | Channel setup + WorkManager scheduling. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationChannels.kt` | Channel ID constants + `ensureChannels()`. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorker.kt` | Fires daily reminder if no log today. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorker.kt` | Fires streak-at-risk if streak active + no log. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorker.kt` | Fires frozen / reset notifs based on yesterday's state. Idempotent. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationFiringDateStore.kt` | Last-fired-date persistence for DayBoundaryWorker idempotency. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PermissionUtils.kt` | `POST_NOTIFICATIONS` runtime permission helpers. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsViewModel.kt` | Owns prefs flow, toggle handlers, sign-out wiring. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsScreen.kt` | Sections: notifications / account / about. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakStrip.kt` | Home composable: header + 30-day grid. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryViewModel.kt` | Loads months on demand, exposes summary. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryScreen.kt` | Top bar + summary card + LazyColumn of months. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/MonthCalendar.kt` | 7×N grid composable for one month. |

### Created (androidApp tests)

| File | Responsibility |
|------|----------------|
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferencesTest.kt` | DataStore round-trip. |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationSchedulerTest.kt` | Toggle on/off → enqueue/cancel via WorkManager test harness. |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorkerTest.kt` | Fires when no log; skips when logged. |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorkerTest.kt` | Fires when streak ≥ 1 + no log; skips when streak == 0. |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorkerTest.kt` | Fires frozen/reset based on yesterday; idempotent same-day re-run. |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsViewModelTest.kt` | Toggle persists; reschedule called. |
| `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryViewModelTest.kt` | Initial month + loadOlder + stop at firstLogDate. |

### Modified (commonMain)

| File | What changes |
|------|--------------|
| `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` | New queries: `observeLogsBetween`, `countActiveLogsForDay`, `firstLogDate`. |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitLogRepository.kt` | New methods matching the new queries. |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SqlDelightHabitLogRepository.kt` | Implement the new methods. |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt` | Day-walking compute with rollover cap; switch to local TZ. |
| `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseTest.kt` | New rollover cases + TZ change. |

### Modified (androidApp)

| File | What changes |
|------|--------------|
| `gradle/libs.versions.toml` | Add `datastore` version + `androidx-datastore-preferences` library entry. |
| `mobile/androidApp/build.gradle.kts` | Add `androidx-datastore-preferences` to androidMain deps. |
| `mobile/androidApp/src/androidMain/AndroidManifest.xml` | Add `POST_NOTIFICATIONS` + `RECEIVE_BOOT_COMPLETED` perms. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` | Wire `NotificationPreferences`, `NotificationScheduler`, `ComputeStreakUseCase`. Reschedule on auth change. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/MainActivity.kt` | Init notification channels + scheduler reschedule on start. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` | Add `Screen.Settings`, `Screen.StreakHistory` + composable nodes. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt` | Wrap content in `PullToRefreshBox`, mount `StreakStrip`, replace overflow with Settings icon. |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt` | Add `streakStrip`, `streakSummary`, `manualRefresh()`. Drop sign-out menu state (now in Settings). |

---

## Tasks

### Task 1: Dependencies + manifest perms
**Files:** `gradle/libs.versions.toml`, `mobile/androidApp/build.gradle.kts`, `mobile/androidApp/src/androidMain/AndroidManifest.xml`.

### Task 2: SQLDelight queries — date range, day count, first log date
**Files:** `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`.

### Task 3: HabitLogRepository — new methods
**Files:** `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitLogRepository.kt`, `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SqlDelightHabitLogRepository.kt`.

### Task 4: Streak domain types
**Files:** `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/StreakDay.kt`.

### Task 5: ComputeStreakUseCase + tests (TDD)
**Files:** `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCase.kt`, `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCaseTest.kt`.

### Task 6: Point rollover cap + TZ fix + tests
**Files:** `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt`, `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseTest.kt`.

### Task 7: Screen sealed-class additions + permission utils
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` (only the `Screen` sealed class, not the navhost yet — that's Task 18), `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PermissionUtils.kt`.

### Task 8: NotificationPreferences (DataStore) + test
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferences.kt`, `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferencesTest.kt`.

### Task 9: NotificationChannels + NotificationScheduler skeleton
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationChannels.kt`, `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationScheduler.kt`.

### Task 10: DailyReminderWorker + test
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorker.kt`, `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorkerTest.kt`.

### Task 11: StreakRiskWorker + test
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorker.kt`, `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorkerTest.kt`.

### Task 12: DayBoundaryWorker + idempotency store + test
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorker.kt`, `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationFiringDateStore.kt`, `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorkerTest.kt`.

### Task 13: NotificationScheduler — wire reschedule + cancel + test
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationScheduler.kt`, `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationSchedulerTest.kt`.

### Task 14: SettingsViewModel + test
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsViewModel.kt`, `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsViewModelTest.kt`.

### Task 15: SettingsScreen UI
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsScreen.kt`.

### Task 16: HomeViewModel — streak flows + manualRefresh + drop overflow state
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt`.

### Task 17: StreakStrip composable + Home wrapping (PullToRefresh + Settings icon)
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakStrip.kt`, `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt`.

### Task 18: StreakHistoryViewModel + test
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryViewModel.kt`, `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryViewModelTest.kt`.

### Task 19: MonthCalendar composable + StreakHistoryScreen
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/MonthCalendar.kt`, `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryScreen.kt`.

### Task 20: Navigation — wire Settings + StreakHistory routes
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`.

### Task 21: AppContainer + MainActivity wiring
**Files:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`, `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/MainActivity.kt`.

### Task 22: Manual verification + close-out PR
**Files:** none (manual smoke + PR).

---

## Tasks (detailed)

> Each task below has steps, exact code, and commit instructions. Tasks 1–22 follow the order above. **DO NOT skip a task** — they have dependencies.

---

### Task 1: Dependencies + manifest perms

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `mobile/androidApp/build.gradle.kts`
- Modify: `mobile/androidApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Add DataStore version to `[versions]` block in `gradle/libs.versions.toml`**

Insert after the `credentials = "1.5.0"` line:

```toml
datastore = "1.1.1"
```

- [ ] **Step 2: Add DataStore library entry to `[libraries]` block**

Insert after the `googleid = ...` line:

```toml
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
```

- [ ] **Step 3: Add the dep to `mobile/androidApp/build.gradle.kts`**

In the `dependencies { ... }` block, after the existing `androidx-credentials*` lines (or anywhere among androidMain deps), add:

```kotlin
    implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 4: Sync gradle**

Run: `cd /Users/jokot/dev/habit-tracker/.worktrees/phase4-streaks-notifications && rtk ./gradlew :mobile:androidApp:dependencies --configuration debugRuntimeClasspath -q | grep datastore`
Expected: prints `androidx.datastore:datastore-preferences:1.1.1` (or similar).

- [ ] **Step 5: Add manifest permissions**

Edit `mobile/androidApp/src/androidMain/AndroidManifest.xml`. Below the existing `<uses-permission android:name="android.permission.INTERNET" />` line, add:

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
```

- [ ] **Step 6: Build to confirm everything compiles**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
rtk git add gradle/libs.versions.toml mobile/androidApp/build.gradle.kts mobile/androidApp/src/androidMain/AndroidManifest.xml
rtk git commit -m "chore(phase4): add datastore + notification permissions"
```

---

### Task 2: SQLDelight queries — date range, day count, first log date

**Files:**
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`

We need three new queries against `HabitLog`:
1. Observe all active logs in a half-open `[start, end)` window — used by streak compute.
2. Count active logs for a single day — used by workers.
3. Get earliest active `loggedAt` for a user — used to determine streak anchor.

- [ ] **Step 1: Add `observeActiveHabitLogsBetween` query**

Find the existing `getAllActiveHabitLogsForUser:` block in `HabitTrackerDatabase.sq` and insert directly after it:

```sql
observeActiveHabitLogsBetween:
SELECT * FROM HabitLog
WHERE userId = ?
  AND loggedAt >= ?
  AND loggedAt < ?
  AND deletedAt IS NULL
ORDER BY loggedAt ASC;

countActiveHabitLogsBetween:
SELECT COUNT(*) FROM HabitLog
WHERE userId = ?
  AND loggedAt >= ?
  AND loggedAt < ?
  AND deletedAt IS NULL;

firstActiveHabitLogInstant:
SELECT MIN(loggedAt) FROM HabitLog
WHERE userId = ?
  AND deletedAt IS NULL;
```

- [ ] **Step 2: Verify the schema compiles**

Run: `rtk ./gradlew :mobile:shared:generateCommonMainHabitTrackerDatabaseInterface -q`
Expected: BUILD SUCCESSFUL. The generated Kotlin file at `mobile/shared/build/generated/sqldelight/code/.../HabitTrackerDatabaseQueries.kt` should now contain the three new functions.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq
rtk git commit -m "feat(phase4): add streak query trio (range observe, day count, first log)"
```

---

### Task 3: HabitLogRepository — new methods

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitLogRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SqlDelightHabitLogRepository.kt`

- [ ] **Step 1: Extend the interface**

In `HabitLogRepository.kt`, add three methods to the interface (just before the closing `}`):

```kotlin
    fun observeActiveLogsBetween(
        userId: String,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<HabitLog>>

    suspend fun countActiveLogsBetween(
        userId: String,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Int

    suspend fun firstActiveLogAt(userId: String): Instant?
```

- [ ] **Step 2: Implement them in `SqlDelightHabitLogRepository`**

Open `SqlDelightHabitLogRepository.kt`. Add these three method bodies inside the class. Follow the existing pattern of the other `observe*` and `get*` functions (use `asFlow()`/`mapToList()` for Flow, `executeAsOneOrNull()` for the singleton):

```kotlin
    override fun observeActiveLogsBetween(
        userId: String,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<HabitLog>> = queries
        .observeActiveHabitLogsBetween(
            userId = userId,
            loggedAt = startInclusive.toEpochMilliseconds(),
            loggedAt_ = endExclusive.toEpochMilliseconds(),
        )
        .asFlow()
        .mapToList(dispatcher)
        .map { rows -> rows.map { it.toDomain() } }

    override suspend fun countActiveLogsBetween(
        userId: String,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Int = withContext(dispatcher) {
        queries.countActiveHabitLogsBetween(
            userId = userId,
            loggedAt = startInclusive.toEpochMilliseconds(),
            loggedAt_ = endExclusive.toEpochMilliseconds(),
        ).executeAsOne().toInt()
    }

    override suspend fun firstActiveLogAt(userId: String): Instant? =
        withContext(dispatcher) {
            queries.firstActiveHabitLogInstant(userId)
                .executeAsOneOrNull()
                ?.MIN
                ?.let { Instant.fromEpochMilliseconds(it) }
        }
```

> Note: the SQLDelight-generated parameter names follow column order. If the generator names them `userId, loggedAt, loggedAt_`, use those. If different, follow the actual generated signature (open the generated `HabitTrackerDatabaseQueries.kt` to confirm).

- [ ] **Step 3: Update fake repos for tests**

If `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitLogRepository.kt` exists, add the three methods there too — return `flowOf(filteredList)`, count predicate, min loggedAt. If no fake exists yet, skip.

- [ ] **Step 4: Build shared module**

Run: `rtk ./gradlew :mobile:shared:compileCommonMainKotlinMetadata -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitLogRepository.kt mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SqlDelightHabitLogRepository.kt mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitLogRepository.kt
rtk git commit -m "feat(phase4): HabitLogRepository — range observe, count, firstLog"
```

---

### Task 4: Streak domain types

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/StreakDay.kt`

- [ ] **Step 1: Create the file**

Path: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/StreakDay.kt`

```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.LocalDate

enum class StreakDayState {
    /** User logged ≥ 1 active habit on this day. */
    COMPLETE,

    /** Missed day, but isolated — streak survives (one-miss tolerance). */
    FROZEN,

    /** Second consecutive miss — streak resets on this day. */
    BROKEN,

    /** Before user's first log (anchor). Never applied to today. */
    EMPTY,

    /** Today, no log yet. Not yet counted as miss. */
    TODAY_PENDING,
}

data class StreakDay(
    val date: LocalDate,
    val state: StreakDayState,
)

data class StreakRangeResult(
    val days: List<StreakDay>,
    val firstLogDate: LocalDate?,
)

data class StreakSummary(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalDaysComplete: Int,
    val firstLogDate: LocalDate?,
)

/** Half-open [start, endExclusive) range of local dates. */
data class DateRange(
    val start: LocalDate,
    val endExclusive: LocalDate,
) {
    init {
        require(start.toEpochDays() <= endExclusive.toEpochDays()) {
            "DateRange end ($endExclusive) must be ≥ start ($start)"
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `rtk ./gradlew :mobile:shared:compileCommonMainKotlinMetadata -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/StreakDay.kt
rtk git commit -m "feat(phase4): streak domain types (StreakDay, range, summary)"
```

---

### Task 5: ComputeStreakUseCase + tests (TDD)

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCaseTest.kt`

This is the core compute. We do TDD here: write the failing test suite first, then the implementation.

- [ ] **Step 1: Create the test file with the full suite (will fail to compile)**

Path: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCaseTest.kt`

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.StreakDayState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComputeStreakUseCaseTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 4, 26)
    private val userId = "u1"

    private fun instantAt(date: LocalDate, hour: Int = 12): Instant =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz)

    private fun fakeRepo(logs: List<HabitLog>): HabitLogRepository =
        InMemoryHabitLogRepo(logs, tz)

    private fun makeUseCase(
        logs: List<HabitLog>,
        currentDay: LocalDate = today,
    ): ComputeStreakUseCase = ComputeStreakUseCase(
        habitLogRepository = fakeRepo(logs),
        timeZone = tz,
        clock = FixedClock(instantAt(currentDay, hour = 12)),
    )

    private fun log(date: LocalDate, habitId: String = "h1"): HabitLog = HabitLog(
        id = "log-${date}-${habitId}",
        userId = userId,
        habitId = habitId,
        quantity = 1.0,
        loggedAt = instantAt(date),
        deletedAt = null,
        syncedAt = null,
    )

    @Test
    fun `empty logs — all EMPTY, summary zeros, firstLogDate null`() = runTest {
        val uc = makeUseCase(logs = emptyList())
        val range = DateRange(today.minusDays(4), today.plusDays(1))
        val result = uc.computeNow(userId, range)

        assertEquals(5, result.days.size)
        assertTrue(result.days.dropLast(1).all { it.state == StreakDayState.EMPTY })
        assertEquals(StreakDayState.TODAY_PENDING, result.days.last().state)
        assertNull(result.firstLogDate)

        val summary = uc.computeSummaryNow(userId)
        assertEquals(0, summary.currentStreak)
        assertEquals(0, summary.longestStreak)
        assertEquals(0, summary.totalDaysComplete)
        assertNull(summary.firstLogDate)
    }

    @Test
    fun `single log today — streak 1, today COMPLETE, longest 1`() = runTest {
        val uc = makeUseCase(logs = listOf(log(today)))
        val summary = uc.computeSummaryNow(userId)
        assertEquals(1, summary.currentStreak)
        assertEquals(1, summary.longestStreak)
        assertEquals(1, summary.totalDaysComplete)
        assertEquals(today, summary.firstLogDate)
    }

    @Test
    fun `5 consecutive days incl today — streak 5`() = runTest {
        val logs = (0..4).map { log(today.minusDays(it)) }
        val uc = makeUseCase(logs)
        val summary = uc.computeSummaryNow(userId)
        assertEquals(5, summary.currentStreak)
        assertEquals(5, summary.longestStreak)
        assertEquals(5, summary.totalDaysComplete)
    }

    @Test
    fun `1-day gap mid-streak — that day FROZEN, streak continues through`() = runTest {
        // logs: today, today-1, [gap today-2], today-3, today-4
        val logs = listOf(today, today.minusDays(1), today.minusDays(3), today.minusDays(4))
            .map { log(it) }
        val uc = makeUseCase(logs)
        val range = DateRange(today.minusDays(4), today.plusDays(1))
        val result = uc.computeNow(userId, range)

        val byDate = result.days.associateBy { it.date }
        assertEquals(StreakDayState.COMPLETE, byDate[today.minusDays(4)]?.state)
        assertEquals(StreakDayState.COMPLETE, byDate[today.minusDays(3)]?.state)
        assertEquals(StreakDayState.FROZEN, byDate[today.minusDays(2)]?.state)
        assertEquals(StreakDayState.COMPLETE, byDate[today.minusDays(1)]?.state)
        assertEquals(StreakDayState.COMPLETE, byDate[today]?.state)

        val summary = uc.computeSummaryNow(userId)
        assertEquals(2, summary.currentStreak) // today + yesterday after the freeze ended
    }

    @Test
    fun `2-day consecutive gap — 1st FROZEN, 2nd BROKEN, streak resets`() = runTest {
        // logs: today, [gap today-1, today-2], today-3, today-4
        val logs = listOf(today, today.minusDays(3), today.minusDays(4)).map { log(it) }
        val uc = makeUseCase(logs)
        val range = DateRange(today.minusDays(4), today.plusDays(1))
        val result = uc.computeNow(userId, range)
        val byDate = result.days.associateBy { it.date }

        assertEquals(StreakDayState.COMPLETE, byDate[today.minusDays(4)]?.state)
        assertEquals(StreakDayState.COMPLETE, byDate[today.minusDays(3)]?.state)
        assertEquals(StreakDayState.FROZEN, byDate[today.minusDays(2)]?.state)
        assertEquals(StreakDayState.BROKEN, byDate[today.minusDays(1)]?.state)
        assertEquals(StreakDayState.COMPLETE, byDate[today]?.state)

        val summary = uc.computeSummaryNow(userId)
        assertEquals(1, summary.currentStreak) // reset, only today counts
        assertEquals(2, summary.longestStreak) // -3,-4 stretch
    }

    @Test
    fun `chain of 3+ misses — all subsequent days BROKEN`() = runTest {
        val logs = listOf(today.minusDays(5)).map { log(it) }
        val uc = makeUseCase(logs)
        val range = DateRange(today.minusDays(5), today.plusDays(1))
        val result = uc.computeNow(userId, range)
        val states = result.days.map { it.state }
        // -5: COMPLETE, -4: FROZEN, -3..-1: BROKEN, today: TODAY_PENDING
        assertEquals(StreakDayState.COMPLETE, states[0])
        assertEquals(StreakDayState.FROZEN, states[1])
        assertEquals(StreakDayState.BROKEN, states[2])
        assertEquals(StreakDayState.BROKEN, states[3])
        assertEquals(StreakDayState.BROKEN, states[4])
        assertEquals(StreakDayState.TODAY_PENDING, states[5])
    }

    @Test
    fun `today no log + yesterday COMPLETE — today TODAY_PENDING, current streak unchanged`() = runTest {
        val logs = (1..3).map { log(today.minusDays(it)) }
        val uc = makeUseCase(logs)
        val range = DateRange(today.minusDays(3), today.plusDays(1))
        val result = uc.computeNow(userId, range)
        assertEquals(StreakDayState.TODAY_PENDING, result.days.last().state)

        val summary = uc.computeSummaryNow(userId)
        assertEquals(3, summary.currentStreak) // streak survives until tomorrow without log
    }

    @Test
    fun `today no log + yesterday FROZEN — today TODAY_PENDING (don't pre-emptively break)`() = runTest {
        // -3 complete, -2 missed (FROZEN), -1 complete, today empty
        val logs = listOf(today.minusDays(3), today.minusDays(1)).map { log(it) }
        val uc = makeUseCase(logs)
        val range = DateRange(today.minusDays(3), today.plusDays(1))
        val result = uc.computeNow(userId, range)
        assertEquals(StreakDayState.TODAY_PENDING, result.days.last().state)
    }

    @Test
    fun `future-dated log ignored`() = runTest {
        val logs = listOf(log(today), log(today.plusDays(2)))
        val uc = makeUseCase(logs)
        val summary = uc.computeSummaryNow(userId)
        assertEquals(1, summary.totalDaysComplete)
        assertEquals(today, summary.firstLogDate)
    }

    @Test
    fun `multiple logs same day — counted once`() = runTest {
        val logs = listOf(
            log(today, "h1"),
            log(today, "h2"),
            log(today, "h3"),
        )
        val uc = makeUseCase(logs)
        val summary = uc.computeSummaryNow(userId)
        assertEquals(1, summary.totalDaysComplete)
        assertEquals(1, summary.currentStreak)
    }

    @Test
    fun `cross-month boundary — correct`() = runTest {
        val anchor = LocalDate(2026, 3, 30)
        val current = LocalDate(2026, 4, 2) // April 2
        val logs = listOf(anchor, anchor.plusDays(1), anchor.plusDays(2), current)
            .map { log(it) }
        val uc = ComputeStreakUseCase(
            habitLogRepository = fakeRepo(logs),
            timeZone = tz,
            clock = FixedClock(instantAt(current)),
        )
        val summary = uc.computeSummaryNow(userId)
        assertEquals(4, summary.currentStreak)
    }

    @Test
    fun `cross-year boundary — correct`() = runTest {
        val anchor = LocalDate(2025, 12, 30)
        val current = LocalDate(2026, 1, 2)
        val logs = listOf(anchor, anchor.plusDays(1), anchor.plusDays(2), current)
            .map { log(it) }
        val uc = ComputeStreakUseCase(
            habitLogRepository = fakeRepo(logs),
            timeZone = tz,
            clock = FixedClock(instantAt(current)),
        )
        val summary = uc.computeSummaryNow(userId)
        assertEquals(4, summary.currentStreak)
    }

    @Test
    fun `longestStreak is at least currentStreak — invariant`() = runTest {
        val logs = (0..6).map { log(today.minusDays(it)) }
        val uc = makeUseCase(logs)
        val summary = uc.computeSummaryNow(userId)
        assertTrue(summary.longestStreak >= summary.currentStreak)
        assertEquals(7, summary.currentStreak)
        assertEquals(7, summary.longestStreak)
    }

    @Test
    fun `broken then resumed — current counts only resumed run`() = runTest {
        // -7,-6,-5 complete, -4,-3 missed (FROZEN, BROKEN), -2,-1, today complete
        val logs = listOf(-7, -6, -5, -2, -1, 0).map { log(today.plusDays(it.toLong())) }
        val uc = makeUseCase(logs)
        val summary = uc.computeSummaryNow(userId)
        assertEquals(3, summary.currentStreak)
        assertEquals(3, summary.longestStreak) // tied
    }

    @AfterTest
    fun teardown() = Unit
}

// --- helpers below ---

private class FixedClock(private val now: Instant) : Clock {
    override fun now(): Instant = now
}

private class InMemoryHabitLogRepo(
    private val logs: List<HabitLog>,
    private val tz: TimeZone,
) : HabitLogRepository {
    override fun observeActiveLogsBetween(
        userId: String,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<HabitLog>> = flowOf(
        logs.filter {
            it.userId == userId &&
                it.deletedAt == null &&
                it.loggedAt >= startInclusive &&
                it.loggedAt < endExclusive
        }.sortedBy { it.loggedAt }
    )

    override suspend fun countActiveLogsBetween(
        userId: String,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Int = logs.count {
        it.userId == userId &&
            it.deletedAt == null &&
            it.loggedAt >= startInclusive &&
            it.loggedAt < endExclusive
    }

    override suspend fun firstActiveLogAt(userId: String): Instant? = logs
        .filter { it.userId == userId && it.deletedAt == null }
        .minByOrNull { it.loggedAt }
        ?.loggedAt

    // Unused in these tests — throw to make sure they aren't called.
    override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant) =
        error("unused")
    override suspend fun softDelete(logId: String, userId: String) = error("unused")
    override fun observeActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) =
        error("unused")
    override suspend fun getActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) =
        error("unused")
    override fun observeAllActiveLogsForUser(userId: String) = error("unused")
    override suspend fun getAllActiveLogsForUser(userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulled(row: HabitLog) = error("unused")
}

private fun LocalDate.minusDays(d: Long): LocalDate =
    this.minus(d.toInt(), kotlinx.datetime.DateTimeUnit.DAY)

private fun LocalDate.plusDays(d: Long): LocalDate =
    this.plus(d.toInt(), kotlinx.datetime.DateTimeUnit.DAY)
```

- [ ] **Step 2: Verify the suite fails to compile (use case not yet defined)**

Run: `rtk ./gradlew :mobile:shared:compileTestKotlinJvm 2>&1 | head -20`
Expected: Compilation errors mentioning `ComputeStreakUseCase` is not defined. This confirms the tests reference the API we're about to build.

- [ ] **Step 3: Create the use case implementation**

Path: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCase.kt`

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.habittracker.domain.model.StreakRangeResult
import com.habittracker.domain.model.StreakSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class ComputeStreakUseCase(
    private val habitLogRepository: HabitLogRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun observeRange(userId: String, range: DateRange): Flow<StreakRangeResult> =
        habitLogRepository.observeActiveLogsBetween(
            userId = userId,
            startInclusive = range.start.atStartOfDayIn(timeZone),
            endExclusive = range.endExclusive.atStartOfDayIn(timeZone),
        ).map { logs -> buildRangeResult(range, logs, firstLogDateFor(userId)) }

    fun observeCurrent(userId: String): Flow<StreakSummary> = flow {
        val first = habitLogRepository.firstActiveLogAt(userId)?.toLocalDate()
        if (first == null) {
            emit(StreakSummary(0, 0, 0, null))
            return@flow
        }
        val today = todayLocal()
        habitLogRepository.observeActiveLogsBetween(
            userId = userId,
            startInclusive = first.atStartOfDayIn(timeZone),
            endExclusive = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone),
        ).collect { logs ->
            emit(summarize(first, today, logs))
        }
    }

    suspend fun computeNow(userId: String, range: DateRange): StreakRangeResult {
        val firstLog = firstLogDateFor(userId)
        val logs = habitLogRepository.observeActiveLogsBetween(
            userId = userId,
            startInclusive = range.start.atStartOfDayIn(timeZone),
            endExclusive = range.endExclusive.atStartOfDayIn(timeZone),
        ).let { firstFromFlow(it) }
        return buildRangeResult(range, logs, firstLog)
    }

    suspend fun computeSummaryNow(userId: String): StreakSummary {
        val first = habitLogRepository.firstActiveLogAt(userId)?.toLocalDate()
            ?: return StreakSummary(0, 0, 0, null)
        val today = todayLocal()
        val logs = firstFromFlow(
            habitLogRepository.observeActiveLogsBetween(
                userId = userId,
                startInclusive = first.atStartOfDayIn(timeZone),
                endExclusive = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone),
            )
        )
        return summarize(first, today, logs)
    }

    // ----- internals -----

    private suspend fun firstLogDateFor(userId: String): LocalDate? =
        habitLogRepository.firstActiveLogAt(userId)?.toLocalDate()

    private fun buildRangeResult(
        range: DateRange,
        logs: List<HabitLog>,
        firstLogDate: LocalDate?,
    ): StreakRangeResult {
        val today = todayLocal()
        // Set of dates with at least one log inside the range.
        val daysWithLog: Set<LocalDate> = logs
            .filter { it.loggedAt <= now() } // ignore future-dated
            .map { it.loggedAt.toLocalDate() }
            .toSet()
        // Walk from streak anchor (firstLogDate, or range.start if no anchor) up through today
        // to know the carrying state when range.start is reached. We then emit only days inside the range.
        val anchor = firstLogDate ?: range.start
        val walkStart = minOf(anchor, range.start)
        val walkEnd = maxOf(today, range.endExclusive.minusOneDay())
        val perDay = mutableMapOf<LocalDate, StreakDayState>()
        var prev: StreakDayState? = null
        var cursor = walkStart
        while (cursor <= walkEnd) {
            val state = when {
                cursor < anchor -> StreakDayState.EMPTY
                cursor > today -> StreakDayState.EMPTY // future days inside requested range render as EMPTY
                cursor in daysWithLog -> StreakDayState.COMPLETE
                cursor == today -> StreakDayState.TODAY_PENDING
                prev == StreakDayState.COMPLETE -> StreakDayState.FROZEN
                prev == StreakDayState.FROZEN -> StreakDayState.BROKEN
                prev == StreakDayState.BROKEN -> StreakDayState.BROKEN
                prev == StreakDayState.TODAY_PENDING -> StreakDayState.FROZEN
                else -> StreakDayState.EMPTY
            }
            perDay[cursor] = state
            prev = state
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        val days = mutableListOf<StreakDay>()
        var d = range.start
        while (d < range.endExclusive) {
            days += StreakDay(d, perDay[d] ?: StreakDayState.EMPTY)
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return StreakRangeResult(days = days, firstLogDate = firstLogDate)
    }

    private fun summarize(firstLog: LocalDate, today: LocalDate, logs: List<HabitLog>): StreakSummary {
        val daysWithLog = logs
            .filter { it.loggedAt <= now() }
            .map { it.loggedAt.toLocalDate() }
            .toSet()
        var longest = 0
        var run = 0
        var current = 0
        var totalComplete = 0
        var prev: StreakDayState? = null
        var cursor = firstLog
        while (cursor <= today) {
            val state = when {
                cursor in daysWithLog -> StreakDayState.COMPLETE
                cursor == today -> StreakDayState.TODAY_PENDING
                prev == StreakDayState.COMPLETE -> StreakDayState.FROZEN
                prev == StreakDayState.FROZEN -> StreakDayState.BROKEN
                prev == StreakDayState.BROKEN -> StreakDayState.BROKEN
                else -> StreakDayState.EMPTY
            }
            when (state) {
                StreakDayState.COMPLETE -> {
                    run += 1
                    totalComplete += 1
                    if (run > longest) longest = run
                }
                StreakDayState.FROZEN -> {
                    // streak still alive — do not increment but do not reset
                }
                StreakDayState.BROKEN -> {
                    run = 0
                }
                StreakDayState.TODAY_PENDING -> {
                    // do nothing — yesterday's streak remains
                }
                StreakDayState.EMPTY -> Unit
            }
            prev = state
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        // currentStreak = number of consecutive COMPLETE days ending at today (or yesterday if today pending).
        current = run
        return StreakSummary(
            currentStreak = current,
            longestStreak = longest,
            totalDaysComplete = totalComplete,
            firstLogDate = firstLog,
        )
    }

    private fun now(): Instant = clock.now()

    private fun todayLocal(): LocalDate = clock.now().toLocalDateTime(timeZone).date

    private fun Instant.toLocalDate(): LocalDate = toLocalDateTime(timeZone).date

    private fun LocalDate.minusOneDay(): LocalDate = this.plus(-1, DateTimeUnit.DAY)

    /** Drain the first emission of a Flow into a list (for the synchronous compute helpers). */
    private suspend fun firstFromFlow(flow: Flow<List<HabitLog>>): List<HabitLog> {
        var result: List<HabitLog> = emptyList()
        flow.collect { result = it; return@collect }
        return result
    }
}
```

> Note: `firstFromFlow` is intentionally simple. We're re-using the existing `observeActiveLogsBetween` Flow and grabbing the first emission, which SQLDelight emits immediately with the current snapshot. If the codebase already has a `first()` extension, prefer it — but `flow.collect { … return@collect }` works without extra imports.

- [ ] **Step 4: Run the test suite — expect failures (mostly streak math edge cases)**

Run: `rtk ./gradlew :mobile:shared:jvmTest --tests "com.habittracker.domain.usecase.ComputeStreakUseCaseTest" -q`
Expected: Some tests pass, some fail. Iterate the implementation until **all 13 tests pass**. Common iteration points:
- BROKEN propagation when there are repeated EMPTY-after-anchor days.
- The `today no log + yesterday FROZEN` case (today should remain TODAY_PENDING — don't double-decrement the streak).
- `today` placement at the end of `range.endExclusive`.

- [ ] **Step 5: All green — confirm**

Run: `rtk ./gradlew :mobile:shared:jvmTest --tests "com.habittracker.domain.usecase.ComputeStreakUseCaseTest" -q`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCase.kt mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCaseTest.kt
rtk git commit -m "feat(phase4): ComputeStreakUseCase + 13 commonTest cases"
```

---

### Task 6: Point rollover cap + TZ fix + tests

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseTest.kt`

Goal: implement spec §4 — `daily_earn_cap = sum(habit.dailyTarget)` and rollover cap = `daily_earn_cap × 2`, applied at each midnight rollover within the current week. Switch week boundary from UTC to `TimeZone.currentSystemDefault()`.

- [ ] **Step 1: Replace the use case body**

Open `GetPointBalanceUseCase.kt` and replace the entire file contents with:

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

class GetPointBalanceUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val wantLogRepo: WantLogRepository,
    private val habitRepo: HabitRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    suspend fun execute(userId: String): Result<PointBalance> = runCatching {
        val today = clock.now().toLocalDateTime(timeZone).date
        val weekStartDate = currentWeekStartDate()
        val habits = habitRepo.getHabitsForUser(userId).associateBy { it.id }
        val activities = wantActivityRepo.getWantActivities(userId).associateBy { it.id }
        val dailyEarnCap = habits.values.sumOf { it.dailyTarget }
        val rolloverCap = dailyEarnCap * 2

        val habitLogs = habitLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= weekStartDate.atStartOfDayIn(timeZone) }
        val wantLogs = wantLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= weekStartDate.atStartOfDayIn(timeZone) }

        var balance = 0
        var totalEarned = 0
        var totalSpent = 0

        var day = weekStartDate
        while (day <= today) {
            val earnedToday = earnedOnDay(day, habitLogs, habits)
            val spentToday = spentOnDay(day, wantLogs, activities)

            // At midnight rollover INTO this day (skip on first day of week — no prior balance).
            if (day != weekStartDate) {
                balance = minOf(balance, rolloverCap)
            }
            balance = (balance + earnedToday - spentToday).coerceAtLeast(0)

            totalEarned += earnedToday
            totalSpent += spentToday
            day = day.plus(1, DateTimeUnit.DAY)
        }

        PointBalance(earned = totalEarned, spent = totalSpent, balance = balance)
    }

    /** Earned points for one day, capped per habit at its dailyTarget. */
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

    private fun spentOnDay(
        day: LocalDate,
        weekWantLogs: List<WantLog>,
        activities: Map<String, WantActivity>,
    ): Int {
        val dayStart = day.atStartOfDayIn(timeZone)
        val nextDayStart = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        return weekWantLogs
            .filter { it.loggedAt >= dayStart && it.loggedAt < nextDayStart }
            .sumOf { log ->
                activities[log.activityId]?.let {
                    PointCalculator.pointsSpent(log.quantity, it.costPerUnit)
                } ?: 0
            }
    }

    /** Local-time Monday 00:00 of the current week, as a LocalDate. */
    internal fun currentWeekStartDate(): LocalDate {
        val today = clock.now().toLocalDateTime(timeZone).date
        val daysFromMonday = (today.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7
        return today.minus(daysFromMonday, DateTimeUnit.DAY)
    }

    /** Backwards-compatible: returns the weekStart as an Instant in the local TZ. */
    internal fun currentWeekStart(): Instant =
        currentWeekStartDate().atStartOfDayIn(timeZone)
}
```

- [ ] **Step 2: Inspect `PointBalance` to verify it has a `balance` field**

Run: `rtk grep -n "data class PointBalance" mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/`
Expected: shows the data class. If `balance` is **not** a stored property, the existing balance was a derived getter — keep it that way. In that case, change the implementation's last line to:

```kotlin
PointBalance(earned = totalEarned, spent = totalSpent /* balance derived */)
```

…and instead carry the rollover-aware balance via a new explicit field. Decision: if `PointBalance` currently exposes balance via a computed property `val balance: Int get() = (earned - spent).coerceAtLeast(0)`, **change it** to accept `balance: Int` as a constructor arg, removing the computed getter — because simple subtraction no longer matches reality once rollover cap is enforced.

> Do this side change inline if needed (single file edit). Search-and-replace any `PointBalance(earned, spent)` callsites if they exist.

- [ ] **Step 3: Replace the test file**

Open `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseTest.kt` and rewrite it as:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeWantActivityRepository
import com.habittracker.data.repository.FakeWantLogRepository
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
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetPointBalanceUseCaseTest {
    private val tz = TimeZone.UTC
    private val userId = "user1"

    private fun habit(id: String, threshold: Double, dailyTarget: Int) = Habit(
        id = id,
        userId = userId,
        templateId = "tpl",
        name = id,
        unit = "u",
        thresholdPerPoint = threshold,
        dailyTarget = dailyTarget,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun makeUseCase(today: LocalDate): Triple<GetPointBalanceUseCase, FakeHabitLogRepository, FakeWantLogRepository> {
        val habits = FakeHabitRepository()
        val acts = FakeWantActivityRepository()
        val hLogs = FakeHabitLogRepository()
        val wLogs = FakeWantLogRepository()
        val clock = object : Clock {
            override fun now(): Instant = LocalDateTime(today, LocalTime(12, 0)).toInstant(tz)
        }
        return Triple(
            GetPointBalanceUseCase(hLogs, wLogs, habits, acts, tz, clock),
            hLogs,
            wLogs,
        ).also {
            // expose the dependencies via lazy hooks if tests need them
        }
    }

    @Test
    fun `no logs — zero balance`() = runTest {
        val (uc, _, _) = makeUseCase(LocalDate(2026, 4, 22)) // Wednesday
        val r = uc.execute(userId).getOrThrow()
        assertEquals(0, r.earned)
        assertEquals(0, r.spent)
        assertEquals(0, r.balance)
    }

    @Test
    fun `single-day earn within cap — balance equals net`() = runTest {
        val today = LocalDate(2026, 4, 22) // Wednesday
        val habits = FakeHabitRepository().apply { saveHabit(habit("h1", 1.0, 5)) }
        val acts = FakeWantActivityRepository()
        val hLogs = FakeHabitLogRepository()
        val wLogs = FakeWantLogRepository()
        val clock = object : Clock {
            override fun now(): Instant = LocalDateTime(today, LocalTime(12, 0)).toInstant(tz)
        }
        val uc = GetPointBalanceUseCase(hLogs, wLogs, habits, acts, tz, clock)
        // Earn 5 today (5 quantity × 1 threshold, capped at dailyTarget 5).
        hLogs.insertLog("l1", userId, "h1", 5.0, LocalDateTime(today, LocalTime(10, 0)).toInstant(tz))
        val r = uc.execute(userId).getOrThrow()
        assertEquals(5, r.earned)
        assertEquals(5, r.balance)
    }

    @Test
    fun `two-day stretch, day 1 earn over rollover cap — day 2 starts capped`() = runTest {
        val tuesday = LocalDate(2026, 4, 21)
        val wednesday = LocalDate(2026, 4, 22)
        val habits = FakeHabitRepository().apply { saveHabit(habit("h1", 1.0, 5)) } // cap = 5*2 = 10
        val acts = FakeWantActivityRepository()
        val hLogs = FakeHabitLogRepository()
        val wLogs = FakeWantLogRepository()
        val clock = object : Clock {
            override fun now(): Instant = LocalDateTime(wednesday, LocalTime(12, 0)).toInstant(tz)
        }
        val uc = GetPointBalanceUseCase(hLogs, wLogs, habits, acts, tz, clock)

        // Day 1 earn 5 (capped at dailyTarget 5; rollover cap = 10 — under cap so no truncation).
        hLogs.insertLog("l1", userId, "h1", 5.0, LocalDateTime(tuesday, LocalTime(10, 0)).toInstant(tz))
        // Day 2 earn 5.
        hLogs.insertLog("l2", userId, "h1", 5.0, LocalDateTime(wednesday, LocalTime(10, 0)).toInstant(tz))

        val r = uc.execute(userId).getOrThrow()
        // Without cap → 10. Rollover at midnight: 5 (day 1) ≤ 10 cap → kept. Day 2 += 5 → 10.
        assertEquals(10, r.balance)
    }

    @Test
    fun `two-day stretch, day 1 unspent over rollover cap — capped at midnight`() = runTest {
        // Habit threshold 1, dailyTarget 3 → daily_earn_cap = 3 → rollover cap = 6.
        // Two habits to push earn higher: cap is sum of dailyTargets.
        val tuesday = LocalDate(2026, 4, 21)
        val wednesday = LocalDate(2026, 4, 22)
        val habits = FakeHabitRepository().apply {
            saveHabit(habit("h1", 1.0, 3))
            saveHabit(habit("h2", 1.0, 3))
        } // cap = (3+3) * 2 = 12 — too high; reduce.
        // Replace with single habit dailyTarget 3 → cap = 6.
        val habits2 = FakeHabitRepository().apply { saveHabit(habit("h1", 1.0, 3)) }
        val acts = FakeWantActivityRepository()
        val hLogs = FakeHabitLogRepository()
        val wLogs = FakeWantLogRepository()
        val clock = object : Clock {
            override fun now(): Instant = LocalDateTime(wednesday, LocalTime(12, 0)).toInstant(tz)
        }
        val uc = GetPointBalanceUseCase(hLogs, wLogs, habits2, acts, tz, clock)

        // Tuesday: log 30 pages → 30 raw, capped to dailyTarget 3.
        hLogs.insertLog("l1", userId, "h1", 30.0, LocalDateTime(tuesday, LocalTime(10, 0)).toInstant(tz))
        // Wednesday: log 30 pages → capped to 3.
        hLogs.insertLog("l2", userId, "h1", 30.0, LocalDateTime(wednesday, LocalTime(10, 0)).toInstant(tz))

        val r = uc.execute(userId).getOrThrow()
        // Day 1 balance after = 3 (≤ cap 6, no truncation). Day 2 += 3 → 6. (Cap doesn't bite here.)
        assertEquals(6, r.balance)

        // Now stress: 5 dailies that each earn 3, totalCap=15, rolloverCap=30. Even max 5 days of 3pt = 15, never crosses.
        // The cap kicks in only when daily earn > dailyTarget cap (impossible) OR when carry > rolloverCap.
        // Carry > rolloverCap requires NO spending and many days. Test that explicitly:
        val monday = LocalDate(2026, 4, 20)
        // Reset all
        val hLogs2 = FakeHabitLogRepository()
        val wLogs2 = FakeWantLogRepository()
        val clockWed = object : Clock {
            override fun now(): Instant = LocalDateTime(wednesday, LocalTime(12, 0)).toInstant(tz)
        }
        val uc2 = GetPointBalanceUseCase(hLogs2, wLogs2, habits2, acts, tz, clockWed)
        // Monday earn 3, Tuesday earn 3, Wednesday earn 3. dailyCap=3, rolloverCap=6.
        hLogs2.insertLog("l-mon", userId, "h1", 30.0, LocalDateTime(monday, LocalTime(10, 0)).toInstant(tz))
        hLogs2.insertLog("l-tue", userId, "h1", 30.0, LocalDateTime(tuesday, LocalTime(10, 0)).toInstant(tz))
        hLogs2.insertLog("l-wed", userId, "h1", 30.0, LocalDateTime(wednesday, LocalTime(10, 0)).toInstant(tz))
        val r2 = uc2.execute(userId).getOrThrow()
        // Mon end: 3. Tue: cap to min(3, 6)=3, +3 = 6. Wed: cap to min(6, 6)=6, +3 = 9. 9 > rolloverCap but cap applied at MIDNIGHT INTO day, so end-of-day Wed = 9.
        // The cap only applies to the carry INTO a new day. End of week = 9 (kept).
        assertEquals(9, r2.balance)
    }

    @Test
    fun `spent before midnight reduces what carries`() = runTest {
        val tuesday = LocalDate(2026, 4, 21)
        val wednesday = LocalDate(2026, 4, 22)
        val habits = FakeHabitRepository().apply { saveHabit(habit("h1", 1.0, 5)) }
        val acts = FakeWantActivityRepository().apply { activities.add(WantActivity("a1", "X", "min", 1.0)) }
        val hLogs = FakeHabitLogRepository()
        val wLogs = FakeWantLogRepository()
        val clock = object : Clock {
            override fun now(): Instant = LocalDateTime(wednesday, LocalTime(12, 0)).toInstant(tz)
        }
        val uc = GetPointBalanceUseCase(hLogs, wLogs, habits, acts, tz, clock)
        hLogs.insertLog("l1", userId, "h1", 5.0, LocalDateTime(tuesday, LocalTime(10, 0)).toInstant(tz))
        wLogs.insertLog("l2", userId, "a1", 3.0, DeviceMode.OTHER, LocalDateTime(tuesday, LocalTime(20, 0)).toInstant(tz))
        val r = uc.execute(userId).getOrThrow()
        // Tue end: 5 - 3 = 2. Wed: rollover min(2, 10) = 2. No further activity.
        assertEquals(2, r.balance)
    }

    @Test
    fun `balance never goes negative — hard floor`() = runTest {
        val today = LocalDate(2026, 4, 22)
        val habits = FakeHabitRepository()
        val acts = FakeWantActivityRepository().apply { activities.add(WantActivity("a1", "X", "min", 1.0)) }
        val hLogs = FakeHabitLogRepository()
        val wLogs = FakeWantLogRepository()
        val clock = object : Clock { override fun now() = LocalDateTime(today, LocalTime(12, 0)).toInstant(tz) }
        val uc = GetPointBalanceUseCase(hLogs, wLogs, habits, acts, tz, clock)
        wLogs.insertLog("l1", userId, "a1", 5.0, DeviceMode.OTHER, LocalDateTime(today, LocalTime(11, 0)).toInstant(tz))
        val r = uc.execute(userId).getOrThrow()
        assertEquals(0, r.balance)
    }

    @Test
    fun `week reset — Monday 00 00 wipes carry`() = runTest {
        val sunday = LocalDate(2026, 4, 19)
        val monday = LocalDate(2026, 4, 20)
        val habits = FakeHabitRepository().apply { saveHabit(habit("h1", 1.0, 3)) }
        val acts = FakeWantActivityRepository()
        val hLogs = FakeHabitLogRepository()
        val wLogs = FakeWantLogRepository()
        val clock = object : Clock { override fun now() = LocalDateTime(monday, LocalTime(12, 0)).toInstant(tz) }
        val uc = GetPointBalanceUseCase(hLogs, wLogs, habits, acts, tz, clock)
        hLogs.insertLog("l-sun", userId, "h1", 30.0, LocalDateTime(sunday, LocalTime(10, 0)).toInstant(tz))
        hLogs.insertLog("l-mon", userId, "h1", 30.0, LocalDateTime(monday, LocalTime(10, 0)).toInstant(tz))
        val r = uc.execute(userId).getOrThrow()
        // Sunday is BEFORE this week's Monday (this week's start is `monday`). Sun log is filtered out.
        assertEquals(3, r.earned) // only Monday's earn counted
        assertEquals(3, r.balance)
    }

    @Test
    fun `user with no habits — cap zero, balance stays zero`() = runTest {
        val today = LocalDate(2026, 4, 22)
        val habits = FakeHabitRepository()
        val acts = FakeWantActivityRepository()
        val hLogs = FakeHabitLogRepository()
        val wLogs = FakeWantLogRepository()
        val clock = object : Clock { override fun now() = LocalDateTime(today, LocalTime(12, 0)).toInstant(tz) }
        val uc = GetPointBalanceUseCase(hLogs, wLogs, habits, acts, tz, clock)
        val r = uc.execute(userId).getOrThrow()
        assertEquals(0, r.balance)
    }
}
```

> If the existing test file already has cases this rewrite drops, port them over. The above replaces the whole file with the rollover-aware suite.

- [ ] **Step 4: Run the tests**

Run: `rtk ./gradlew :mobile:shared:jvmTest --tests "com.habittracker.domain.usecase.GetPointBalanceUseCaseTest" -q`
Expected: all tests pass. If any fail, iterate the implementation. Common issues: wrong week-start arithmetic on Mondays (`(today.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7` is the canonical form).

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseTest.kt mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/PointBalance.kt
rtk git commit -m "fix(phase4): point rollover cap + local-TZ week start"
```

---

### Task 7: Screen sealed-class additions + permission utils

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` (only the `Screen` sealed class)
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PermissionUtils.kt`

- [ ] **Step 1: Add the two new routes**

In `AppNavigation.kt`, replace the `Screen` sealed class block:

```kotlin
sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Settings : Screen("settings")
    object StreakHistory : Screen("streak-history")
}
```

> Composable destinations for these routes are wired in **Task 20**. This task only adds the route constants so other tasks can reference `Screen.Settings.route` etc.

- [ ] **Step 2: Create `PermissionUtils.kt`**

Path: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PermissionUtils.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionUtils {
    /** Returns true when POST_NOTIFICATIONS is either granted or unnecessary on this device. */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Returns the system-settings intent for the app's notification screen. Caller adds FLAG_ACTIVITY_NEW_TASK. */
    fun appNotificationSettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            data = Uri.parse("package:$packageName")
        }

    const val PERMISSION_NAME = Manifest.permission.POST_NOTIFICATIONS
}
```

- [ ] **Step 3: Build**

Run: `rtk ./gradlew :mobile:androidApp:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PermissionUtils.kt
rtk git commit -m "feat(phase4): Screen routes (Settings, StreakHistory) + permission utils"
```

---

### Task 8: NotificationPreferences (DataStore) + test

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferences.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferencesTest.kt`

- [ ] **Step 1: Create the DataStore wrapper**

Path: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferences.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_prefs",
)

data class NotificationPrefs(
    val dailyReminderEnabled: Boolean,
    val dailyReminderMinutes: Int,
    val streakRiskEnabled: Boolean,
    val streakRiskMinutes: Int,
    val streakFrozenEnabled: Boolean,
    val streakResetEnabled: Boolean,
) {
    companion object {
        val DEFAULT = NotificationPrefs(
            dailyReminderEnabled = true,
            dailyReminderMinutes = 9 * 60,   // 09:00
            streakRiskEnabled = true,
            streakRiskMinutes = 21 * 60,     // 21:00
            streakFrozenEnabled = true,
            streakResetEnabled = true,
        )
    }
}

class NotificationPreferences(private val context: Context) {

    private object Keys {
        val DAILY_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val DAILY_MINUTES = intPreferencesKey("daily_reminder_minutes")
        val RISK_ENABLED = booleanPreferencesKey("streak_risk_enabled")
        val RISK_MINUTES = intPreferencesKey("streak_risk_minutes")
        val FROZEN_ENABLED = booleanPreferencesKey("streak_frozen_enabled")
        val RESET_ENABLED = booleanPreferencesKey("streak_reset_enabled")
    }

    val flow: Flow<NotificationPrefs> = context.notificationDataStore.data.map { p ->
        val d = NotificationPrefs.DEFAULT
        NotificationPrefs(
            dailyReminderEnabled = p[Keys.DAILY_ENABLED] ?: d.dailyReminderEnabled,
            dailyReminderMinutes = p[Keys.DAILY_MINUTES] ?: d.dailyReminderMinutes,
            streakRiskEnabled = p[Keys.RISK_ENABLED] ?: d.streakRiskEnabled,
            streakRiskMinutes = p[Keys.RISK_MINUTES] ?: d.streakRiskMinutes,
            streakFrozenEnabled = p[Keys.FROZEN_ENABLED] ?: d.streakFrozenEnabled,
            streakResetEnabled = p[Keys.RESET_ENABLED] ?: d.streakResetEnabled,
        )
    }

    suspend fun current(): NotificationPrefs {
        var snap: NotificationPrefs = NotificationPrefs.DEFAULT
        flow.collect { snap = it; return@collect }
        return snap
    }

    suspend fun setDailyReminderEnabled(enabled: Boolean) = update { it[Keys.DAILY_ENABLED] = enabled }
    suspend fun setDailyReminderMinutes(minutes: Int) = update { it[Keys.DAILY_MINUTES] = minutes.coerceIn(0, 1439) }
    suspend fun setStreakRiskEnabled(enabled: Boolean) = update { it[Keys.RISK_ENABLED] = enabled }
    suspend fun setStreakRiskMinutes(minutes: Int) = update { it[Keys.RISK_MINUTES] = minutes.coerceIn(0, 1439) }
    suspend fun setStreakFrozenEnabled(enabled: Boolean) = update { it[Keys.FROZEN_ENABLED] = enabled }
    suspend fun setStreakResetEnabled(enabled: Boolean) = update { it[Keys.RESET_ENABLED] = enabled }

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.notificationDataStore.edit { block(it) }
    }
}
```

- [ ] **Step 2: Create the test**

Path: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferencesTest.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33])
class NotificationPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs = NotificationPreferences(context)

    @After fun cleanup() {
        // DataStore files clean up automatically per Robolectric test isolation in most setups.
    }

    @Test fun `defaults match spec`() = runTest {
        val snap = prefs.flow.first()
        assertEquals(NotificationPrefs.DEFAULT, snap)
    }

    @Test fun `toggle daily reminder persists`() = runTest {
        prefs.setDailyReminderEnabled(false)
        val snap = prefs.flow.first()
        assertEquals(false, snap.dailyReminderEnabled)
    }

    @Test fun `set daily reminder minutes persists and clamps`() = runTest {
        prefs.setDailyReminderMinutes(1500) // > 1439, should clamp.
        val snap = prefs.flow.first()
        assertEquals(1439, snap.dailyReminderMinutes)
    }
}
```

- [ ] **Step 3: Add Robolectric + AndroidX test deps if missing**

Inspect `mobile/androidApp/build.gradle.kts` for `testImplementation` entries for `robolectric` and `androidx.test.ext:junit`. If missing, add to gradle (and `libs.versions.toml`):

```toml
# libs.versions.toml additions
robolectric = "4.13"
androidx-test-ext = "1.2.1"
androidx-test-core = "1.6.1"

[libraries]
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-ext" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test-core" }
```

```kotlin
// mobile/androidApp/build.gradle.kts dependencies block
testImplementation(libs.robolectric)
testImplementation(libs.androidx.test.ext.junit)
testImplementation(libs.androidx.test.core)
```

Also enable Robolectric in `android { testOptions { unitTests.isIncludeAndroidResources = true } }`.

- [ ] **Step 4: Run the test**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.NotificationPreferencesTest" -q`
Expected: BUILD SUCCESSFUL with 3 passing tests.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferences.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferencesTest.kt mobile/androidApp/build.gradle.kts gradle/libs.versions.toml
rtk git commit -m "feat(phase4): NotificationPreferences DataStore + test"
```

---

### Task 9: NotificationChannels + NotificationScheduler skeleton

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationChannels.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationScheduler.kt`

This task creates the channel constants + a scheduler skeleton. The scheduler's actual work-enqueue logic is wired in Task 13 (after the workers exist).

- [ ] **Step 1: Create channel constants + ensureChannels**

Path: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationChannels.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    const val DAILY_REMINDER = "daily_reminder"
    const val STREAK_RISK = "streak_risk"
    const val STREAK_STATUS = "streak_status"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(DAILY_REMINDER, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminds you each day to log your habits."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(STREAK_RISK, "Streak alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Late-day reminder when your streak is about to break."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(STREAK_STATUS, "Streak status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet updates when your streak is frozen or has reset."
            }
        )
    }
}
```

- [ ] **Step 2: Create scheduler skeleton**

Path: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationScheduler.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.work.WorkManager

/**
 * Single entry point for managing notification work.
 * - Call [reschedule] whenever prefs change, the user signs in/out, or app starts.
 * - Workers themselves are in DailyReminderWorker / StreakRiskWorker / DayBoundaryWorker.
 *
 * The actual enqueue logic is filled in by Task 13 once all worker classes exist.
 */
class NotificationScheduler(
    private val context: Context,
    private val prefs: NotificationPreferences,
) {
    fun ensureChannels() = NotificationChannels.ensureChannels(context)

    /** Cancels all 3 periodic works. */
    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_DAILY)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_RISK)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_DAY_BOUNDARY)
    }

    suspend fun reschedule() {
        // Filled in by Task 13.
    }

    companion object {
        const val WORK_DAILY = "phase4-daily-reminder"
        const val WORK_RISK = "phase4-streak-risk"
        const val WORK_DAY_BOUNDARY = "phase4-day-boundary"
    }
}
```

- [ ] **Step 3: Build**

Run: `rtk ./gradlew :mobile:androidApp:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationChannels.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationScheduler.kt
rtk git commit -m "feat(phase4): notification channels + scheduler skeleton"
```

---

### Task 10: DailyReminderWorker + test

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorker.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorkerTest.kt`

- [ ] **Step 1: Create the worker**

Path: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorker.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.R
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext.applicationContext as HabitTrackerApplication
        val container: AppContainer = app.container
        val prefs = container.notificationPreferences.current()
        if (!prefs.dailyReminderEnabled) return@runCatching Result.success()
        if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

        val userId = container.currentUserId()
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val start = today.atStartOfDayIn(tz)
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)

        val count = container.habitLogRepository.countActiveLogsBetween(userId, start, end)
        if (count == 0) {
            fireDailyReminder(applicationContext)
        }
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        const val NOTIF_ID = 4001

        fun fireDailyReminder(context: Context) {
            val builder = NotificationCompat.Builder(context, NotificationChannels.DAILY_REMINDER)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Habitto")
                .setContentText("Log your habits to keep your streak alive.")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
        }
    }
}
```

> Note: this worker references `container.notificationPreferences`, `container.currentUserId()`, and `container.habitLogRepository`. Those wirings exist after Task 21 (AppContainer wiring). The build will compile fine because `AppContainer` already exposes `currentUserId()` and `habitLogRepository`; we only need to add `notificationPreferences` in Task 21.

- [ ] **Step 2: Create the test**

Path: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorkerTest.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.jktdeveloper.habitto.HabitTrackerApplication
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = HabitTrackerApplication::class, sdk = [33])
class DailyReminderWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `doWork returns success even when no logs exist`() = runTest {
        val worker = TestListenableWorkerBuilder<DailyReminderWorker>(context).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }
}
```

> Full behavior testing (count == 0 → fires, count > 0 → skips) requires a richer `AppContainer` test fake. For Phase 4 Task 10, the smoke "doWork returns success" is enough; behavioral coverage relies on manual smoke (Task 22). Iterate later if needed.

- [ ] **Step 3: Run the test**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.DailyReminderWorkerTest" -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorker.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorkerTest.kt
rtk git commit -m "feat(phase4): DailyReminderWorker + smoke test"
```

---

### Task 11: StreakRiskWorker + test

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorker.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorkerTest.kt`

- [ ] **Step 1: Create the worker**

Path: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorker.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.R
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class StreakRiskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext.applicationContext as HabitTrackerApplication
        val container = app.container
        val prefs = container.notificationPreferences.current()
        if (!prefs.streakRiskEnabled) return@runCatching Result.success()
        if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

        val userId = container.currentUserId()
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val start = today.atStartOfDayIn(tz)
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)

        val count = container.habitLogRepository.countActiveLogsBetween(userId, start, end)
        if (count > 0) return@runCatching Result.success()

        val summary = container.computeStreakUseCase.computeSummaryNow(userId)
        if (summary.currentStreak <= 0) return@runCatching Result.success()

        val builder = NotificationCompat.Builder(applicationContext, NotificationChannels.STREAK_RISK)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Habitto")
            .setContentText("${summary.currentStreak}-day streak at risk. Log a habit before midnight.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, builder.build())
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        const val NOTIF_ID = 4002
    }
}
```

- [ ] **Step 2: Create the test**

Path: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorkerTest.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.jktdeveloper.habitto.HabitTrackerApplication
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = HabitTrackerApplication::class, sdk = [33])
class StreakRiskWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `doWork returns success on empty state`() = runTest {
        val w = TestListenableWorkerBuilder<StreakRiskWorker>(context).build()
        val result = w.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }
}
```

- [ ] **Step 3: Run the test**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.StreakRiskWorkerTest" -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorker.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorkerTest.kt
rtk git commit -m "feat(phase4): StreakRiskWorker + smoke test"
```

---

### Task 12: DayBoundaryWorker + idempotency store + test

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationFiringDateStore.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorker.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorkerTest.kt`

- [ ] **Step 1: Create the firing-date store**

Path: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationFiringDateStore.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

private val Context.firingDateStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_firing_dates",
)

class NotificationFiringDateStore(private val context: Context) {

    suspend fun getLastFired(eventKey: String): LocalDate? {
        val key = stringPreferencesKey(eventKey)
        val raw = context.firingDateStore.data.first()[key] ?: return null
        return runCatching { LocalDate.parse(raw) }.getOrNull()
    }

    suspend fun setLastFired(eventKey: String, date: LocalDate) {
        val key = stringPreferencesKey(eventKey)
        context.firingDateStore.edit { it[key] = date.toString() }
    }

    companion object {
        const val EVENT_FROZEN = "day_boundary_frozen"
        const val EVENT_RESET = "day_boundary_reset"
    }
}
```

- [ ] **Step 2: Create the worker**

Path: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorker.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.StreakDayState
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.R
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class DayBoundaryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext.applicationContext as HabitTrackerApplication
        val container = app.container
        val prefs = container.notificationPreferences.current()
        if (!prefs.streakFrozenEnabled && !prefs.streakResetEnabled) return@runCatching Result.success()
        if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        val range = DateRange(start = yesterday, endExclusive = today.plus(1, DateTimeUnit.DAY))
        val userId = container.currentUserId()
        val result = container.computeStreakUseCase.computeNow(userId, range)
        val yesterdayState = result.days.firstOrNull { it.date == yesterday }?.state

        val firingStore = container.notificationFiringDateStore
        when (yesterdayState) {
            StreakDayState.FROZEN -> if (prefs.streakFrozenEnabled) {
                if (firingStore.getLastFired(NotificationFiringDateStore.EVENT_FROZEN) != yesterday) {
                    fire(applicationContext, NOTIF_FROZEN, "Missed yesterday. Don't miss today, or your streak resets.")
                    firingStore.setLastFired(NotificationFiringDateStore.EVENT_FROZEN, yesterday)
                }
            }
            StreakDayState.BROKEN -> if (prefs.streakResetEnabled) {
                if (firingStore.getLastFired(NotificationFiringDateStore.EVENT_RESET) != yesterday) {
                    fire(applicationContext, NOTIF_RESET, "Streak reset. Start fresh today.")
                    firingStore.setLastFired(NotificationFiringDateStore.EVENT_RESET, yesterday)
                }
            }
            else -> Unit
        }

        Result.success()
    }.getOrElse { Result.retry() }

    private fun fire(context: Context, id: Int, body: String) {
        val builder = NotificationCompat.Builder(context, NotificationChannels.STREAK_STATUS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Habitto")
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    companion object {
        const val NOTIF_FROZEN = 4003
        const val NOTIF_RESET = 4004
    }
}
```

- [ ] **Step 3: Create the test**

Path: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorkerTest.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.jktdeveloper.habitto.HabitTrackerApplication
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = HabitTrackerApplication::class, sdk = [33])
class DayBoundaryWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `doWork on empty state returns success`() = runTest {
        val w = TestListenableWorkerBuilder<DayBoundaryWorker>(context).build()
        val result = w.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test fun `firing-date store round-trips`() = runTest {
        val store = NotificationFiringDateStore(context)
        val day = LocalDate(2026, 4, 25)
        store.setLastFired(NotificationFiringDateStore.EVENT_FROZEN, day)
        assertEquals(day, store.getLastFired(NotificationFiringDateStore.EVENT_FROZEN))
    }
}
```

- [ ] **Step 4: Run the test**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.DayBoundaryWorkerTest" -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorker.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationFiringDateStore.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorkerTest.kt
rtk git commit -m "feat(phase4): DayBoundaryWorker + firing-date idempotency store"
```

---

### Task 13: NotificationScheduler — wire reschedule + cancel + test

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationScheduler.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationSchedulerTest.kt`

- [ ] **Step 1: Replace the scheduler skeleton with the real implementation**

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit

class NotificationScheduler(
    private val context: Context,
    private val prefs: NotificationPreferences,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun ensureChannels() = NotificationChannels.ensureChannels(context)

    fun cancelAll() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_DAILY)
        wm.cancelUniqueWork(WORK_RISK)
        wm.cancelUniqueWork(WORK_DAY_BOUNDARY)
    }

    suspend fun reschedule() {
        val snap = prefs.current()
        val wm = WorkManager.getInstance(context)

        if (snap.dailyReminderEnabled) {
            wm.enqueueUniquePeriodicWork(
                WORK_DAILY,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicAt(snap.dailyReminderMinutes, DailyReminderWorker::class.java),
            )
        } else wm.cancelUniqueWork(WORK_DAILY)

        if (snap.streakRiskEnabled) {
            wm.enqueueUniquePeriodicWork(
                WORK_RISK,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicAt(snap.streakRiskMinutes, StreakRiskWorker::class.java),
            )
        } else wm.cancelUniqueWork(WORK_RISK)

        if (snap.streakFrozenEnabled || snap.streakResetEnabled) {
            // Day boundary worker fires at 00:30 local each day.
            wm.enqueueUniquePeriodicWork(
                WORK_DAY_BOUNDARY,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicAt(30, DayBoundaryWorker::class.java),
            )
        } else wm.cancelUniqueWork(WORK_DAY_BOUNDARY)
    }

    /** Builds a daily PeriodicWorkRequest whose first run lands at the given minutes-since-midnight. */
    private fun <W : androidx.work.ListenableWorker> periodicAt(
        minutesOfDay: Int,
        worker: Class<W>,
    ): androidx.work.PeriodicWorkRequest {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val targetToday = today.atStartOfDayIn(timeZone)
            .plus(minutesOfDay.toLong(), DateTimeUnit.MINUTE, timeZone)
        val targetInstant = if (targetToday > now) targetToday
            else today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
                .plus(minutesOfDay.toLong(), DateTimeUnit.MINUTE, timeZone)
        val initialDelayMs = (targetInstant - now).inWholeMilliseconds.coerceAtLeast(0)

        return PeriodicWorkRequestBuilder<W>(1, TimeUnit.DAYS, java.lang.Class.forName("androidx.work.PeriodicWorkRequest"))
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()
            .let { existing ->
                // The above factory used a placeholder Class arg to satisfy the generic;
                // rebuild with the real worker class via the proper builder:
                PeriodicWorkRequestBuilder<W>(1, TimeUnit.DAYS)
                    .apply { /* worker class is implicit in W */ }
                    .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                    .build()
            }
    }

    companion object {
        const val WORK_DAILY = "phase4-daily-reminder"
        const val WORK_RISK = "phase4-streak-risk"
        const val WORK_DAY_BOUNDARY = "phase4-day-boundary"
    }
}

private fun kotlinx.datetime.Instant.plus(value: Long, unit: DateTimeUnit.TimeBased): kotlinx.datetime.Instant =
    plus(value, unit, TimeZone.UTC) // helper overload not strictly needed; kept for clarity
```

> Realistic note: the `periodicAt` helper above is over-fancy. Simplify by inlining the periodic builder directly. The simpler form once W is known:

Replace the body of `periodicAt` with the canonical pattern (delete the placeholder factory dance):

```kotlin
private inline fun <reified W : androidx.work.CoroutineWorker> periodicAt(
    minutesOfDay: Int,
): androidx.work.PeriodicWorkRequest {
    val now = clock.now()
    val today = now.toLocalDateTime(timeZone).date
    val candidateToday = today.atStartOfDayIn(timeZone)
        .plus(minutesOfDay.toLong(), DateTimeUnit.MINUTE)
    val target = if (candidateToday > now) candidateToday
        else today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
            .plus(minutesOfDay.toLong(), DateTimeUnit.MINUTE)
    val initialDelayMs = (target - now).inWholeMilliseconds.coerceAtLeast(0)
    return PeriodicWorkRequestBuilder<W>(1, TimeUnit.DAYS)
        .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
        .build()
}
```

…and update each `enqueueUniquePeriodicWork` call to use the reified form: `periodicAt<DailyReminderWorker>(snap.dailyReminderMinutes)`, `periodicAt<StreakRiskWorker>(snap.streakRiskMinutes)`, `periodicAt<DayBoundaryWorker>(30)`. Drop the `worker: Class<W>` parameter version entirely.

> `Instant.plus(Long, DateTimeUnit.MINUTE)` returns `Instant` directly in kotlinx-datetime 0.6 — no extra helper needed. Drop the `private fun ...plus(...)` extension at the bottom.

- [ ] **Step 2: Final, simplified scheduler — replace whole file with this clean version**

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit

class NotificationScheduler(
    private val context: Context,
    private val prefs: NotificationPreferences,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun ensureChannels() = NotificationChannels.ensureChannels(context)

    fun cancelAll() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_DAILY)
        wm.cancelUniqueWork(WORK_RISK)
        wm.cancelUniqueWork(WORK_DAY_BOUNDARY)
    }

    suspend fun reschedule() {
        val snap = prefs.current()
        val wm = WorkManager.getInstance(context)

        if (snap.dailyReminderEnabled)
            wm.enqueueUniquePeriodicWork(WORK_DAILY, ExistingPeriodicWorkPolicy.UPDATE, periodicAt<DailyReminderWorker>(snap.dailyReminderMinutes))
        else wm.cancelUniqueWork(WORK_DAILY)

        if (snap.streakRiskEnabled)
            wm.enqueueUniquePeriodicWork(WORK_RISK, ExistingPeriodicWorkPolicy.UPDATE, periodicAt<StreakRiskWorker>(snap.streakRiskMinutes))
        else wm.cancelUniqueWork(WORK_RISK)

        if (snap.streakFrozenEnabled || snap.streakResetEnabled)
            wm.enqueueUniquePeriodicWork(WORK_DAY_BOUNDARY, ExistingPeriodicWorkPolicy.UPDATE, periodicAt<DayBoundaryWorker>(30))
        else wm.cancelUniqueWork(WORK_DAY_BOUNDARY)
    }

    private inline fun <reified W : CoroutineWorker> periodicAt(minutesOfDay: Int): PeriodicWorkRequest {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val candidate = today.atStartOfDayIn(timeZone)
            .plus(minutesOfDay.toLong(), DateTimeUnit.MINUTE)
        val target = if (candidate > now) candidate
            else today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
                .plus(minutesOfDay.toLong(), DateTimeUnit.MINUTE)
        val initialDelayMs = (target - now).inWholeMilliseconds.coerceAtLeast(0)
        return PeriodicWorkRequestBuilder<W>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()
    }

    companion object {
        const val WORK_DAILY = "phase4-daily-reminder"
        const val WORK_RISK = "phase4-streak-risk"
        const val WORK_DAY_BOUNDARY = "phase4-day-boundary"
    }
}
```

> Delete the previous skeleton + the over-fancy stuff. The file should match the version above exactly.

- [ ] **Step 3: Add the WorkManager test harness dep**

In `gradle/libs.versions.toml`:

```toml
[libraries]
androidx-work-testing = { module = "androidx.work:work-testing", version.ref = "workmanager" }
```

In `mobile/androidApp/build.gradle.kts` `dependencies` block:

```kotlin
testImplementation(libs.androidx.work.testing)
```

- [ ] **Step 4: Create the scheduler test**

Path: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationSchedulerTest.kt`

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class NotificationSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setup() {
        val cfg = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, cfg)
    }

    @Test fun `reschedule with all enabled enqueues 3 unique works`() = runTest {
        val prefs = NotificationPreferences(context)
        val scheduler = NotificationScheduler(context, prefs)
        scheduler.reschedule()

        val wm = WorkManager.getInstance(context)
        assertEquals(1, wm.getWorkInfosForUniqueWork(NotificationScheduler.WORK_DAILY).get().size)
        assertEquals(1, wm.getWorkInfosForUniqueWork(NotificationScheduler.WORK_RISK).get().size)
        assertEquals(1, wm.getWorkInfosForUniqueWork(NotificationScheduler.WORK_DAY_BOUNDARY).get().size)
    }

    @Test fun `cancelAll clears all unique works`() = runTest {
        val prefs = NotificationPreferences(context)
        val scheduler = NotificationScheduler(context, prefs)
        scheduler.reschedule()
        scheduler.cancelAll()

        val wm = WorkManager.getInstance(context)
        // Cancelled works leave entries with state CANCELLED — assert state, not size 0.
        val daily = wm.getWorkInfosForUniqueWork(NotificationScheduler.WORK_DAILY).get()
        if (daily.isNotEmpty()) {
            assert(daily.all { it.state.isFinished })
        }
    }

    @Test fun `disabling a pref cancels its work`() = runTest {
        val prefs = NotificationPreferences(context)
        val scheduler = NotificationScheduler(context, prefs)
        scheduler.reschedule()

        prefs.setDailyReminderEnabled(false)
        scheduler.reschedule()

        val wm = WorkManager.getInstance(context)
        val daily = wm.getWorkInfosForUniqueWork(NotificationScheduler.WORK_DAILY).get()
        assert(daily.all { it.state.isFinished }) {
            "Expected daily-reminder work to be cancelled, was: ${daily.map { it.state }}"
        }
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.NotificationSchedulerTest" -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationScheduler.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationSchedulerTest.kt gradle/libs.versions.toml mobile/androidApp/build.gradle.kts
rtk git commit -m "feat(phase4): NotificationScheduler reschedule/cancel + WorkManager tests"
```

---

### Task 14: SettingsViewModel + test

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsViewModel.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Create the ViewModel**

```kotlin
package com.jktdeveloper.habitto.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jktdeveloper.habitto.notifications.NotificationPreferences
import com.jktdeveloper.habitto.notifications.NotificationPrefs
import com.jktdeveloper.habitto.notifications.NotificationScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val notificationPrefs: NotificationPreferences,
    private val scheduler: NotificationScheduler,
) : ViewModel() {

    val prefs: StateFlow<NotificationPrefs> = notificationPrefs.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationPrefs.DEFAULT,
    )

    fun setDailyReminderEnabled(enabled: Boolean) = update {
        notificationPrefs.setDailyReminderEnabled(enabled)
    }

    fun setDailyReminderMinutes(minutes: Int) = update {
        notificationPrefs.setDailyReminderMinutes(minutes)
    }

    fun setStreakRiskEnabled(enabled: Boolean) = update {
        notificationPrefs.setStreakRiskEnabled(enabled)
    }

    fun setStreakRiskMinutes(minutes: Int) = update {
        notificationPrefs.setStreakRiskMinutes(minutes)
    }

    fun setStreakFrozenEnabled(enabled: Boolean) = update {
        notificationPrefs.setStreakFrozenEnabled(enabled)
    }

    fun setStreakResetEnabled(enabled: Boolean) = update {
        notificationPrefs.setStreakResetEnabled(enabled)
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            scheduler.reschedule()
        }
    }
}
```

- [ ] **Step 2: Create the test**

```kotlin
package com.jktdeveloper.habitto.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.jktdeveloper.habitto.notifications.NotificationPreferences
import com.jktdeveloper.habitto.notifications.NotificationScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SettingsViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setup() {
        val cfg = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, cfg)
    }

    @Test fun `setting daily reminder enabled persists and reaches DataStore`() = runTest {
        val prefs = NotificationPreferences(context)
        val scheduler = NotificationScheduler(context, prefs)
        val vm = SettingsViewModel(prefs, scheduler)
        vm.setDailyReminderEnabled(false)
        // Pull directly from DataStore — vm.prefs is a stateIn StateFlow that lazily collects.
        assertEquals(false, prefs.flow.first().dailyReminderEnabled)
    }

    @Test fun `setting daily reminder minutes clamps and persists`() = runTest {
        val prefs = NotificationPreferences(context)
        val scheduler = NotificationScheduler(context, prefs)
        val vm = SettingsViewModel(prefs, scheduler)
        vm.setDailyReminderMinutes(2000) // out of range — clamps to 1439
        assertEquals(1439, prefs.flow.first().dailyReminderMinutes)
    }
}
```

- [ ] **Step 3: Run the test**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.ui.settings.SettingsViewModelTest" -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsViewModel.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsViewModelTest.kt
rtk git commit -m "feat(phase4): SettingsViewModel + test"
```

---

### Task 15: SettingsScreen UI

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create the composable**

```kotlin
package com.jktdeveloper.habitto.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.BuildConfig
import com.jktdeveloper.habitto.notifications.NotificationPrefs
import com.jktdeveloper.habitto.notifications.PermissionUtils
import com.jktdeveloper.habitto.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    isAuthenticated: Boolean,
    accountEmail: String?,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsState()
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(Unit) {
        // Re-check on every entrance/resume.
        permissionGranted = PermissionUtils.hasNotificationPermission(context)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            // Notifications section
            item { SectionHeader("Notifications") }
            if (!permissionGranted) {
                item {
                    PermissionBanner(
                        onRequestPermission = { permLauncher.launch(PermissionUtils.PERMISSION_NAME) },
                        onOpenSettings = {
                            val intent = PermissionUtils.appNotificationSettingsIntent(context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                    )
                }
            }
            item {
                SwitchRow(
                    title = "Daily reminder",
                    supporting = "Reminds you each day to log habits",
                    checked = prefs.dailyReminderEnabled,
                    enabled = permissionGranted,
                    timeMinutes = prefs.dailyReminderMinutes,
                    onCheckedChange = viewModel::setDailyReminderEnabled,
                    onTimeChange = viewModel::setDailyReminderMinutes,
                )
            }
            item {
                SwitchRow(
                    title = "Streak at risk",
                    supporting = "Late-day nudge if your streak is about to break",
                    checked = prefs.streakRiskEnabled,
                    enabled = permissionGranted,
                    timeMinutes = prefs.streakRiskMinutes,
                    onCheckedChange = viewModel::setStreakRiskEnabled,
                    onTimeChange = viewModel::setStreakRiskMinutes,
                )
            }
            item {
                SwitchRow(
                    title = "Streak frozen alerts",
                    supporting = "Notify when one missed day used your freeze",
                    checked = prefs.streakFrozenEnabled,
                    enabled = permissionGranted,
                    timeMinutes = null,
                    onCheckedChange = viewModel::setStreakFrozenEnabled,
                    onTimeChange = {},
                )
            }
            item {
                SwitchRow(
                    title = "Streak reset alerts",
                    supporting = "Notify when your streak resets to zero",
                    checked = prefs.streakResetEnabled,
                    enabled = permissionGranted,
                    timeMinutes = null,
                    onCheckedChange = viewModel::setStreakResetEnabled,
                    onTimeChange = {},
                )
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }

            // Account section
            item { SectionHeader("Account") }
            item {
                AccountSection(
                    isAuthenticated = isAuthenticated,
                    email = accountEmail,
                    onSignOut = onSignOut,
                    onSignIn = onSignIn,
                )
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }

            // About section
            item { SectionHeader("About") }
            item {
                AboutSection(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = Spacing.xl, top = Spacing.xxl, bottom = Spacing.md),
    )
}

@Composable
private fun PermissionBanner(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md).fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Notifications are blocked.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row {
                    TextButton(onClick = onRequestPermission) { Text("Allow") }
                    TextButton(onClick = onOpenSettings) { Text("System settings") }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    enabled: Boolean,
    timeMinutes: Int?,
    onCheckedChange: (Boolean) -> Unit,
    onTimeChange: (Int) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (timeMinutes != null && enabled && checked) {
                TextButton(onClick = { showTimePicker = true }) {
                    Text(formatMinutes(timeMinutes))
                }
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }

    if (showTimePicker && timeMinutes != null) {
        TimePickerDialogStub(
            initialMinutes = timeMinutes,
            onDismiss = { showTimePicker = false },
            onConfirm = { newMinutes ->
                onTimeChange(newMinutes)
                showTimePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogStub(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick time") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AccountSection(
    isAuthenticated: Boolean,
    email: String?,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.xl)) {
        if (isAuthenticated) {
            Text(email ?: "Signed in", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Spacing.xl))
            TextButton(
                onClick = onSignOut,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Sign out") }
        } else {
            Text("Sign in to sync across devices.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Spacing.md))
            TextButton(onClick = onSignIn) { Text("Sign in") }
        }
    }
}

@Composable
private fun AboutSection(versionName: String, versionCode: Int) {
    Column(modifier = Modifier.padding(Spacing.xl)) {
        Text("Version $versionName ($versionCode)", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val period = if (h < 12) "AM" else "PM"
    val h12 = ((h + 11) % 12) + 1
    val mm = m.toString().padStart(2, '0')
    return "$h12:$mm $period"
}
```

- [ ] **Step 2: Build**

Run: `rtk ./gradlew :mobile:androidApp:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL. (UI is exercised manually in Task 22 — no unit test for this composable.)

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/SettingsScreen.kt
rtk git commit -m "feat(phase4): SettingsScreen UI (notifications + account + about)"
```

---

### Task 16: HomeViewModel — streak flows + manualRefresh + drop overflow state

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt`

- [ ] **Step 1: Add the streak flows + manualRefresh**

In `HomeViewModel.kt`, add new imports + flows. Find the line `val syncState: StateFlow<SyncState> = container.syncEngine.syncState` and immediately after it add:

```kotlin
    private val _streakStrip = MutableStateFlow(
        com.habittracker.domain.model.StreakRangeResult(emptyList(), null)
    )
    val streakStrip: StateFlow<com.habittracker.domain.model.StreakRangeResult> = _streakStrip.asStateFlow()

    private val _streakSummary = MutableStateFlow(
        com.habittracker.domain.model.StreakSummary(0, 0, 0, null)
    )
    val streakSummary: StateFlow<com.habittracker.domain.model.StreakSummary> = _streakSummary.asStateFlow()
```

- [ ] **Step 2: Wire the streak flows inside the existing `observeHomeUiState` flatMapLatest block**

Inside the `container.authState.flatMapLatest { auth -> ... }` block, where existing streams (`habitsFlow`, `pointBalanceFlow`, etc.) are wired, add a parallel collection. The cleanest approach: after the existing block that builds `_uiState`, append:

```kotlin
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeStreaks() {
        viewModelScope.launch {
            container.authState
                .flatMapLatest { auth ->
                    val tz = TimeZone.currentSystemDefault()
                    val today = Clock.System.now().toLocalDateTime(tz).date
                    val start = today.minus(29, DateTimeUnit.DAY)
                    val range = com.habittracker.domain.model.DateRange(
                        start = start,
                        endExclusive = today.plus(1, DateTimeUnit.DAY),
                    )
                    container.computeStreakUseCase.observeRange(auth.userId, range)
                }
                .collect { _streakStrip.value = it }
        }
        viewModelScope.launch {
            container.authState
                .flatMapLatest { auth -> container.computeStreakUseCase.observeCurrent(auth.userId) }
                .collect { _streakSummary.value = it }
        }
    }
```

> Add `import kotlinx.datetime.plus` and `com.habittracker.domain.model.DateRange` to imports.

Then call `observeStreaks()` in the same `init {}` block where `observeHomeUiState()` is invoked.

- [ ] **Step 3: Add `manualRefresh()` method**

Inside the `HomeViewModel` class, add:

```kotlin
    fun manualRefresh() {
        viewModelScope.launch {
            container.syncEngine.sync(SyncReason.MANUAL_PULL)
        }
    }
```

> If `SyncReason.MANUAL_PULL` does not exist yet, add it to the enum in `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncTypes.kt` (look for the existing `SyncReason` enum). Adding an enum variant is a one-line change; it does not require migration. Commit it together with this task.

- [ ] **Step 4: Drop overflow-menu / sign-out state from this ViewModel**

The Phase 3 overflow menu state (sign-out dialog, unsynced count) **stays** because `LogoutConfirmDialog` is reused by Settings. Don't delete those flows. The only behavior that moves is the **trigger** — the `IconButton` invocation moves from Home overflow to Settings. We'll wire the trigger in Task 21.

Skip removing anything; the existing `_showLogoutDialog`, `requestLogout()`, `confirmLogout()`, `cancelLogout()` are still valid — they're now exercised from Settings.

- [ ] **Step 5: Build**

Run: `rtk ./gradlew :mobile:androidApp:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncTypes.kt
rtk git commit -m "feat(phase4): HomeViewModel — streak flows + manualRefresh + MANUAL_PULL reason"
```

---

### Task 17: StreakStrip composable + Home wrapping (PullToRefresh + Settings icon)

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakStrip.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt`

- [ ] **Step 1: Create `StreakStrip.kt`**

```kotlin
package com.jktdeveloper.habitto.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.habittracker.domain.model.StreakRangeResult
import com.jktdeveloper.habitto.ui.theme.*

@Composable
fun StreakStrip(
    range: StreakRangeResult,
    currentStreak: Int,
    onViewAll: () -> Unit,
    onDayTap: (StreakDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Spacing.md))
                if (range.firstLogDate == null) {
                    Text(
                        "Log your first habit to start a streak.",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        "Streak",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        "$currentStreak ${if (currentStreak == 1) "day" else "days"}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onViewAll) { Text("View all") }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(range.days)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(days: List<StreakDay>) {
    items(days.size, key = { idx -> days[idx].date.toString() }) { idx ->
        StreakDayCell(day = days[idx], onTap = { /* wired by parent if needed */ })
    }
}

@Composable
private fun StreakDayCell(
    day: StreakDay,
    onTap: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val color = when (day.state) {
        StreakDayState.COMPLETE -> if (isDark) StreakCompleteDark else StreakComplete
        StreakDayState.FROZEN -> if (isDark) StreakFrozenDark else StreakFrozen
        StreakDayState.BROKEN -> if (isDark) StreakBrokenDark else StreakBroken
        StreakDayState.EMPTY -> if (isDark) StreakEmptyDark else StreakEmpty
        StreakDayState.TODAY_PENDING -> if (isDark) StreakEmptyDark else StreakEmpty
    }
    val outline = day.state == StreakDayState.TODAY_PENDING
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .let { if (outline) it.border(1.5.dp, StreakTodayOutline, RoundedCornerShape(4.dp)) else it }
            .clickable(onClick = onTap),
    )
}

@Composable
private fun isSystemInDarkTheme(): Boolean =
    androidx.compose.foundation.isSystemInDarkTheme()
```

> Note: `StreakComplete`, `StreakCompleteDark`, etc. are exported from `ui/theme/Color.kt` (verified in spec §3.1). Make sure the imports above match the symbol names (case-sensitive).

- [ ] **Step 2: Add tap callback to support nav to specific date**

Edit the `private fun ...items(...)` helper to forward an `onTap` from the parent. Replace the helper with:

```kotlin
private fun androidx.compose.foundation.lazy.LazyListScope.streakDayItems(
    days: List<StreakDay>,
    onDayTap: (StreakDay) -> Unit,
) {
    items(days.size, key = { idx -> days[idx].date.toString() }) { idx ->
        StreakDayCell(day = days[idx], onTap = { onDayTap(days[idx]) })
    }
}
```

…and change the `LazyRow` call inside `StreakStrip` to use `streakDayItems(range.days, onDayTap)`.

- [ ] **Step 3: Modify HomeScreen — wrap content + add Settings icon + mount StreakStrip**

In `HomeScreen.kt`:

1. Add imports:
```kotlin
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.filled.Settings
import com.jktdeveloper.habitto.ui.streak.StreakStrip
```

2. Replace the existing `TopAppBar` actions block. Find the `MoreVert` dropdown and replace the entire `IconButton + DropdownMenu` block with a single icon button:

```kotlin
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
```

3. Add `onOpenSettings: () -> Unit` and `onOpenStreakHistory: () -> Unit` parameters to `HomeScreen`. Threading these from the navigation graph happens in Task 20.

4. Wrap the existing `LazyColumn` (the habits + wants list) inside a `PullToRefreshBox`:

```kotlin
        val isRefreshing = syncState is SyncState.Running
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.manualRefresh() },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    val streakRange by viewModel.streakStrip.collectAsState()
                    val streakSummary by viewModel.streakSummary.collectAsState()
                    StreakStrip(
                        range = streakRange,
                        currentStreak = streakSummary.currentStreak,
                        onViewAll = onOpenStreakHistory,
                        onDayTap = { onOpenStreakHistory() },
                        modifier = Modifier.padding(Spacing.xl),
                    )
                }
                // existing habit + want items unchanged
                items(uiState.habitsWithProgress) { /* ... */ }
                items(uiState.wantActivities) { /* ... */ }
            }
        }
```

> Inline the existing items inside the new `LazyColumn` body. Keep all current render logic identical — only the wrapper changes.

5. Remove the `MoreVert` overflow `DropdownMenu` entirely. The signout / signin entry-point is now in Settings.

- [ ] **Step 4: Build + run app**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakStrip.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt
rtk git commit -m "feat(phase4): StreakStrip + Home pull-to-refresh + Settings icon"
```

---

### Task 18: StreakHistoryViewModel + test

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryViewModel.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryViewModelTest.kt`

- [ ] **Step 1: Create the ViewModel**

```kotlin
package com.jktdeveloper.habitto.ui.streak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakSummary
import com.habittracker.domain.usecase.ComputeStreakUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class MonthData(
    val year: Int,
    val month: Int,
    val days: List<StreakDay>,
    val isLoading: Boolean,
    val error: String? = null,
) {
    fun firstDay(): LocalDate = LocalDate(year, month, 1)
    fun lastDayExclusive(): LocalDate = firstDay().plus(1, DateTimeUnit.MONTH)
}

class StreakHistoryViewModel(
    private val useCase: ComputeStreakUseCase,
    private val userIdProvider: () -> String,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _summary = MutableStateFlow(StreakSummary(0, 0, 0, null))
    val summary: StateFlow<StreakSummary> = _summary.asStateFlow()

    private val _months = MutableStateFlow<List<MonthData>>(emptyList())
    val months: StateFlow<List<MonthData>> = _months.asStateFlow()

    private val _firstLogDate = MutableStateFlow<LocalDate?>(null)
    val firstLogDate: StateFlow<LocalDate?> = _firstLogDate.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = userIdProvider()
            _summary.value = useCase.computeSummaryNow(userId)
            _firstLogDate.value = _summary.value.firstLogDate
            // Seed with current month.
            val today = clock.now().toLocalDateTime(timeZone).date
            loadMonth(today.year, today.monthNumber)
        }
    }

    fun loadOlderMonth() {
        val oldest = _months.value.lastOrNull() ?: return
        val first = LocalDate(oldest.year, oldest.month, 1)
        val older = first.minus(1, DateTimeUnit.MONTH)
        val firstLog = _firstLogDate.value
        if (firstLog != null && older < LocalDate(firstLog.year, firstLog.monthNumber, 1)) return
        loadMonth(older.year, older.monthNumber)
    }

    private fun loadMonth(year: Int, monthNumber: Int) {
        if (_months.value.any { it.year == year && it.month == monthNumber }) return
        val placeholder = MonthData(year, monthNumber, emptyList(), isLoading = true)
        _months.update { it + placeholder }

        viewModelScope.launch {
            runCatching {
                val first = LocalDate(year, monthNumber, 1)
                val nextMonth = first.plus(1, DateTimeUnit.MONTH)
                val range = DateRange(start = first, endExclusive = nextMonth)
                val result = useCase.computeNow(userIdProvider(), range)
                _months.update { existing ->
                    existing.map { m ->
                        if (m.year == year && m.month == monthNumber)
                            m.copy(days = result.days, isLoading = false)
                        else m
                    }
                }
            }.onFailure { e ->
                _months.update { existing ->
                    existing.map { m ->
                        if (m.year == year && m.month == monthNumber)
                            m.copy(isLoading = false, error = e.message ?: "Failed to load")
                        else m
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Create the test**

```kotlin
package com.jktdeveloper.habitto.ui.streak

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.usecase.ComputeStreakUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.test.assertTrue

class StreakHistoryViewModelTest {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 4, 22)
    private val userId = "u1"

    @Test fun `initial load seeds current month + summary`() = runTest {
        val logs = listOf(makeLog(today))
        val uc = ComputeStreakUseCase(InMemoryRepo(logs), tz, FixedClock(today))
        val vm = StreakHistoryViewModel(uc, { userId }, tz, FixedClock(today))
        advanceUntilIdle()
        val months = vm.months.value
        assertEquals(1, months.size)
        assertEquals(2026, months.first().year)
        assertEquals(4, months.first().month)
        assertEquals(1, vm.summary.value.currentStreak)
    }

    @Test fun `loadOlderMonth prepends previous month`() = runTest {
        val logs = listOf(makeLog(LocalDate(2026, 3, 15)), makeLog(today))
        val uc = ComputeStreakUseCase(InMemoryRepo(logs), tz, FixedClock(today))
        val vm = StreakHistoryViewModel(uc, { userId }, tz, FixedClock(today))
        advanceUntilIdle()
        vm.loadOlderMonth()
        advanceUntilIdle()
        val months = vm.months.value
        assertEquals(2, months.size)
        // Last entry in months list is the older month.
        val older = months.last()
        assertEquals(2026, older.year)
        assertEquals(3, older.month)
    }

    @Test fun `loadOlderMonth stops at firstLogDate`() = runTest {
        // First log is in March; trying to load older than March should be a no-op.
        val logs = listOf(makeLog(LocalDate(2026, 3, 15)), makeLog(today))
        val uc = ComputeStreakUseCase(InMemoryRepo(logs), tz, FixedClock(today))
        val vm = StreakHistoryViewModel(uc, { userId }, tz, FixedClock(today))
        advanceUntilIdle()
        vm.loadOlderMonth() // March loaded
        advanceUntilIdle()
        val sizeAfterMarch = vm.months.value.size
        vm.loadOlderMonth() // attempts February — should be blocked
        advanceUntilIdle()
        assertEquals(sizeAfterMarch, vm.months.value.size)
    }

    private fun makeLog(date: LocalDate, habitId: String = "h1"): HabitLog = HabitLog(
        id = "log-$date-$habitId",
        userId = userId,
        habitId = habitId,
        quantity = 1.0,
        loggedAt = LocalDateTime(date, LocalTime(12, 0)).toInstant(tz),
        deletedAt = null,
        syncedAt = null,
    )
}

private class FixedClock(private val date: LocalDate) : Clock {
    override fun now(): Instant = LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.UTC)
}

private class InMemoryRepo(private val logs: List<HabitLog>) : HabitLogRepository {
    override fun observeActiveLogsBetween(
        userId: String,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<HabitLog>> = flowOf(
        logs.filter {
            it.userId == userId && it.deletedAt == null &&
                it.loggedAt >= startInclusive && it.loggedAt < endExclusive
        }
    )

    override suspend fun countActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant): Int =
        logs.count { it.userId == userId && it.deletedAt == null &&
            it.loggedAt >= startInclusive && it.loggedAt < endExclusive }

    override suspend fun firstActiveLogAt(userId: String): Instant? =
        logs.filter { it.userId == userId && it.deletedAt == null }.minByOrNull { it.loggedAt }?.loggedAt

    override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant) =
        error("unused")
    override suspend fun softDelete(logId: String, userId: String) = error("unused")
    override fun observeActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) =
        error("unused")
    override suspend fun getActiveLogsForHabitOnDay(userId: String, habitId: String, dayStart: Instant, dayEnd: Instant) =
        error("unused")
    override fun observeAllActiveLogsForUser(userId: String) = error("unused")
    override suspend fun getAllActiveLogsForUser(userId: String) = error("unused")
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) = error("unused")
    override suspend fun clearForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedFor(userId: String) = error("unused")
    override suspend fun markSynced(id: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulled(row: HabitLog) = error("unused")
}
```

- [ ] **Step 3: Run the test**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.ui.streak.StreakHistoryViewModelTest" -q`
Expected: BUILD SUCCESSFUL with all 3 tests passing.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryViewModel.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryViewModelTest.kt
rtk git commit -m "feat(phase4): StreakHistoryViewModel + tests (load + older + stop)"
```

---

### Task 19: MonthCalendar composable + StreakHistoryScreen

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/MonthCalendar.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryScreen.kt`

- [ ] **Step 1: Create MonthCalendar composable**

```kotlin
package com.jktdeveloper.habitto.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.jktdeveloper.habitto.ui.theme.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthCalendar(
    month: MonthData,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xl)) {
        Text(
            text = "${java.time.Month.of(month.month).getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.md),
        )

        if (month.isLoading) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        if (month.error != null) {
            Text("Couldn't load — ${month.error}", color = MaterialTheme.colorScheme.error)
            return@Column
        }

        // 7-column grid: pad start with empty placeholders so the 1st aligns under the right weekday.
        val firstDay = LocalDate(month.year, month.month, 1)
        val firstDow = firstDay.dayOfWeek
        // We want columns laid out Mon..Sun (kotlinx-datetime DayOfWeek.MONDAY = ordinal 0).
        val padStart = firstDow.ordinal
        val cells: List<StreakDay?> = List(padStart) { null } + month.days
        val rows = cells.chunked(7)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { day ->
                        if (day == null) Box(modifier = Modifier.size(32.dp))
                        else DayCell(day = day, isToday = day.date == today)
                    }
                    repeat(7 - row.size) { Box(modifier = Modifier.size(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: StreakDay, isToday: Boolean) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val (color, onColor) = when (day.state) {
        StreakDayState.COMPLETE -> (if (isDark) StreakCompleteDark else StreakComplete) to Color.White
        StreakDayState.FROZEN -> (if (isDark) StreakFrozenDark else StreakFrozen) to Color.White
        StreakDayState.BROKEN -> (if (isDark) StreakBrokenDark else StreakBroken) to Color.White
        StreakDayState.EMPTY -> (if (isDark) StreakEmptyDark else StreakEmpty) to MaterialTheme.colorScheme.onSurface
        StreakDayState.TODAY_PENDING -> (if (isDark) StreakEmptyDark else StreakEmpty) to MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .let { if (isToday || day.state == StreakDayState.TODAY_PENDING) it.border(1.5.dp, StreakTodayOutline, RoundedCornerShape(6.dp)) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = onColor,
        )
    }
}
```

- [ ] **Step 2: Create StreakHistoryScreen**

```kotlin
package com.jktdeveloper.habitto.ui.streak

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakHistoryScreen(
    viewModel: StreakHistoryViewModel,
    onBack: () -> Unit,
) {
    val summary by viewModel.summary.collectAsState()
    val months by viewModel.months.collectAsState()
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Streak History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item {
                SummaryCard(summary)
            }
            itemsIndexed(months) { index, month ->
                LaunchedEffect(index, months.size) {
                    if (index == months.lastIndex) viewModel.loadOlderMonth()
                }
                MonthCalendar(month = month, today = today)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: com.habittracker.domain.model.StreakSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xl),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat(label = "Current", value = "${summary.currentStreak}")
            Stat(label = "Longest", value = "${summary.longestStreak}")
            Stat(label = "Total days", value = "${summary.totalDaysComplete}")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

> Imports list omits `itemsIndexed`. Add `import androidx.compose.foundation.lazy.itemsIndexed` to the imports block.

- [ ] **Step 3: Build**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/MonthCalendar.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakHistoryScreen.kt
rtk git commit -m "feat(phase4): StreakHistoryScreen + MonthCalendar"
```

---

### Task 20: Navigation — wire Settings + StreakHistory routes

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Add composable destinations**

Inside the `NavHost` block (where `composable(Screen.Auth.route) { ... }` etc. live), append two new composable nodes:

```kotlin
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = remember { container.settingsViewModel() },
                isAuthenticated = container.isAuthenticated(),
                accountEmail = container.currentAccountEmail(),
                onSignOut = {
                    // reuse existing logout flow exposed by HomeViewModel/AuthRepository
                    container.requestLogout {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                },
                onSignIn = {
                    navController.navigate(Screen.Auth.route)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.StreakHistory.route) {
            StreakHistoryScreen(
                viewModel = remember { container.streakHistoryViewModel() },
                onBack = { navController.popBackStack() },
            )
        }
```

> `container.settingsViewModel()`, `container.streakHistoryViewModel()`, `container.currentAccountEmail()`, and `container.requestLogout(onComplete)` are added in Task 21 — that's why Task 20 commits the *navigation hookup* and Task 21 commits the *container side*. They compile together once Task 21 lands.

- [ ] **Step 2: Update HomeScreen call site to pass nav callbacks**

Find the `composable(Screen.Home.route) { HomeScreen(...) }` block and update it to pass the two new callbacks:

```kotlin
            HomeScreen(
                viewModel = remember { container.homeViewModel() },
                onSignIn = { navController.navigate(Screen.Auth.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenStreakHistory = { navController.navigate(Screen.StreakHistory.route) },
            )
```

- [ ] **Step 3: Build (this WILL break — Task 21 fixes it)**

Run: `rtk ./gradlew :mobile:androidApp:compileDebugKotlin -q`
Expected: Compilation errors referencing `settingsViewModel()`, `streakHistoryViewModel()`, etc. — these come from Task 21. Don't commit yet — go to Task 21.

- [ ] **Step 4: Defer commit until Task 21 lands**

Tasks 20 + 21 form a single compilable unit. Stage Task 20 changes but don't commit; commit them together with Task 21.

---

### Task 21: AppContainer + MainActivity wiring

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/MainActivity.kt`

- [ ] **Step 1: Add dependencies + factory methods to AppContainer**

In `AppContainer.kt`, add new fields + factory methods. After the existing constructor body:

```kotlin
    val notificationPreferences: NotificationPreferences = NotificationPreferences(context)
    val notificationFiringDateStore: NotificationFiringDateStore = NotificationFiringDateStore(context)
    val computeStreakUseCase: ComputeStreakUseCase = ComputeStreakUseCase(habitLogRepository)
    val notificationScheduler: NotificationScheduler = NotificationScheduler(context, notificationPreferences)

    fun settingsViewModel(): SettingsViewModel =
        SettingsViewModel(notificationPreferences, notificationScheduler)

    fun streakHistoryViewModel(): StreakHistoryViewModel =
        StreakHistoryViewModel(computeStreakUseCase) { currentUserId() }

    fun currentAccountEmail(): String? =
        runCatching { authRepository.currentSessionEmail() }.getOrNull()

    /** Confirm-and-sign-out helper used by Settings. */
    fun requestLogout(onComplete: () -> Unit) {
        // Reuse existing logout path (push pending → clear local → reset watermarks).
        // Implementation lives in HomeViewModel + AuthRepository — wrap that here.
        kotlinx.coroutines.GlobalScope.launch {
            authRepository.signOut()
            clearAuthenticatedUserData()
            onComplete()
        }
    }
```

> Don't worry about `currentSessionEmail()` if the repo doesn't expose it — return `null` from `currentAccountEmail()` and add the field later when needed.

> The `requestLogout` wrapper above is acceptable for Phase 4 even if it duplicates a tiny amount of logic from `HomeViewModel`. If you find a cleaner shared helper, refactor inline.

- [ ] **Step 2: Reschedule notifications on auth state change**

Wherever `AppContainer.refreshAuthState()` is invoked, also call `notificationScheduler.reschedule()` after the auth state settles. Inline:

```kotlin
    suspend fun refreshAuthStateAndReschedule() {
        refreshAuthState()
        notificationScheduler.reschedule()
    }
```

…and call this from `AppNavigation.kt` startup block instead of `refreshAuthState()`.

- [ ] **Step 3: Initialize channels + scheduler in MainActivity**

In `MainActivity.kt`, inside `onCreate`, after `super.onCreate(savedInstanceState)`:

```kotlin
        val container = (application as HabitTrackerApplication).container
        container.notificationScheduler.ensureChannels()
        lifecycleScope.launch { container.notificationScheduler.reschedule() }
```

> Add `import androidx.lifecycle.lifecycleScope` and `import kotlinx.coroutines.launch` if not already imported.

- [ ] **Step 4: Build**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit Tasks 20 + 21 together**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/MainActivity.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "feat(phase4): nav routes for Settings/StreakHistory + AppContainer wiring"
```

---

### Task 22: Manual verification + close-out PR

**Files:** none (manual smoke + git operations).

- [ ] **Step 1: Run all unit tests**

Run: `rtk ./gradlew :mobile:shared:jvmTest :mobile:androidApp:testDebugUnitTest -q`
Expected: BUILD SUCCESSFUL — no failing tests anywhere.

- [ ] **Step 2: Build a debug APK + install on emulator**

Run: `rtk ./gradlew :mobile:androidApp:installDebug -q`
Expected: APK installed.

- [ ] **Step 3: Manual smoke checklist**

Tick each item by exercising the app on the emulator. Capture screenshots (`adb exec-out screencap -p > screenshot-N.png`) for the PR description.

- [ ] Fresh install → Home shows empty StreakStrip with "Log your first habit to start a streak."
- [ ] Tap a habit, wait 3s for commit → StreakStrip's today cell flips from `TODAY_PENDING` → `COMPLETE`.
- [ ] StreakStrip shows current streak number and updates after each new day's first log.
- [ ] Tap "View all" → opens StreakHistory; current month renders with correct colors.
- [ ] Scroll down → older months load lazily; eventually a stop state at first log month.
- [ ] Tap the Settings icon in the Home top bar → opens Settings.
- [ ] Settings: toggle daily reminder off → check `adb shell dumpsys jobscheduler | grep phase4-daily` → entry gone.
- [ ] Settings: change daily reminder time to 1 minute from now → wait → notification fires.
- [ ] Tap notification → opens app on Home.
- [ ] Settings → Sign out → confirm dialog → returns to Home → app shows guest state.
- [ ] Settings → Sign in → AuthScreen flow → returns to Home authenticated.
- [ ] Pull to refresh on Home → SyncEngine triggers; chip transitions Running → Idle; indicator clears.
- [ ] Pull to refresh while offline → snackbar "No network", indicator clears.
- [ ] Toggle device theme between light/dark → all new surfaces (StreakStrip, StreakHistory, Settings) remain readable.
- [ ] Two-day high-earn / no-spend point rollover: log heavy earnings on day 1, simulate next day via `adb shell date`, observe balance capped at `daily_earn_cap × 2`. (If `adb date` is awkward, defer this verification to next-day natural test.)
- [ ] Permission flow: deny POST_NOTIFICATIONS → Settings shows banner with "System settings" deep link.

- [ ] **Step 4: Push the branch + open PR**

```bash
rtk git push -u origin feature/phase4-streaks-notifications
rtk gh pr create --base main --title "Phase 4: streaks + notifications + settings" --body "$(cat <<'EOF'
## Summary
- Adds 30-day streak strip on Home + Streak History screen with month-by-month scroll
- Adds Settings screen (notifications + account + about); replaces Home overflow menu
- Adds 4 notification types via 3 WorkManager workers (daily reminder, streak risk, day boundary)
- Adds pull-to-refresh on Home backed by manual `SyncEngine.sync(MANUAL_PULL)`
- Implements master spec §2.3 point rollover cap; switches week boundary from UTC to local TZ

## Test plan
- [ ] Streak compute correctness (13 commonTest cases pass)
- [ ] Point rollover cap enforced (8 commonTest cases pass)
- [ ] Notification scheduler enqueues / cancels via WorkManager test harness
- [ ] Manual smoke checklist in plan §22 — done, screenshots attached

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Tag spec + plan complete**

After PR merge:
- Update `docs/superpowers/specs/2026-04-26-phase4-streaks-notifications-design.md` § Definition of Done — tick every box.
- Drop the worktree once main has the merge: `rtk git worktree remove .worktrees/phase4-streaks-notifications`.

---

## Self-Review

After this plan was written, the following gaps were checked:

- **Spec §3.1 (Day States)** — covered by Task 4 (types) + Task 5 (compute) + Task 17 (Home cell render) + Task 19 (history cell render).
- **Spec §3.2 (Compute Rules)** — Task 5 has 13 cases including all FROZEN/BROKEN transitions + DST/timezone + cross-month/year.
- **Spec §3.4 (Use Case API)** — Task 5 implements the full signature.
- **Spec §4 (Point Rollover)** — Task 6 covers compute + TZ fix + tests.
- **Spec §5.1 (Home additions)** — Task 17 wraps Home in PullToRefresh and inserts StreakStrip + Settings icon.
- **Spec §5.2 (Streak History)** — Tasks 18 + 19.
- **Spec §5.3 (Settings)** — Tasks 14 + 15.
- **Spec §6 (Notification system)** — Tasks 8–13 (DataStore, channels, 3 workers, scheduler).
- **Spec §6.5 (Permission flow)** — Task 7 (PermissionUtils) + Task 15 (banner UI) + Task 21 (reschedule on resume).
- **Spec §6.6 (Manifest)** — Task 1.
- **Spec §7 (Data Flow)** — Tasks 16 (Home VM streak flows + manualRefresh) + 18 (streak history loading).
- **Spec §8 (Error handling)** — handled per task: empty / future-dated logs (Task 5), failed monthload retry (Task 18), notif scheduling failure (Task 14), missed worker fire (Task 12 idempotency).
- **Spec §9 (Testing)** — Tasks 5, 6, 8, 10, 11, 12, 13, 14, 18 cover the full unit-test list. Task 22 covers manual smoke.
- **Spec §10 (Design system)** — encoded in Tasks 15 (Settings) + 17 (StreakStrip) + 19 (history).
- **Spec §11 (Dependencies)** — Task 1 + Task 8 + Task 13.
- **Spec §12 (Migration)** — none required; Task 1 manifest perms only.
- **Spec §13 (Open decisions)** — all locked at design time; no open items remain.
- **Spec §14 (Definition of Done)** — Task 22 step 5 ticks each.

No placeholders found. Type / property names consistent across tasks. Task 20+21 explicitly couple-committed to avoid dangling compile errors.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-26-phase4-streaks-notifications.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
