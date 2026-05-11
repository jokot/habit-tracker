# Phase 7 Pivot — Units-Per-Point Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace want-side fractional cost model with `unitsPerPoint: Int` so that 1 tap = ±1 pt for both habits and wants. Rate ladder squeezes effective unit count via `floor(unitsPerPoint / rate)`.

**Architecture:** Schema migration (drop `cost_per_unit`, add `units_per_point`; add `points_spent` on logs; wipe logs). Domain model field swap. `PointCalculator` rewrite drops `pointsSpentWithRate` and adds `pointsSpent(taps)` + `effectiveUnitsPerPoint(units, rate)`. `LogWantUseCase` becomes taps-based and stamps `pointsSpent` at write time. UI flat redesign across HomeScreen, WantList, WantDetail, WantForm, ExchangeRate.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Compose Material 3, kotlinx.datetime, Robolectric + JUnit4, Supabase Postgres.

**Branch:** `feature/phase7-want-crud` (PR #21 already open — pivot extends same branch).

**Spec:** [`docs/superpowers/specs/2026-05-10-phase7-pivot-units-per-point-design.md`](../specs/2026-05-10-phase7-pivot-units-per-point-design.md).

---

## File Map

**Schema:**
- Create: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/7.sqm`
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`

**Domain:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantLog.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/PointCalculator.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LogWantUseCase.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCase.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt`

**Repos:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantLogRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantLogRepository.kt`

**Seed:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt`

**Sync:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt`

**Server migration:**
- Create: `supabase/migrations/20260511000000_phase7_pivot_units_per_point.sql`

**UI:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt`

**Tests (rebaseline):**
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/PointCalculatorTest.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LogWantUseCaseRateTest.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCaseRateTest.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseRateTest.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCaseTest.kt`
- Modify: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantListViewModelTest.kt`
- Modify: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModelTest.kt`
- Modify: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModelTest.kt`
- Modify: `mobile/androidApp/src/test/kotlin/com/habittracker/data/repository/LocalWantActivityRepositoryTest.kt`

---

## Task 1: Schema migration 7 + sq queries

**Files:**
- Create: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/7.sqm`
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`

- [ ] **Step 1: Write migration 7**

`mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/7.sqm`:
```sql
-- Phase 7 pivot: replace cost_per_unit (REAL) with units_per_point (INTEGER).
-- Stamp pointsSpent on each WantLog. Wipe existing WantLogs (solo dev decision).

ALTER TABLE LocalWantActivity ADD COLUMN unitsPerPoint INTEGER NOT NULL DEFAULT 1;
-- SQLite cannot DROP COLUMN before 3.35; recreate the table to drop costPerUnit.
CREATE TABLE LocalWantActivity_new (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    unitsPerPoint INTEGER NOT NULL DEFAULT 1,
    isCustom INTEGER NOT NULL DEFAULT 0,
    updatedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    iconKey TEXT,
    hiddenAt INTEGER
);
INSERT INTO LocalWantActivity_new
    (id, userId, name, unit, unitsPerPoint, isCustom, updatedAt, syncedAt, iconKey, hiddenAt)
SELECT id, userId, name, unit, unitsPerPoint, isCustom, updatedAt, syncedAt, iconKey, hiddenAt
FROM LocalWantActivity;
DROP TABLE LocalWantActivity;
ALTER TABLE LocalWantActivity_new RENAME TO LocalWantActivity;

ALTER TABLE WantLog ADD COLUMN pointsSpent INTEGER NOT NULL DEFAULT 1;
DELETE FROM WantLog;
```

- [ ] **Step 2: Update LocalWantActivity table definition + queries in `.sq`**

In `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`:

Replace the table at L68-79:
```sql
CREATE TABLE IF NOT EXISTS LocalWantActivity (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    unitsPerPoint INTEGER NOT NULL DEFAULT 1,
    isCustom INTEGER NOT NULL DEFAULT 0,
    updatedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    iconKey TEXT,
    hiddenAt INTEGER
);
```

Replace `upsertWantActivity` (L169-171):
```sql
upsertWantActivity:
INSERT OR REPLACE INTO LocalWantActivity (id, userId, name, unit, unitsPerPoint, isCustom, updatedAt, syncedAt, iconKey, hiddenAt)
VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?);
```

Replace `mergePulledWantActivity` (L238-240):
```sql
mergePulledWantActivity:
INSERT OR REPLACE INTO LocalWantActivity (id, userId, name, unit, unitsPerPoint, isCustom, updatedAt, syncedAt, iconKey, hiddenAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

- [ ] **Step 3: Update WantLog table + queries in `.sq`**

Find `CREATE TABLE IF NOT EXISTS WantLog` (around L11). Add `pointsSpent INTEGER NOT NULL DEFAULT 1` column. Resulting:
```sql
CREATE TABLE IF NOT EXISTS WantLog (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,
    activityId TEXT NOT NULL,
    quantity REAL NOT NULL,
    pointsSpent INTEGER NOT NULL DEFAULT 1,
    deviceMode TEXT NOT NULL,
    loggedAt INTEGER NOT NULL,
    deletedAt INTEGER,
    syncedAt INTEGER
);
```

Update `insertWantLog` (L127-129) to take pointsSpent:
```sql
insertWantLog:
INSERT INTO WantLog (id, userId, activityId, quantity, pointsSpent, deviceMode, loggedAt, deletedAt, syncedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
```

Update `mergePulledWantLog` (L131-133):
```sql
mergePulledWantLog:
INSERT OR REPLACE INTO WantLog (id, userId, activityId, quantity, pointsSpent, deviceMode, loggedAt, deletedAt, syncedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
```

- [ ] **Step 4: Bump schema version**

Find the SQLDelight DSL block in `mobile/shared/build.gradle.kts`. Locate `schemaVersion = 6` (or `version = 6`) and bump to `7`.

Run: `rtk grep -n "version.*= 6\|schemaVersion = 6" mobile/shared/build.gradle.kts`. Update the matching line to `7`.

- [ ] **Step 5: Build to verify schema valid**

Run: `rtk ./gradlew :mobile:shared:generateCommonMainHabitTrackerDatabaseInterface 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL. SQLDelight regenerates `LocalWantActivity.kt` with `unitsPerPoint: Long` field and `WantLog` with `pointsSpent: Long`.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/7.sqm \
    mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq \
    mobile/shared/build.gradle.kts
rtk git commit -m "feat(want): schema 7 — units_per_point + points_spent + wipe want_logs"
```

---

## Task 2: Domain models WantActivity + WantLog

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantLog.kt`

- [ ] **Step 1: Update WantActivity**

Replace `costPerUnit: Double` with `unitsPerPoint: Int`. Final:
```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class WantActivity(
    val id: String,
    val name: String,
    val unit: String,
    val unitsPerPoint: Int,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
    val iconKey: String? = null,
    val hiddenAt: Instant? = null,
    val updatedAt: Instant = Instant.fromEpochMilliseconds(0),
    val syncedAt: Instant? = null,
)
```

- [ ] **Step 2: Update WantLog**

Add `pointsSpent: Int`:
```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class WantLog(
    val id: String,
    val userId: String,
    val activityId: String,
    val quantity: Double,
    val pointsSpent: Int,
    val deviceMode: DeviceMode,
    val loggedAt: Instant,
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null,
)
```

- [ ] **Step 3: Compile to surface call sites**

Run: `rtk ./gradlew :mobile:shared:compileKotlinMetadata 2>&1 | tail -20`

Expected: FAIL — many references to `costPerUnit`, `pointsSpent` missing on log construction. That's the to-do list for the next tasks.

- [ ] **Step 4: Commit (broken-build commit, intermediate)**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt \
    mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantLog.kt
rtk git commit -m "refactor(want): domain WantActivity.unitsPerPoint + WantLog.pointsSpent"
```

---

## Task 3: PointCalculator rewrite + tests

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/PointCalculator.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/PointCalculatorTest.kt`

- [ ] **Step 1: Write failing tests**

Replace contents of `PointCalculatorTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run tests to verify failure**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*PointCalculatorTest*" 2>&1 | tail -10`

Expected: FAIL — `pointsSpent(taps: Int)` and `effectiveUnitsPerPoint` not defined; old `pointsSpentWithRate` referenced from elsewhere.

- [ ] **Step 3: Rewrite PointCalculator**

Replace contents of `PointCalculator.kt`:
```kotlin
package com.habittracker.domain.usecase

object PointCalculator {
    /** Habit side — units accumulate, points = floor(quantity / threshold). */
    fun pointsEarned(quantity: Double, threshold: Double): Int =
        if (threshold <= 0.0) 0 else (quantity / threshold).toInt()

    /** Want side — one tap is one point. Multi-tap sums. */
    fun pointsSpent(taps: Int): Int = taps.coerceAtLeast(0)

    /**
     * Higher rate squeezes the unit count behind a single −1 pt tap.
     * Clamped to 1 so cheap wants (unitsPerPoint = 1) stay at 1 unit per tap
     * regardless of tier.
     */
    fun effectiveUnitsPerPoint(unitsPerPoint: Int, rate: Double): Int =
        if (unitsPerPoint <= 0 || rate <= 0.0) 1
        else (unitsPerPoint / rate).toInt().coerceAtLeast(1)
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*PointCalculatorTest*" 2>&1 | tail -10`

Expected: PASS, 4/4.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/PointCalculator.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/PointCalculatorTest.kt
rtk git commit -m "refactor(want): PointCalculator — pointsSpent(taps) + effectiveUnitsPerPoint"
```

---

## Task 4: WantActivity repo + queries

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt` (interface)
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeWantActivityRepository.kt`
- Modify: `mobile/androidApp/src/test/kotlin/com/habittracker/data/repository/LocalWantActivityRepositoryTest.kt`

- [ ] **Step 1: Update LocalWantActivityRepository.toDomain mapper**

Find the `LocalWantActivity.toDomain()` mapper in `LocalWantActivityRepository.kt`. Replace `costPerUnit = costPerUnit` with `unitsPerPoint = unitsPerPoint.toInt()`. Generated SQLDelight column is `unitsPerPoint: Long` — cast to Int.

- [ ] **Step 2: Update saveWantActivity binding order**

Find calls to `queries.upsertWantActivity(...)` in `LocalWantActivityRepository.kt`. The 9 placeholder bindings (id, userId, name, unit, unitsPerPoint, isCustom, updatedAt, iconKey, hiddenAt) must pass `activity.unitsPerPoint.toLong()` instead of `activity.costPerUnit`.

- [ ] **Step 3: Update mergePulled mapping**

Same file. The mapping for `mergePulledWantActivity(...)` (10 bindings: id, userId, name, unit, unitsPerPoint, isCustom, updatedAt, syncedAt, iconKey, hiddenAt) must pass `activity.unitsPerPoint.toLong()`.

- [ ] **Step 4: Repo test fixtures**

Replace every `WantActivity(... costPerUnit = X.X ...)` with `WantActivity(... unitsPerPoint = N ...)` in `FakeWantActivityRepository.kt` and `LocalWantActivityRepositoryTest.kt`. Tests compile but specific cost-related assertions must use Int values.

In `LocalWantActivityRepositoryTest.kt`, locate the `iconKey persistence` test and similar — replace literals (e.g. `costPerUnit = 1.0` → `unitsPerPoint = 1`).

- [ ] **Step 5: Run repo tests**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*LocalWantActivityRepositoryTest*" 2>&1 | tail -10`

Expected: PASS, 4/4.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt \
    mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeWantActivityRepository.kt \
    mobile/androidApp/src/test/kotlin/com/habittracker/data/repository/LocalWantActivityRepositoryTest.kt
rtk git commit -m "refactor(want): repo + queries use unitsPerPoint Int"
```

---

## Task 5: WantLogRepository.insertLog gains pointsSpent

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantLogRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantLogRepository.kt`

- [ ] **Step 1: Update interface**

In `WantLogRepository.kt` add `pointsSpent: Int` parameter:
```kotlin
suspend fun insertLog(
    id: String,
    userId: String,
    activityId: String,
    quantity: Double,
    pointsSpent: Int,
    deviceMode: DeviceMode,
    loggedAt: Instant,
): WantLog
```

- [ ] **Step 2: Update Local impl**

In `LocalWantLogRepository.kt`:
- `insertLog(...)` impl receives the new param. Pass `pointsSpent.toLong()` as the 5th SQL placeholder per Task 1's updated `insertWantLog` query.
- `toDomain()` mapper reads `pointsSpent.toInt()` from the row.
- `mergePulled(...)` passes `row.pointsSpent.toLong()`.

- [ ] **Step 3: Update FakeWantLogRepository (commonTest)**

Find `FakeWantLogRepository` (search via `rtk grep -n "FakeWantLogRepository" mobile/`). Update `insertLog` signature and stored objects to carry `pointsSpent`. Default to `1` if a test doesn't care.

- [ ] **Step 4: Build shared**

Run: `rtk ./gradlew :mobile:shared:compileKotlinMetadata 2>&1 | tail -20`

Expected: surfaces remaining compile errors at `LogWantUseCase` call site (next task) and the Get* point-summing use cases.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantLogRepository.kt \
    mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantLogRepository.kt
rtk git commit -m "refactor(want): WantLogRepository.insertLog stamps pointsSpent"
```

---

## Task 6: LogWantUseCase rewrite + tests

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LogWantUseCase.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LogWantUseCaseRateTest.kt`

- [ ] **Step 1: Write failing tests**

Replace contents of `LogWantUseCaseRateTest.kt`:
```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeWantActivityRepository
import com.habittracker.data.repository.FakeWantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogWantUseCaseRateTest {
    private val userId = "u1"
    private val fixedClock = object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(1_000_000) }

    private fun setup(
        unitsPerPoint: Int,
        rate: Double,
    ): Triple<LogWantUseCase, FakeWantLogRepository, FakeWantActivityRepository> {
        val activityRepo = FakeWantActivityRepository()
        val logRepo = FakeWantLogRepository()
        val activity = WantActivity(
            id = "a", name = "Test", unit = "min", unitsPerPoint = unitsPerPoint,
        )
        kotlinx.coroutines.runBlocking { activityRepo.saveWantActivity(activity, userId) }
        val sut = LogWantUseCase(
            activityRepo = activityRepo,
            logRepo = logRepo,
            rateProvider = { rate },
            clock = fixedClock,
        )
        return Triple(sut, logRepo, activityRepo)
    }

    @Test fun `single tap stamps quantity = unitsPerPoint, pointsSpent = 1 at tier 1`() = runTest {
        val (sut, logRepo, _) = setup(unitsPerPoint = 10, rate = 1.0)
        val res = sut.execute(userId, "a", taps = 1, deviceMode = DeviceMode.OTHER)
        assertTrue(res.isSuccess)
        val logged = logRepo.getAllActiveLogsForUser(userId).single()
        assertEquals(10.0, logged.quantity)
        assertEquals(1, logged.pointsSpent)
    }

    @Test fun `tier 5 squeezes quantity per tap to floor(unitsPerPoint divided by 2)`() = runTest {
        val (sut, logRepo, _) = setup(unitsPerPoint = 10, rate = 2.0)
        sut.execute(userId, "a", taps = 1, deviceMode = DeviceMode.OTHER)
        val logged = logRepo.getAllActiveLogsForUser(userId).single()
        assertEquals(5.0, logged.quantity)
        assertEquals(1, logged.pointsSpent)
    }

    @Test fun `multi-tap multiplies quantity and points by tap count`() = runTest {
        val (sut, logRepo, _) = setup(unitsPerPoint = 10, rate = 1.0)
        sut.execute(userId, "a", taps = 3, deviceMode = DeviceMode.OTHER)
        val logged = logRepo.getAllActiveLogsForUser(userId).single()
        assertEquals(30.0, logged.quantity)
        assertEquals(3, logged.pointsSpent)
    }

    @Test fun `unitsPerPoint = 1 stays 1 unit per tap at tier 5`() = runTest {
        val (sut, logRepo, _) = setup(unitsPerPoint = 1, rate = 2.0)
        sut.execute(userId, "a", taps = 1, deviceMode = DeviceMode.OTHER)
        val logged = logRepo.getAllActiveLogsForUser(userId).single()
        assertEquals(1.0, logged.quantity)
        assertEquals(1, logged.pointsSpent)
    }
}
```

- [ ] **Step 2: Run failing**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*LogWantUseCaseRateTest*" 2>&1 | tail -10`

Expected: FAIL — LogWantUseCase signature mismatch.

- [ ] **Step 3: Rewrite LogWantUseCase**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantLog
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.Clock

class LogWantUseCase(
    private val activityRepo: WantActivityRepository,
    private val logRepo: WantLogRepository,
    private val rateProvider: suspend (String) -> Double = { 1.0 },
    private val clock: Clock = Clock.System,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(
        userId: String,
        activityId: String,
        taps: Int = 1,
        deviceMode: DeviceMode,
    ): Result<WantLog> = runCatching {
        require(taps >= 1) { "taps must be ≥ 1" }
        val activity = activityRepo.getAllWantActivitiesForUser(userId)
            .firstOrNull { it.id == activityId }
            ?: error("WantActivity $activityId not found for user $userId")
        val rate = rateProvider(userId)
        val effUnits = PointCalculator.effectiveUnitsPerPoint(activity.unitsPerPoint, rate)
        val quantity = (effUnits.toLong() * taps.toLong()).toDouble()
        val pointsSpent = PointCalculator.pointsSpent(taps)
        logRepo.insertLog(
            id = Uuid.random().toString(),
            userId = userId,
            activityId = activityId,
            quantity = quantity,
            pointsSpent = pointsSpent,
            deviceMode = deviceMode,
            loggedAt = clock.now(),
        )
    }
}
```

If existing `LogWantUseCase` had a different rate provider shape (e.g. `getCurrentRate: GetCurrentExchangeRateUseCase`), wrap the call in a lambda when wiring in `AppContainer`.

- [ ] **Step 4: Run tests**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*LogWantUseCaseRateTest*" 2>&1 | tail -10`

Expected: PASS, 4/4.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LogWantUseCase.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LogWantUseCaseRateTest.kt
rtk git commit -m "refactor(want): LogWantUseCase taps-based + stamps pointsSpent"
```

---

## Task 7: Get*PointsUseCase switch to summing pointsSpent

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCase.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCaseRateTest.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseRateTest.kt`

- [ ] **Step 1: Update GetDayPointsUseCase to sum WantLog.pointsSpent**

Find every occurrence of `pointsSpentWithRate(log.quantity, activity.costPerUnit, rate)` (or similar) in `GetDayPointsUseCase.kt`. Replace with `log.pointsSpent`. The corresponding loop sums `log.pointsSpent` across all want logs for the day (same loop structure, different value source). Habit-side logic unchanged.

- [ ] **Step 2: Same for GetPointBalanceUseCase**

Replace the want-side accumulation with `wantLogs.sumOf { it.pointsSpent }`.

- [ ] **Step 3: Rebaseline tests**

In `GetDayPointsUseCaseRateTest.kt` and `GetPointBalanceUseCaseRateTest.kt`:
- Replace `WantActivity(... costPerUnit = X.X ...)` with `... unitsPerPoint = N ...`.
- Replace constructed `WantLog(...)` fixtures to include `pointsSpent = N`.
- Adjust expected aggregates: under the new model, total pt for the day equals sum of `pointsSpent` (not a recomputation). Most tests simplify; remove rate-recompute assertions and add direct sum assertions.

Example (in `GetDayPointsUseCaseRateTest.kt`):
```kotlin
@Test
fun `dayWantPoints sums pointsSpent across active logs`() = runTest {
    val day = LocalDate(2026, 5, 10)
    repo.insertLog("l1", "u1", "a1", quantity = 10.0, pointsSpent = 1, ...)
    repo.insertLog("l2", "u1", "a1", quantity = 30.0, pointsSpent = 3, ...)
    val sut = GetDayPointsUseCase(...)
    assertEquals(4, sut.execute("u1", day).wantPoints)
}
```

- [ ] **Step 4: Run tests**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*GetDayPointsUseCase*" --tests "*GetPointBalanceUseCase*" 2>&1 | tail -10`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCase.kt \
    mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCaseRateTest.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCaseRateTest.kt
rtk git commit -m "refactor(want): Get*PointsUseCase sum pointsSpent (stamped at write)"
```

---

## Task 8: SeedData rescale + reconcile test

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCaseTest.kt`

- [ ] **Step 1: Rebuild seed list with Int unitsPerPoint**

Replace the `wantActivities = listOf(...)` block in `SeedData.kt` with the 14 entries from spec §"Seed list":
```kotlin
val wantActivities: List<WantActivity> = listOf(
    seed("20000000-0000-0000-0000-000000000001", "TikTok",         "min",     1,  "play_circle"),
    seed("20000000-0000-0000-0000-000000000002", "YouTube Shorts", "min",     1,  "smart_display"),
    seed("20000000-0000-0000-0000-000000000003", "YouTube",        "min",     10, "smart_display"),
    seed("20000000-0000-0000-0000-000000000004", "Netflix",        "min",     15, "local_movies"),
    seed("20000000-0000-0000-0000-000000000005", "Twitter/X",      "min",     2,  "chat_bubble"),
    seed("20000000-0000-0000-0000-000000000006", "Instagram",      "min",     2,  "photo_camera"),
    seed("20000000-0000-0000-0000-000000000007", "Reddit",         "min",     2,  "forum"),
    seed("20000000-0000-0000-0000-000000000008", "Gaming",         "min",     10, "sports_esports"),
    seed("20000000-0000-0000-0000-000000000009", "Online shopping","min",     5,  "shopping_bag"),
    seed("20000000-0000-0000-0000-000000000010", "Junk food",      "meal",    1,  "restaurant"),
    seed("20000000-0000-0000-0000-000000000011", "Snacks",         "serving", 1,  "restaurant"),
    seed("20000000-0000-0000-0000-000000000012", "Sweets",         "piece",   1,  "cake"),
    seed("20000000-0000-0000-0000-000000000013", "Sugary drinks",  "drink",   1,  "local_drink"),
    seed("20000000-0000-0000-0000-000000000014", "Coffee",         "cup",     1,  "local_cafe"),
)

private fun seed(id: String, name: String, unit: String, unitsPerPoint: Int, iconKey: String) =
    WantActivity(
        id = id, name = name, unit = unit,
        unitsPerPoint = unitsPerPoint,
        isCustom = false, iconKey = iconKey,
    )
```

The stable UUIDs survive — only relevant for tests; production reconcile assigns fresh per-user UUIDs since the prior bug fix.

- [ ] **Step 2: Update reconcile tests**

In `SetupUserWantActivitiesUseCaseTest.kt`, replace `costPerUnit = X.X` literals with `unitsPerPoint = N`. The `preserveCustomizedCost` test renames to `preserveCustomizedUnitsPerPoint` and asserts user-edited `unitsPerPoint = 7` is preserved when reconcile runs again.

- [ ] **Step 3: Run tests**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*SetupUserWantActivitiesUseCaseTest*" 2>&1 | tail -10`

Expected: PASS, 4/4.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCaseTest.kt
rtk git commit -m "feat(want): rescale 14 seed wants to Int unitsPerPoint"
```

---

## Task 9: Sync DTO swap

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt`

- [ ] **Step 1: Update WantActivityDto**

Replace `cost_per_unit` field with `units_per_point: Int`:
```kotlin
@Serializable
private data class WantActivityDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val unit: String,
    @SerialName("units_per_point") val unitsPerPoint: Int,
    @SerialName("is_custom") val isCustom: Boolean,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("icon_key") val iconKey: String? = null,
    @SerialName("hidden_at") val hiddenAt: String? = null,
)
```

Update `WantActivity.toDto(...)` and `WantActivityDto.toDomain()`:
```kotlin
private fun WantActivity.toDto(ownerUserId: String) = WantActivityDto(
    id = id, userId = ownerUserId, name = name, unit = unit,
    unitsPerPoint = unitsPerPoint,
    isCustom = isCustom, updatedAt = updatedAt.toString(),
    iconKey = iconKey, hiddenAt = hiddenAt?.toString(),
)

private fun WantActivityDto.toDomain() = WantActivity(
    id = id, name = name, unit = unit,
    unitsPerPoint = unitsPerPoint,
    isCustom = isCustom, createdByUserId = userId,
    iconKey = iconKey, hiddenAt = hiddenAt?.let { Instant.parse(it) },
    updatedAt = Instant.parse(updatedAt),
    syncedAt = Instant.parse(updatedAt),
)
```

- [ ] **Step 2: Add WantLogDto.pointsSpent**

Find `WantLogDto`. Add:
```kotlin
@SerialName("points_spent") val pointsSpent: Int = 1,
```

Update `WantLog.toDto()` and `WantLogDto.toDomain()` to round-trip the field.

- [ ] **Step 3: Build**

Run: `rtk ./gradlew :mobile:shared:assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt
rtk git commit -m "refactor(sync): WantActivityDto.units_per_point + WantLogDto.points_spent"
```

---

## Task 10: Server migration

**Files:**
- Create: `supabase/migrations/20260511000000_phase7_pivot_units_per_point.sql`

- [ ] **Step 1: Write SQL**

```sql
-- Phase 7 pivot: units-per-point want point model.
--
-- want_activities: drop cost_per_unit, add units_per_point.
--   Repair user-claimed seed rows by name → new Int. Custom rows default to 1.
-- want_logs: wipe (solo dev — no production logs) and stamp points_spent on each row.

alter table want_activities add column units_per_point integer not null default 1;

update want_activities set units_per_point = case lower(name)
    when 'tiktok'          then 1
    when 'youtube shorts'  then 1
    when 'youtube'         then 10
    when 'netflix'         then 15
    when 'twitter/x'       then 2
    when 'instagram'       then 2
    when 'reddit'          then 2
    when 'gaming'          then 10
    when 'online shopping' then 5
    when 'junk food'       then 1
    when 'snacks'          then 1
    when 'sweets'          then 1
    when 'sugary drinks'   then 1
    when 'coffee'          then 1
    else 1
end where is_custom = false;

alter table want_activities drop column cost_per_unit;

truncate table want_logs;
alter table want_logs add column points_spent integer not null default 1;
```

- [ ] **Step 2: Push to remote**

Run: `rtk supabase db push`

Expected: prompts for `20260511000000_phase7_pivot_units_per_point.sql` → `y`. "Finished supabase db push."

- [ ] **Step 3: Commit**

```bash
rtk git add supabase/migrations/20260511000000_phase7_pivot_units_per_point.sql
rtk git commit -m "fix(sync): server migration for units_per_point + points_spent + truncate logs"
```

---

## Task 11: WantForm VM + Screen rewrite

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormScreen.kt`
- Modify: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModelTest.kt`

- [ ] **Step 1: Write failing VM tests**

In `WantFormViewModelTest.kt`, replace the cost-related fields/tests:
- Replace `costInput: String = "1.0"` references with `unitsInput: String = "1"`.
- Drop the `cost change triggers warning when past logs exist` test (cost-edit warning is removed under new model).
- Drop the `cost change doesn't warn when no past logs` test.
- Drop the `validation rejects empty name and negative cost` test in favor of `validation rejects empty name and unitsPerPoint < 1`.
- Update `new mode saves a custom activity` to assert `saved.unitsPerPoint = 5` (or whatever value is exercised).

- [ ] **Step 2: Run failing**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*WantFormViewModelTest*" 2>&1 | tail -10`

Expected: FAIL — fields/methods missing.

- [ ] **Step 3: Rewrite WantFormViewModel state**

Replace `WantFormUi` data class fields:
```kotlin
data class WantFormUi(
    val mode: FormMode,
    val name: String = "",
    val unit: String = "min",
    val unitsInput: String = "1",
    val iconKey: String = "more_horiz",
    val isSaving: Boolean = false,
    val validationError: String? = null,
    val saved: Boolean = false,
)
```

Drop `hasPastLogs`, `originalCost`, `showCostEditWarning`. Drop `costInput`.

Add setter `onUnitsInput(v: String)`:
```kotlin
fun onUnitsInput(v: String) {
    _state.update { it.copy(unitsInput = v, validationError = null) }
}
```

Drop `onCostInput`. Drop the `wantLogRepo` dependency and the `load()` past-logs check (no longer needed).

Replace `save(onDone)` validation:
```kotlin
val s = _state.value
if (s.name.isBlank()) {
    _state.update { it.copy(validationError = "Name required") }; return
}
val units = s.unitsInput.toIntOrNull()
if (units == null || units < 1) {
    _state.update { it.copy(validationError = "Units per point must be ≥ 1") }; return
}
```

In the WantActivity construction, swap `costPerUnit = cost` for `unitsPerPoint = units`.

Update `load()` Edit branch to populate `unitsInput = w.unitsPerPoint.toString()`. Drop the `wantLogRepo.getAllActiveLogsForUser(...)` call.

- [ ] **Step 4: Rewrite WantFormScreen cost section**

In `WantFormScreen.kt`, replace the `CostStepperRow` block + cost-edit warning surface:
```kotlin
Spacer(Modifier.height(20.dp))
Text("Units per point", fontWeight = FontWeight.SemiBold)
Spacer(Modifier.height(8.dp))
UnitsStepperRow(
    value = state.unitsInput,
    onChange = viewModel::onUnitsInput,
    unit = state.unit,
)
```

Drop the cost-edit warning Surface entirely. Add the `UnitsStepperRow` composable:
```kotlin
@Composable
private fun UnitsStepperRow(value: String, onChange: (String) -> Unit, unit: String) {
    val parsed = value.toIntOrNull() ?: 1
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            label = { Text("$unit per −1 pt") },
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { onChange((parsed - 1).coerceAtLeast(1).toString()) }) {
            Icon(Icons.Default.Remove, contentDescription = "Decrement")
        }
        IconButton(onClick = { onChange((parsed + 1).toString()) }) {
            Icon(Icons.Default.Add, contentDescription = "Increment")
        }
    }
    Text(
        "$parsed $unit = −1 pt",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

- [ ] **Step 5: Replace unit FilterChip block with free-text + chip suggestions**

Replace the `FlowRow` of `FilterChip` in `WantFormScreen.kt` with:
```kotlin
Spacer(Modifier.height(20.dp))
Text("Unit", fontWeight = FontWeight.SemiBold)
Spacer(Modifier.height(8.dp))
OutlinedTextField(
    value = state.unit,
    onValueChange = viewModel::onUnit,
    singleLine = true,
    label = { Text("Unit (e.g. min, cup, meal)") },
    modifier = Modifier.fillMaxWidth(),
)
Spacer(Modifier.height(8.dp))
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    UNITS.forEach { unit ->
        AssistChip(
            onClick = { viewModel.onUnit(unit) },
            label = { Text(unit) },
        )
    }
}
```

- [ ] **Step 6: Run tests + build**

```bash
rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*WantFormViewModelTest*" :mobile:androidApp:assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all VM tests pass.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModel.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormScreen.kt \
    mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModelTest.kt
rtk git commit -m "refactor(want): WantForm — Int stepper + free-text unit + drop cost warning"
```

---

## Task 12: WantList + WantDetail UI

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt`
- Modify: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantListViewModelTest.kt`

- [ ] **Step 1: WantList row subtitle uses unitsPerPoint**

In `WantListScreen.kt`, locate `WantRow` and replace the cost line:
```kotlin
Text(
    "${activity.unitsPerPoint} ${activity.unit} = −1 pt",
    style = MaterialTheme.typography.bodySmall,
    color = costColor.copy(alpha = mutedAlpha),
)
```

Drop the `formatCost` helper at the bottom of the file (no longer used).

- [ ] **Step 2: Update WantListViewModelTest fixtures**

In `WantListViewModelTest.kt`, swap `costPerUnit = 1.0` for `unitsPerPoint = 1` in the `seed(...)` helper.

- [ ] **Step 3: WantDetailViewModel — sum pointsSpent**

In `WantDetailViewModel.kt`, replace the per-day timeline computation. Drop the `getUserStreakOnDay.execute(...)` + `ExchangeRateCalculator.rateFor(...)` + `PointCalculator.pointsSpentWithRate(...)` chain. Compute each `TimedLog.pointsAtLog` directly from `log.pointsSpent`:
```kotlin
val items = (byDate[d] ?: emptyList()).map { log ->
    TimedLog(
        time = log.loggedAt.toLocalDateTime(tz).time,
        qty = log.quantity,
        pointsAtLog = log.pointsSpent,
    )
}
```

Drop the `getUserStreakOnDay: GetUserStreakOnDayUseCase` constructor parameter.

`totalSpent7d` now equals `days.sumOf { it.items.sumOf { item -> item.pointsAtLog } }` — same shape, but each `pointsAtLog` is the persisted `pointsSpent`.

- [ ] **Step 4: WantDetailScreen — hero cost line uses unitsPerPoint**

In `WantDetailScreen.kt`, locate the hero cost text (`"−${formatCostDetail(costPerUnit)} pt per $unit"`) and replace:
```kotlin
Text(
    "${unitsPerPoint} $unit = −1 pt",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

Drop the red `−$cost pt` Text. The hero now shows just the units → 1 pt rate in muted color.

In `HeroCard` signature, replace `costPerUnit: Double` with `unitsPerPoint: Int`. Update the call site.

In `DayCard` rows, the `formatQty(item.qty)` already shows the logged quantity (e.g. 10 min) and `−${item.pointsAtLog} pt` shows the actual points. No change needed. Drop the now-unused `formatCostDetail` helper.

- [ ] **Step 5: AppContainer wire-up**

Find `WantDetailViewModel(activityId, container)` secondary constructor in the VM. Drop the `getUserStreakOnDay = container.getUserStreakOnDayUseCase` line.

- [ ] **Step 6: Run tests + build**

```bash
rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*WantListViewModelTest*" :mobile:androidApp:assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListScreen.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt \
    mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantListViewModelTest.kt
rtk git commit -m "refactor(want-ui): WantList + WantDetail use unitsPerPoint + log.pointsSpent"
```

---

## Task 13: HomeScreen want chip + ExchangeRate comparison

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateScreen.kt`
- Modify: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModelTest.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: HomeScreen want chip subtitle**

In `HomeScreen.kt`, locate the `WantActivityCard` idle subtitle (around L580) and replace with:
```kotlin
Row {
    Text(
        text = "${activity.unitsPerPoint} ${activity.unit}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = " · −1 pt",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
```

For pending state (multi-tap), `totalCost = pending.count * 1`. Replace `totalCost = cost * pending.count` with `totalCost = pending.count`. Drop the `cost = perTapCostInt(activity, rate)` line.

Drop helper `perTapCostInt(...)` and `formatRawCost(...)` at the bottom of the file.

- [ ] **Step 2: HomeViewModel logWant call**

In `HomeViewModel.kt`, locate `container.logWantUseCase.execute(userId, activity.id, 1.0, DeviceMode.OTHER)` (around L356) and the multi-tap commit path. Update both to the new signature:
```kotlin
container.logWantUseCase.execute(
    userId = userId,
    activityId = activity.id,
    taps = batch.count,
    deviceMode = DeviceMode.OTHER,
)
```

The single-tap idle path uses `taps = 1`. Multi-tap uses the pending batch count.

- [ ] **Step 3: ExchangeRateViewModel — comparison rows by tier**

Locate the comparison row computation. It currently produces per-tier `−$cost pt` figures. Replace per-row computation:
```kotlin
val rows = activeWants.map { wa ->
    val perTier = ExchangeRateCalculator.tiers.map { tier ->
        val effUnits = PointCalculator.effectiveUnitsPerPoint(wa.unitsPerPoint, tier.rate)
        TierCell(
            tierLevel = tier.level,
            unitsPerPoint = effUnits,
            unit = wa.unit,
        )
    }
    ComparisonRow(
        activityId = wa.id,
        activityName = wa.name,
        iconKey = wa.iconKey,
        tiers = perTier,
    )
}
```

Update the corresponding state class shape (`TierCell`, `ComparisonRow`).

- [ ] **Step 4: ExchangeRateScreen — render new shape**

In `ExchangeRateScreen.kt`'s `ComparisonRowView`, replace the per-tier `Text("−${cell.cost} pt")` with:
```kotlin
Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
        "${cell.unitsPerPoint}",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        cell.unit,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "/ −1 pt",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

The header row "T1 / T2 / T3 / T4 / T5" remains.

- [ ] **Step 5: ExchangeRateViewModelTest rebaseline**

Replace fixture wants to use `unitsPerPoint`. Update assertions to expect `effectiveUnitsPerPoint(10, 1.2) = 8` etc. Drop `−$cost pt` assertions.

- [ ] **Step 6: OnboardingScreen — drop cost preview**

Find the want preview section in `OnboardingScreen.kt` (search for `costPerUnit`). Replace with `${unitsPerPoint} ${unit} = −1 pt` formatting.

- [ ] **Step 7: Run tests + build**

```bash
rtk ./gradlew :mobile:androidApp:testDebugUnitTest :mobile:androidApp:assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModel.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateScreen.kt \
    mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModelTest.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt
rtk git commit -m "refactor(ui): Home + ExchangeRate + Onboarding speak unitsPerPoint"
```

---

## Task 14: Final smoke + amend PR description

- [ ] **Step 1: Full shared test suite**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Full android test suite**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Debug + release builds**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug :mobile:androidApp:assembleRelease 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 4: Manual smoke (device)**

User clears app data + reinstalls. Verify:

- [ ] Onboarding completes without sync error.
- [ ] Wants list shows 14 seeded items each as "$N $unit = −1 pt" (no decimals anywhere).
- [ ] Tap YouTube once on Today → balance drops by 1, log shows 10 min logged.
- [ ] Tap YouTube three times rapid (multi-tap) → commits as 30 min, −3 pt.
- [ ] WantDetail "Recent activity" shows time · 10 min · −1 pt rows.
- [ ] WantForm: edit Coffee → unitsPerPoint stepper +1/−1, free-text unit field accepts "shot". Save persists.
- [ ] ExchangeRate comparison rows show per-tier units count (5 / 6 / 7 / 8 / 10 for YouTube reading right-to-left from T5 to T1).
- [ ] Hide a seeded want → manual sync → still hidden after pull (DTO carries hidden_at).

- [ ] **Step 5: Push + amend PR**

```bash
rtk git push origin feature/phase7-want-crud
gh pr edit 21 --title "Phase 7: Want CRUD + units-per-point point model" --body "$(cat <<'EOF'
## Summary

Adds Want CRUD (list / detail / add / edit / hide / delete), reconciles
seeded wants to a curated 14-item list with explicit per-row icons, and
introduces the **units-per-point** point model: a single tap is always
±1 pt for both habits and wants. Each `WantActivity` declares
`unitsPerPoint: Int` — how many units of the activity equal one point of
spend. Replaces the prior fractional `costPerUnit: Double` model that
caused a per-log ceiling math bug.

## Highlights

- WantActivity gains `iconKey`, `hiddenAt`, swaps `costPerUnit: Double` for `unitsPerPoint: Int`.
- WantLog gains `pointsSpent: Int` — stamped at write, never recomputed.
- Rate ladder (1.0 / 1.2 / 1.4 / 1.6 / 2.0×) applies via `effectiveUnitsPerPoint = max(1, floor(unitsPerPoint / rate))`. Tier 5 squeezes minutes per −1 pt.
- 14-item seeded want set with stable per-user UUIDs (regenerated random) — no cross-user RLS conflict.
- One-shot rate-ladder banner + Today long-press → WantDetail.
- ExchangeRateScreen comparison rows show per-tier unit counts.
- Sync DTO round-trips iconKey, hidden_at, units_per_point, points_spent.
- Server migration aligns columns + truncates want_logs.

## Test plan

- [x] All shared + android unit tests pass
- [x] Debug + release builds succeed
- [x] Manual smoke per plan Task 14 step 4
EOF
)"
```

---

## Self-Review

**Spec coverage:**
- ✅ Domain rename (Task 2)
- ✅ Math rewrite (Task 3)
- ✅ Repo + queries (Tasks 1, 4, 5)
- ✅ LogWantUseCase (Task 6)
- ✅ Get*PointsUseCase (Task 7)
- ✅ Seed (Task 8)
- ✅ Sync DTO (Task 9)
- ✅ Server migration (Task 10)
- ✅ WantForm (Task 11)
- ✅ WantList + WantDetail (Task 12)
- ✅ Home + ExchangeRate + Onboarding (Task 13)
- ✅ Smoke + PR (Task 14)

**Placeholder scan:** clean. No TBD/TODO markers. Each task has concrete file paths, code blocks, exact commands.

**Type consistency:** `unitsPerPoint: Int` consistent across model, SQL (Long → Int cast at boundary), repo, sync DTO, UI. `pointsSpent: Int` consistent across WantLog, repo, sync DTO, use cases. `effectiveUnitsPerPoint(unitsPerPoint, rate): Int` consistent across PointCalculator + LogWantUseCase + ExchangeRateViewModel.
