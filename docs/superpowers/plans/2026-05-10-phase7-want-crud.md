# Phase 7 — Want CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build CRUD for `WantActivity` (list / detail / add-edit form / hide / delete), reconcile the seeded set to the new 14-item canvas list with explicit per-row icons, adopt the new exchange rate ladder (1.0/1.2/1.4/1.6/2.0×), surface a one-shot rate-ladder migration banner.

**Architecture:** Add two columns to `WantActivity` (`iconKey`, `hiddenAt`) via SQLDelight migration 6. Repository gains `hideWantActivity` / `unhideWantActivity` and a visibility-aware `getWantActivities`. New shared `SetupUserWantActivitiesUseCase.reconcile(userId)` runs once on app start (idempotent). Android UI mirrors the Phase 5e Habit CRUD pattern (List/Detail/Form × Screen/VM), entered from YouHub → Wants. Today gains long-press → WantDetail and a one-shot rate-ladder migration snackbar.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Compose Material 3, Navigation-Compose, Robolectric+JUnit4, kotlinx-coroutines-test, kotlinx-datetime.

Spec: `docs/superpowers/specs/2026-05-10-phase7-want-crud-design.md`. Decisions: `docs/design/claude-design-decisions-want-crud.md`. Seed list: `docs/design/claude-design-followup-wants-seed.md`.

---

## File Structure

| Type | File | Responsibility |
|---|---|---|
| modify | `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` | Add `iconKey` + `hiddenAt` columns to `LocalWantActivity`; new queries `hideWantActivity`, `unhideWantActivity`, `getAllWantActivitiesForUser`. Update existing `getWantActivitiesForUser` to filter hidden. |
| create | `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/6.sqm` | ALTER TABLE add `iconKey TEXT`, `hiddenAt INTEGER` (both NULL default). |
| modify | `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt` | Add `iconKey: String?`, `hiddenAt: Instant?`. |
| modify | `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt` | New methods + visibility-aware getter. |
| modify | `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt` | Implement new methods; persist `iconKey` + `hiddenAt`. |
| modify | `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt` | Replace 15-item list with 14 canonical items + iconKey. |
| modify | `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCase.kt` | Add `reconcile(userId)` (additive, idempotent). |
| modify | `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculator.kt` | Update `tiers` to 1.0/1.2/1.4/1.6/2.0×. |
| create | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListScreen.kt` | Compose: visible/hidden sections + FAB. |
| create | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListViewModel.kt` | Observes wants + hide/unhide. |
| create | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt` | Compose: hero + 7d timeline + actions. |
| create | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt` | Loads activity + last 7d logs. |
| create | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormScreen.kt` | Compose: form. |
| create | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModel.kt` | Add/edit/save/delete + cost-edit warning. |
| create | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/WantIconKey.kt` | Curated 13-glyph map + legacy fallback. |
| create | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/WantIconPicker.kt` | Bottom sheet picker. |
| modify | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt` | Long-press want → Detail; render rate-ladder banner; use `resolveWantIcon`. |
| modify | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt` | Banner state + dismiss. |
| modify | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModel.kt` | Use visibility-filtered repo for comparison rows. |
| modify | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt` | "Wants" row → WantList. |
| modify | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` | Routes `WantList`, `WantDetail`, `WantForm`; YouHub `onOpenWants`; Today `onOpenWantDetail`. |
| create | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/preferences/AppFlagsPreferences.kt` | DataStore for `seenRateLadderUpgradeBanner`. |
| modify | `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` | Wire `appFlagsPreferences` + invoke `setupUserWantActivitiesUseCase.reconcile(userId)` on app start. |

---

## Task 1: Schema + model

**Files:**
- Create: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/6.sqm`
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt`

- [ ] **Step 1: Create migration 6.sqm**

`mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/6.sqm`:
```sql
-- Phase 7: WantActivity gains iconKey + hiddenAt for explicit icons + hide/delete.
ALTER TABLE LocalWantActivity ADD COLUMN iconKey TEXT;
ALTER TABLE LocalWantActivity ADD COLUMN hiddenAt INTEGER;
```

- [ ] **Step 2: Update CREATE TABLE in HabitTrackerDatabase.sq**

Locate the `CREATE TABLE IF NOT EXISTS LocalWantActivity` block (around line 68) and replace with:
```sql
CREATE TABLE IF NOT EXISTS LocalWantActivity (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    costPerUnit REAL NOT NULL,
    isCustom INTEGER NOT NULL DEFAULT 0,
    updatedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    iconKey TEXT,
    hiddenAt INTEGER
);
```

- [ ] **Step 3: Update queries in HabitTrackerDatabase.sq**

Locate the `-- LocalWantActivity queries` section (around line 166). Replace `upsertWantActivity` + `getWantActivitiesForUser` and add new queries:
```sql
-- LocalWantActivity queries
upsertWantActivity:
INSERT OR REPLACE INTO LocalWantActivity (id, userId, name, unit, costPerUnit, isCustom, updatedAt, syncedAt, iconKey, hiddenAt)
VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?);

getWantActivitiesForUser:
SELECT * FROM LocalWantActivity WHERE userId = ? AND hiddenAt IS NULL;

getAllWantActivitiesForUser:
SELECT * FROM LocalWantActivity WHERE userId = ?;

hideWantActivity:
UPDATE LocalWantActivity SET hiddenAt = ?, syncedAt = NULL WHERE id = ? AND userId = ?;

unhideWantActivity:
UPDATE LocalWantActivity SET hiddenAt = NULL, syncedAt = NULL WHERE id = ? AND userId = ?;
```

Find `mergePulledWantActivity` (around line 227) and replace with:
```sql
mergePulledWantActivity:
INSERT OR REPLACE INTO LocalWantActivity (id, userId, name, unit, costPerUnit, isCustom, updatedAt, syncedAt, iconKey, hiddenAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

- [ ] **Step 4: Update WantActivity model**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt`:
```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class WantActivity(
    val id: String,
    val name: String,
    val unit: String,
    val costPerUnit: Double,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
    val updatedAt: Instant = Instant.fromEpochMilliseconds(0L),
    val syncedAt: Instant? = null,
    val iconKey: String? = null,
    val hiddenAt: Instant? = null,
)
```

- [ ] **Step 5: Build shared module**

Run: `rtk ./gradlew :mobile:shared:compileKotlinAndroid 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL or compile errors only inside `LocalWantActivityRepository` (which now sees extra SQLDelight columns). Those are fixed in Task 2.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq \
    mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/6.sqm \
    mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt
rtk git commit -m "feat(want): schema migration 6 — add iconKey + hiddenAt to WantActivity"
```

---

## Task 2: Repository hide/unhide + iconKey persistence

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/LocalWantActivityRepositoryTest.kt`

- [ ] **Step 1: Add new repo methods to interface**

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt`:
```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface WantActivityRepository {
    fun observeWantActivities(userId: String): Flow<List<WantActivity>>
    suspend fun getWantActivities(userId: String): List<WantActivity>
    suspend fun getAllWantActivitiesForUser(userId: String): List<WantActivity>
    suspend fun saveWantActivity(activity: WantActivity, userId: String)
    suspend fun hideWantActivity(id: String, userId: String, hiddenAt: Instant)
    suspend fun unhideWantActivity(id: String, userId: String)
    suspend fun migrateUserId(oldUserId: String, newUserId: String)
    suspend fun clearForUser(userId: String)
    suspend fun getUnsyncedFor(userId: String): List<WantActivity>
    suspend fun markSynced(id: String, syncedAt: Instant)
    suspend fun getByIdsForUser(userId: String, ids: List<String>): List<WantActivity>
    suspend fun mergePulled(row: WantActivity)
}
```

- [ ] **Step 2: Implement in LocalWantActivityRepository**

In `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt`:

a) Update the SQLDelight row → domain mapper (private extension; conventionally named `toDomain()` returning `WantActivity`) to include the two new fields:
```kotlin
val iconKey: String? = row.iconKey
val hiddenAt: Instant? = row.hiddenAt?.let { Instant.fromEpochMilliseconds(it) }
```
And pass both into the constructed `WantActivity`.

b) Update `saveWantActivity` to pass `iconKey` and `hiddenAt` to `upsertWantActivity` (10 args now, matching the SQL). Set `hiddenAt` parameter to `activity.hiddenAt?.toEpochMilliseconds()`.

c) Update `mergePulled` to pass all 10 args including `iconKey`, `hiddenAt`, `syncedAt` for sync upserts.

d) Add new methods:
```kotlin
override suspend fun getAllWantActivitiesForUser(userId: String): List<WantActivity> =
    db.habitTrackerDatabaseQueries
        .getAllWantActivitiesForUser(userId)
        .executeAsList()
        .map { it.toDomain() }

override suspend fun hideWantActivity(id: String, userId: String, hiddenAt: Instant) {
    db.habitTrackerDatabaseQueries.hideWantActivity(hiddenAt.toEpochMilliseconds(), id, userId)
}

override suspend fun unhideWantActivity(id: String, userId: String) {
    db.habitTrackerDatabaseQueries.unhideWantActivity(id, userId)
}
```

The existing `getWantActivities`/`observeWantActivities` already call `getWantActivitiesForUser`, which is now visibility-filtered by the SQL change in Task 1. No code change needed at the Kotlin layer for that.

- [ ] **Step 3: Write test**

`mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/LocalWantActivityRepositoryTest.kt`:
```kotlin
package com.habittracker.data.repository

import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class LocalWantActivityRepositoryTest {
    private val userId = "u1"

    private fun seed(id: String, iconKey: String? = null, hiddenAt: Instant? = null) =
        WantActivity(
            id = id,
            name = "n-$id",
            unit = "minutes",
            costPerUnit = 1.0,
            isCustom = false,
            updatedAt = Instant.fromEpochMilliseconds(1_000),
            syncedAt = null,
            iconKey = iconKey,
            hiddenAt = hiddenAt,
        )

    @Test
    fun `getWantActivities returns only un-hidden rows`() = runTest {
        val repo = LocalWantActivityRepository(HabitTrackerDatabase(inMemoryDriver()))
        repo.saveWantActivity(seed("a"), userId)
        repo.saveWantActivity(seed("b"), userId)
        repo.hideWantActivity("a", userId, Instant.fromEpochMilliseconds(2_000))

        val visible = repo.getWantActivities(userId)
        assertEquals(listOf("b"), visible.map { it.id })

        val all = repo.getAllWantActivitiesForUser(userId)
        assertEquals(setOf("a", "b"), all.map { it.id }.toSet())
    }

    @Test
    fun `hideWantActivity sets hiddenAt and clears syncedAt`() = runTest {
        val repo = LocalWantActivityRepository(HabitTrackerDatabase(inMemoryDriver()))
        repo.saveWantActivity(seed("a"), userId)
        repo.markSynced("a", Instant.fromEpochMilliseconds(1_500))
        repo.hideWantActivity("a", userId, Instant.fromEpochMilliseconds(2_000))

        val hidden = repo.getAllWantActivitiesForUser(userId).single()
        assertNotNull(hidden.hiddenAt)
        assertNull(hidden.syncedAt)
    }

    @Test
    fun `unhideWantActivity clears hiddenAt and syncedAt`() = runTest {
        val repo = LocalWantActivityRepository(HabitTrackerDatabase(inMemoryDriver()))
        repo.saveWantActivity(seed("a", hiddenAt = Instant.fromEpochMilliseconds(2_000)), userId)
        repo.markSynced("a", Instant.fromEpochMilliseconds(2_500))
        repo.unhideWantActivity("a", userId)

        val visible = repo.getWantActivities(userId)
        assertEquals(1, visible.size)
        assertNull(visible.single().hiddenAt)
        assertNull(visible.single().syncedAt)
    }

    @Test
    fun `saveWantActivity persists iconKey`() = runTest {
        val repo = LocalWantActivityRepository(HabitTrackerDatabase(inMemoryDriver()))
        repo.saveWantActivity(seed("a", iconKey = "play_circle"), userId)
        val row = repo.getWantActivities(userId).single()
        assertEquals("play_circle", row.iconKey)
    }
}
```

> The `inMemoryDriver()` factory should already exist in `commonTest`; if not, mirror its usage from another existing repo test (e.g. `LocalHabitLogRepositoryTest`) — it's an `expect`/`actual` pair returning a SQLDelight `JdbcSqliteDriver` for testDebug.

- [ ] **Step 4: Run tests**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*LocalWantActivityRepositoryTest*" 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL, 4 tests passing.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt \
    mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/LocalWantActivityRepositoryTest.kt
rtk git commit -m "feat(want): repo hide/unhide + visible filter + iconKey persistence"
```

---

## Task 3: Seed list + reconcile

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCase.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCaseTest.kt`

- [ ] **Step 1: Replace seeded WANTS list**

In `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt`, replace the `wantActivities: List<WantActivity>` block (around line 65) with:

```kotlin
val wantActivities: List<WantActivity> = listOf(
    WantActivity("20000000-0000-0000-0000-000000000001", "TikTok",          "minutes",  1.0, iconKey = "play_circle"),
    WantActivity("20000000-0000-0000-0000-000000000002", "YouTube Shorts",  "minutes",  1.0, iconKey = "play_circle"),
    WantActivity("20000000-0000-0000-0000-000000000003", "YouTube",         "minutes",  0.1, iconKey = "smart_display"),
    WantActivity("20000000-0000-0000-0000-000000000004", "Netflix",         "minutes",  0.1, iconKey = "local_movies"),
    WantActivity("20000000-0000-0000-0000-000000000005", "Twitter/X",       "minutes",  0.5, iconKey = "chat_bubble"),
    WantActivity("20000000-0000-0000-0000-000000000006", "Instagram",       "minutes",  0.5, iconKey = "photo_camera"),
    WantActivity("20000000-0000-0000-0000-000000000007", "Reddit",          "minutes",  0.5, iconKey = "forum"),
    WantActivity("20000000-0000-0000-0000-000000000008", "Gaming",          "minutes",  0.5, iconKey = "sports_esports"),
    WantActivity("20000000-0000-0000-0000-000000000009", "Online shopping", "minutes",  0.5, iconKey = "shopping_bag"),
    WantActivity("20000000-0000-0000-0000-000000000010", "Junk food",       "meals",    5.0, iconKey = "restaurant"),
    WantActivity("20000000-0000-0000-0000-000000000011", "Snacks",          "servings", 2.0, iconKey = "restaurant"),
    WantActivity("20000000-0000-0000-0000-000000000012", "Sweets",          "pieces",   2.0, iconKey = "cake"),
    WantActivity("20000000-0000-0000-0000-000000000013", "Sugary drinks",   "drinks",   2.0, iconKey = "local_drink"),
    WantActivity("20000000-0000-0000-0000-000000000014", "Coffee",          "cups",     1.0, iconKey = "local_cafe"),
)
```

- [ ] **Step 2: Update `SetupUserWantActivitiesUseCase`**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCase.kt`:
```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.domain.model.WantActivity
import kotlinx.datetime.Clock

class SetupUserWantActivitiesUseCase(
    private val wantActivityRepository: WantActivityRepository,
    private val clock: Clock = Clock.System,
) {
    /** Insert a known list (used by onboarding seed for new users). */
    suspend fun execute(userId: String, activities: List<WantActivity>): Result<Unit> =
        runCatching {
            val now = clock.now()
            activities.forEach { activity ->
                wantActivityRepository.saveWantActivity(
                    activity.copy(updatedAt = now),
                    userId,
                )
            }
        }

    /**
     * Idempotent reconciliation. For each canonical seeded id, if the user has no
     * row with that id, insert it. Existing rows untouched.
     */
    suspend fun reconcile(userId: String): Result<Unit> = runCatching {
        val existing = wantActivityRepository.getAllWantActivitiesForUser(userId)
        val existingIds = existing.map { it.id }.toSet()
        val now = clock.now()
        SeedData.wantActivities
            .filter { it.id !in existingIds }
            .forEach { seed ->
                wantActivityRepository.saveWantActivity(
                    seed.copy(updatedAt = now),
                    userId,
                )
            }
    }
}
```

- [ ] **Step 3: Write test**

`mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCaseTest.kt`:
```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.SeedData
import com.habittracker.data.repository.LocalWantActivityRepository
import com.habittracker.data.repository.inMemoryDriver
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SetupUserWantActivitiesUseCaseTest {
    private val userId = "u1"
    private val fixedClock = object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(123_000) }

    private fun newSut(): Pair<SetupUserWantActivitiesUseCase, LocalWantActivityRepository> {
        val driver = inMemoryDriver()
        val repo = LocalWantActivityRepository(HabitTrackerDatabase(driver))
        return SetupUserWantActivitiesUseCase(repo, fixedClock) to repo
    }

    @Test
    fun `reconcile inserts all 14 seed items for new user`() = runTest {
        val (sut, repo) = newSut()
        sut.reconcile(userId).getOrThrow()
        assertEquals(14, repo.getAllWantActivitiesForUser(userId).size)
    }

    @Test
    fun `reconcile preserves customized cost on existing seed row`() = runTest {
        val (sut, repo) = newSut()
        val customized = SeedData.wantActivities.first().copy(costPerUnit = 5.0)
        repo.saveWantActivity(customized, userId)

        sut.reconcile(userId).getOrThrow()

        val tiktok = repo.getAllWantActivitiesForUser(userId)
            .single { it.id == customized.id }
        assertEquals(5.0, tiktok.costPerUnit)
    }

    @Test
    fun `reconcile preserves hidden state on existing seed row`() = runTest {
        val (sut, repo) = newSut()
        val seed = SeedData.wantActivities.first()
        repo.saveWantActivity(seed, userId)
        repo.hideWantActivity(seed.id, userId, Instant.fromEpochMilliseconds(2_000))

        sut.reconcile(userId).getOrThrow()

        val row = repo.getAllWantActivitiesForUser(userId).single { it.id == seed.id }
        assertNotNull(row.hiddenAt)
    }

    @Test
    fun `reconcile is idempotent`() = runTest {
        val (sut, repo) = newSut()
        sut.reconcile(userId).getOrThrow()
        val firstCount = repo.getAllWantActivitiesForUser(userId).size
        sut.reconcile(userId).getOrThrow()
        val secondCount = repo.getAllWantActivitiesForUser(userId).size
        assertEquals(firstCount, secondCount)
    }
}
```

- [ ] **Step 4: Run tests**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*SetupUserWantActivitiesUseCaseTest*" 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL, 4 tests passing.

- [ ] **Step 5: Wire reconcile into AppContainer**

In `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`, find the existing `seedLocalDataIfEmpty` (or equivalent app-startup hook called from `AppNavigation.kt`'s startup `LaunchedEffect`). Right after that call, invoke:

```kotlin
runCatching { setupUserWantActivitiesUseCase.reconcile(currentUserId()) }
```

Failure to reconcile must not block app start.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SeedData.kt \
    mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCase.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCaseTest.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt
rtk git commit -m "feat(want): replace seed list to 14 items + idempotent reconcile on app start"
```

---

## Task 4: Rate ladder update

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculator.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculatorTest.kt`

- [ ] **Step 1: Update tiers list**

In `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculator.kt`, replace the `tiers` `val` with:
```kotlin
val tiers: List<RateTier> = listOf(
    RateTier(level = 1, rate = 1.0, minStreak = 0,  maxStreak = 6),
    RateTier(level = 2, rate = 1.2, minStreak = 7,  maxStreak = 13),
    RateTier(level = 3, rate = 1.4, minStreak = 14, maxStreak = 20),
    RateTier(level = 4, rate = 1.6, minStreak = 21, maxStreak = 29),
    RateTier(level = 5, rate = 2.0, minStreak = 30, maxStreak = null),
)
```

Tier breakpoints (0 / 7 / 14 / 21 / 30) unchanged.

- [ ] **Step 2: Rebaseline ExchangeRateCalculatorTest**

In `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculatorTest.kt`, replace any rate-equality assertion with the new multipliers:
- `rateFor(0)` → `1.0`
- `rateFor(6)` → `1.0`
- `rateFor(7)` → `1.2`
- `rateFor(13)` → `1.2`
- `rateFor(14)` → `1.4`
- `rateFor(20)` → `1.4`
- `rateFor(21)` → `1.6`
- `rateFor(29)` → `1.6`
- `rateFor(30)` → `2.0`
- `rateFor(365)` → `2.0`

`daysToNextTier` integer counts stay the same (breakpoints unchanged).

- [ ] **Step 3: Run tests**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*ExchangeRateCalculatorTest*" 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run full shared suite**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL. If `LogWantUseCase` / `GetPointBalanceUseCase` / `GetDayPointsUseCase` tests break because they hardcode spent-pt values for specific streaks, recompute the expected values using the new multipliers:
- streak 7..13 → ×1.2
- streak 14..20 → ×1.4
- streak 21..29 → ×1.6
- streak 30+ → ×2.0

Update the assertions inline.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculator.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculatorTest.kt
rtk git commit -m "feat(rate): exchange rate ladder 1.0/1.2/1.4/1.6/2.0×"
```

---

## Task 5: AppFlagsPreferences

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/preferences/AppFlagsPreferences.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`

- [ ] **Step 1: Create AppFlagsPreferences**

`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/preferences/AppFlagsPreferences.kt`:
```kotlin
package com.jktdeveloper.habitto.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appFlagsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_flags",
)

class AppFlagsPreferences(private val context: Context) {

    private object Keys {
        val SEEN_RATE_LADDER_BANNER = booleanPreferencesKey("seen_rate_ladder_upgrade_banner")
    }

    val seenRateLadderUpgradeBanner: Flow<Boolean> = context.appFlagsDataStore.data
        .map { it[Keys.SEEN_RATE_LADDER_BANNER] ?: false }

    suspend fun current(): Boolean = seenRateLadderUpgradeBanner.first()

    suspend fun setSeenRateLadderUpgradeBanner(seen: Boolean) {
        context.appFlagsDataStore.edit { it[Keys.SEEN_RATE_LADDER_BANNER] = seen }
    }
}
```

- [ ] **Step 2: Wire into AppContainer**

In `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` add:
```kotlin
val appFlagsPreferences = AppFlagsPreferences(appContext)
```

Place it near the other `*Preferences` declarations. Add the import:
```kotlin
import com.jktdeveloper.habitto.preferences.AppFlagsPreferences
```

- [ ] **Step 3: Build**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/preferences/AppFlagsPreferences.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt
rtk git commit -m "feat(prefs): app flags datastore for one-shot rate ladder banner"
```

---

## Task 6: WantIconKey resolver + WantIconPicker

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/WantIconKey.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/WantIconPicker.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt`

- [ ] **Step 1: Create WantIconKey resolver**

`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/WantIconKey.kt`:
```kotlin
package com.jktdeveloper.habitto.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

/** Curated 13-glyph picker set. Stable string keys persist on WantActivity.iconKey. */
val WANT_ICON_KEYS: List<String> = listOf(
    "play_circle", "smart_display", "local_movies",
    "chat_bubble", "photo_camera", "forum", "sports_esports",
    "shopping_bag", "restaurant", "cake", "local_drink",
    "local_cafe", "more_horiz",
)

fun wantIconForKey(key: String?): ImageVector = when (key) {
    "play_circle" -> Icons.Default.PlayCircle
    "smart_display" -> Icons.Default.SmartDisplay
    "local_movies" -> Icons.Default.LocalMovies
    "chat_bubble" -> Icons.Default.ChatBubble
    "photo_camera" -> Icons.Default.PhotoCamera
    "forum" -> Icons.Default.Forum
    "sports_esports" -> Icons.Default.SportsEsports
    "shopping_bag" -> Icons.Default.ShoppingBag
    "restaurant" -> Icons.Default.Restaurant
    "cake" -> Icons.Default.Cake
    "local_drink" -> Icons.Default.LocalDrink
    "local_cafe" -> Icons.Default.LocalCafe
    else -> Icons.Default.MoreHoriz
}

/** Resolve icon for a WantActivity. Prefer explicit key; fallback to legacy name match. */
fun resolveWantIcon(iconKey: String?, name: String): ImageVector {
    if (iconKey != null) return wantIconForKey(iconKey)
    return legacyWantIconByName(name)
}

private fun legacyWantIconByName(name: String): ImageVector = when {
    name.contains("twitter", ignoreCase = true) || name.contains("/x", ignoreCase = true) -> Icons.Default.ChatBubble
    name.contains("instagram", ignoreCase = true) -> Icons.Default.PhotoCamera
    name.contains("tiktok", ignoreCase = true) || name.contains("scroll", ignoreCase = true)
        || name.contains("reel", ignoreCase = true) || name.contains("short", ignoreCase = true) -> Icons.Default.PlayCircle
    name.contains("youtube", ignoreCase = true) -> Icons.Default.SmartDisplay
    name.contains("netflix", ignoreCase = true) || name.contains("stream", ignoreCase = true) -> Icons.Default.LocalMovies
    name.contains("reddit", ignoreCase = true) -> Icons.Default.Forum
    name.contains("game", ignoreCase = true) || name.contains("valorant", ignoreCase = true) -> Icons.Default.SportsEsports
    name.contains("snack", ignoreCase = true) || name.contains("food", ignoreCase = true)
        || name.contains("junk", ignoreCase = true) -> Icons.Default.Restaurant
    name.contains("donut", ignoreCase = true) || name.contains("dessert", ignoreCase = true)
        || name.contains("sweet", ignoreCase = true) -> Icons.Default.Cake
    name.contains("shop", ignoreCase = true) || name.contains("purchase", ignoreCase = true) -> Icons.Default.ShoppingBag
    name.contains("drink", ignoreCase = true) || name.contains("sugary", ignoreCase = true) -> Icons.Default.LocalDrink
    name.contains("coffee", ignoreCase = true) -> Icons.Default.LocalCafe
    else -> Icons.Default.MoreHoriz
}
```

- [ ] **Step 2: Create WantIconPicker bottom sheet**

`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/WantIconPicker.kt`:
```kotlin
package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WantIconPicker(
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Pick an icon",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                items(WANT_ICON_KEYS) { key ->
                    val isSelected = key == selected
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                onPick(key)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            wantIconForKey(key),
                            contentDescription = key,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Replace HomeScreen icon resolver**

In `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt`:
1. Delete the existing `private fun wantIcon(name: String): ImageVector` block (around line 679–695).
2. Add import: `import com.jktdeveloper.habitto.ui.components.resolveWantIcon`.
3. Replace each call site using `wantIcon(activity.name)` with `resolveWantIcon(activity.iconKey, activity.name)`.

- [ ] **Step 4: Build**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/WantIconKey.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/WantIconPicker.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt
rtk git commit -m "feat(want): icon key resolver + curated 13-glyph picker bottom sheet"
```

---

## Task 7: WantListViewModel + WantListScreen

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListViewModel.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListScreen.kt`
- Test: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantListViewModelTest.kt`

- [ ] **Step 1: Implement WantListViewModel**

`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListViewModel.kt`:
```kotlin
package com.jktdeveloper.habitto.ui.want

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.domain.model.WantActivity
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class WantListUi(
    val seeded: List<WantActivity>,
    val custom: List<WantActivity>,
    val hidden: List<WantActivity>,
    val showHidden: Boolean = false,
    val toast: String? = null,
)

class WantListViewModel private constructor(
    private val repo: WantActivityRepository,
    private val userIdProvider: () -> String,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(
        WantListUi(seeded = emptyList(), custom = emptyList(), hidden = emptyList())
    )
    val state: StateFlow<WantListUi> = _state.asStateFlow()

    constructor(container: AppContainer) : this(
        repo = container.wantActivityRepository,
        userIdProvider = { container.currentUserId() },
    )

    init { reload() }

    private fun reload() {
        viewModelScope.launch {
            val userId = userIdProvider()
            val all = repo.getAllWantActivitiesForUser(userId).sortedBy { it.name.lowercase() }
            _state.value = _state.value.copy(
                seeded = all.filter { !it.isCustom && it.hiddenAt == null },
                custom = all.filter { it.isCustom && it.hiddenAt == null },
                hidden = all.filter { !it.isCustom && it.hiddenAt != null },
            )
        }
    }

    fun toggleShowHidden() {
        _state.value = _state.value.copy(showHidden = !_state.value.showHidden)
    }

    fun hide(activityId: String, name: String) {
        viewModelScope.launch {
            repo.hideWantActivity(activityId, userIdProvider(), clock.now())
            reload()
            _state.value = _state.value.copy(toast = "$name hidden")
        }
    }

    fun unhide(activityId: String) {
        viewModelScope.launch {
            repo.unhideWantActivity(activityId, userIdProvider())
            reload()
        }
    }

    fun consumeToast() { _state.value = _state.value.copy(toast = null) }

    companion object {
        fun forTest(
            repo: WantActivityRepository,
            userIdProvider: () -> String,
            clock: Clock = Clock.System,
        ) = WantListViewModel(repo, userIdProvider, clock)
    }
}
```

- [ ] **Step 2: Write VM test**

`mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantListViewModelTest.kt`:
```kotlin
package com.jktdeveloper.habitto.ui.want

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.repository.LocalWantActivityRepository
import com.habittracker.data.repository.inMemoryDriver
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class WantListViewModelTest {
    private val userId = "u1"
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun seed(id: String, custom: Boolean = false, hiddenAt: Instant? = null) =
        WantActivity(
            id = id, name = "n-$id", unit = "minutes", costPerUnit = 1.0,
            isCustom = custom, hiddenAt = hiddenAt,
        )

    @Test
    fun `partition splits seeded vs custom`() = runTest {
        val repo = LocalWantActivityRepository(HabitTrackerDatabase(inMemoryDriver()))
        repo.saveWantActivity(seed("s"), userId)
        repo.saveWantActivity(seed("c", custom = true), userId)

        val vm = WantListViewModel.forTest(repo, { userId })
        val state = vm.state.first { it.seeded.isNotEmpty() || it.custom.isNotEmpty() }
        assertEquals(listOf("s"), state.seeded.map { it.id })
        assertEquals(listOf("c"), state.custom.map { it.id })
    }

    @Test
    fun `hide moves seeded want from visible to hidden bucket`() = runTest {
        val repo = LocalWantActivityRepository(HabitTrackerDatabase(inMemoryDriver()))
        repo.saveWantActivity(seed("s"), userId)
        val vm = WantListViewModel.forTest(repo, { userId })
        vm.state.first { it.seeded.isNotEmpty() }

        vm.hide("s", "n-s")
        val after = vm.state.first { it.hidden.isNotEmpty() }
        assertEquals(emptyList<String>(), after.seeded.map { it.id })
        assertEquals(listOf("s"), after.hidden.map { it.id })
        assertTrue(after.toast?.contains("hidden") == true)
    }

    @Test
    fun `unhide moves seeded want back to visible bucket`() = runTest {
        val repo = LocalWantActivityRepository(HabitTrackerDatabase(inMemoryDriver()))
        repo.saveWantActivity(seed("s", hiddenAt = Instant.fromEpochMilliseconds(2_000)), userId)
        val vm = WantListViewModel.forTest(repo, { userId })
        vm.state.first { it.hidden.isNotEmpty() }

        vm.unhide("s")
        val after = vm.state.first { it.seeded.isNotEmpty() }
        assertEquals(listOf("s"), after.seeded.map { it.id })
    }

    @Test
    fun `sorts seeded alphabetically`() = runTest {
        val repo = LocalWantActivityRepository(HabitTrackerDatabase(inMemoryDriver()))
        repo.saveWantActivity(seed("s2").copy(name = "Bravo"), userId)
        repo.saveWantActivity(seed("s1").copy(name = "Alpha"), userId)
        val vm = WantListViewModel.forTest(repo, { userId })
        val state = vm.state.first { it.seeded.size == 2 }
        assertEquals(listOf("Alpha", "Bravo"), state.seeded.map { it.name })
    }
}
```

- [ ] **Step 3: Implement WantListScreen**

`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListScreen.kt`:
```kotlin
package com.jktdeveloper.habitto.ui.want

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.WantActivity
import com.jktdeveloper.habitto.ui.components.resolveWantIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WantListScreen(
    viewModel: WantListViewModel,
    onBack: () -> Unit,
    onAddWant: () -> Unit,
    onEditWant: (id: String) -> Unit,
    onOpenDetail: (id: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val toast = state.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wants", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.showHidden) "Hide hidden" else "Show hidden (${state.hidden.size})") },
                                onClick = {
                                    viewModel.toggleShowHidden()
                                    menuOpen = false
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add want") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onAddWant,
            )
        },
    ) { padding ->
        if (state.seeded.isEmpty() && state.custom.isEmpty() && state.hidden.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No wants yet — tap + to add one",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.seeded.isNotEmpty()) {
                item { SectionHeader("Seeded · ${state.seeded.size}") }
                items(state.seeded, key = { it.id }) { activity ->
                    WantRow(
                        activity = activity,
                        trailing = {
                            IconButton(onClick = { viewModel.hide(activity.id, activity.name) }) {
                                Icon(Icons.Default.VisibilityOff,
                                     contentDescription = "Hide ${activity.name}")
                            }
                        },
                        onTap = { onOpenDetail(activity.id) },
                    )
                }
            }
            if (state.custom.isNotEmpty()) {
                item { SectionHeader("Custom · ${state.custom.size}") }
                items(state.custom, key = { it.id }) { activity ->
                    WantRow(
                        activity = activity,
                        trailing = {
                            IconButton(onClick = { onEditWant(activity.id) }) {
                                Icon(Icons.Default.Edit,
                                     contentDescription = "Edit ${activity.name}")
                            }
                        },
                        onTap = { onOpenDetail(activity.id) },
                    )
                }
            }
            if (state.showHidden && state.hidden.isNotEmpty()) {
                item { SectionHeader("Hidden · ${state.hidden.size}") }
                items(state.hidden, key = { it.id }) { activity ->
                    WantRow(
                        activity = activity,
                        trailing = {
                            IconButton(onClick = { viewModel.unhide(activity.id) }) {
                                Icon(Icons.Default.Visibility,
                                     contentDescription = "Unhide ${activity.name}")
                            }
                        },
                        onTap = { onOpenDetail(activity.id) },
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun WantRow(
    activity: WantActivity,
    trailing: @Composable () -> Unit,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                resolveWantIcon(activity.iconKey, activity.name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(activity.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "−${activity.costPerUnit} pt / ${activity.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        trailing()
    }
}
```

- [ ] **Step 4: Run tests + build**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*WantListViewModelTest*" :mobile:androidApp:assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL, 4 tests passing.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListViewModel.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantListScreen.kt \
    mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantListViewModelTest.kt
rtk git commit -m "feat(want): WantList VM + Screen with seeded/custom/hidden sections"
```

---

## Task 8: WantDetailViewModel + WantDetailScreen

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt`

- [ ] **Step 1: Implement WantDetailViewModel**

`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt`:
```kotlin
package com.jktdeveloper.habitto.ui.want

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.usecase.ExchangeRateCalculator
import com.habittracker.domain.usecase.GetUserStreakOnDayUseCase
import com.habittracker.domain.usecase.PointCalculator
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class TimedLog(val time: LocalTime, val qty: Double, val pointsAtLog: Int)
data class DayLogs(val date: LocalDate, val items: List<TimedLog>)

data class WantDetailUi(
    val isLoading: Boolean = true,
    val want: WantActivity? = null,
    val totalSpent7d: Int = 0,
    val timesLogged7d: Int = 0,
    val timeline: List<DayLogs> = emptyList(),
    val toast: String? = null,
)

class WantDetailViewModel private constructor(
    private val activityId: String,
    private val wantActivityRepo: WantActivityRepository,
    private val wantLogRepo: WantLogRepository,
    private val getUserStreakOnDay: GetUserStreakOnDayUseCase,
    private val userIdProvider: () -> String,
    private val clock: Clock = Clock.System,
    private val tz: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(WantDetailUi())
    val state: StateFlow<WantDetailUi> = _state.asStateFlow()

    constructor(activityId: String, container: AppContainer) : this(
        activityId = activityId,
        wantActivityRepo = container.wantActivityRepository,
        wantLogRepo = container.wantLogRepository,
        getUserStreakOnDay = container.getUserStreakOnDayUseCase,
        userIdProvider = { container.currentUserId() },
    )

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val userId = userIdProvider()
            val want = wantActivityRepo.getAllWantActivitiesForUser(userId)
                .firstOrNull { it.id == activityId }
            if (want == null) {
                _state.value = _state.value.copy(isLoading = false, want = null)
                return@launch
            }
            val today = clock.now().toLocalDateTime(tz).date
            val sevenAgo = today.minus(6, DateTimeUnit.DAY)
            val windowStart = sevenAgo.atStartOfDayIn(tz)
            val windowEnd = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)
            val logs = wantLogRepo.getAllActiveLogsForUser(userId)
                .filter { it.activityId == activityId
                    && it.loggedAt >= windowStart && it.loggedAt < windowEnd }

            val byDate = logs.groupBy { it.loggedAt.toLocalDateTime(tz).date }
            val days = (0..6).map { offset ->
                val d = today.minus(offset, DateTimeUnit.DAY)
                val items = (byDate[d] ?: emptyList()).map { log ->
                    val streak = getUserStreakOnDay.execute(userId, d)
                    val rate = ExchangeRateCalculator.rateFor(streak)
                    val points = PointCalculator.pointsSpentWithRate(log.quantity, want.costPerUnit, rate)
                    TimedLog(
                        time = log.loggedAt.toLocalDateTime(tz).time,
                        qty = log.quantity,
                        pointsAtLog = points,
                    )
                }
                DayLogs(date = d, items = items)
            }
            val totalSpent = days.sumOf { it.items.sumOf { item -> item.pointsAtLog } }
            _state.value = WantDetailUi(
                isLoading = false,
                want = want,
                totalSpent7d = totalSpent,
                timesLogged7d = days.sumOf { it.items.size },
                timeline = days,
            )
        }
    }

    fun onTimerStub() {
        _state.value = _state.value.copy(toast = "Timer coming soon.")
    }

    fun consumeToast() { _state.value = _state.value.copy(toast = null) }

    fun hide() {
        viewModelScope.launch {
            wantActivityRepo.hideWantActivity(activityId, userIdProvider(), clock.now())
            _state.value = _state.value.copy(toast = "Hidden")
        }
    }

    fun delete() {
        viewModelScope.launch {
            wantActivityRepo.hideWantActivity(activityId, userIdProvider(), clock.now())
            _state.value = _state.value.copy(toast = "Deleted")
        }
    }
}
```

- [ ] **Step 2: Implement WantDetailScreen**

`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt`:
```kotlin
package com.jktdeveloper.habitto.ui.want

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.components.resolveWantIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WantDetailScreen(
    viewModel: WantDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val toast = state.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
            if (toast == "Hidden" || toast == "Deleted") onBack()
        }
    }
    var pendingDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        val want = state.want ?: run {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("Want not found") }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    resolveWantIcon(want.iconKey, want.name),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    want.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                if (!want.isCustom) {
                    AssistChip(onClick = {}, label = { Text("Seeded") })
                }
            }
            Text(
                "−${want.costPerUnit} pt per ${want.unit}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatCol(state.totalSpent7d.toString(), "Spent 7d")
                StatCol(state.timesLogged7d.toString(), "Logged 7d")
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::onTimerStub, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start timer")
                }
                FilledTonalButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Recent activity",
                 style = MaterialTheme.typography.titleMedium,
                 fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            state.timeline.forEach { day ->
                if (day.items.isEmpty()) return@forEach
                Text(
                    day.date.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Column {
                        day.items.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider()
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${item.time}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "−${item.pointsAtLog} pt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            if (want.isCustom) {
                TextButton(onClick = { pendingDelete = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null,
                         tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete want", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = viewModel::hide) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Hide from list")
                }
            }
        }

        if (pendingDelete) {
            AlertDialog(
                onDismissRequest = { pendingDelete = false },
                title = { Text("Delete ${state.want?.name}?") },
                text = { Text("Past logs stay in your history.") },
                confirmButton = {
                    Button(onClick = {
                        pendingDelete = false
                        viewModel.delete()
                    }) { Text("Delete") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { pendingDelete = false }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun StatCol(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 3: Build**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt
rtk git commit -m "feat(want): WantDetail VM + Screen — hero, 7d timeline, hide/delete, timer stub"
```

---

## Task 9: WantFormViewModel + WantFormScreen

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModel.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormScreen.kt`
- Test: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModelTest.kt`

- [ ] **Step 1: Implement WantFormViewModel**

`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModel.kt`:
```kotlin
package com.jktdeveloper.habitto.ui.want

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.WantActivity
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface FormMode {
    data object New : FormMode
    data class Edit(val activityId: String) : FormMode
}

data class WantFormUi(
    val mode: FormMode,
    val name: String = "",
    val unit: String = "minutes",
    val costInput: String = "1.0",
    val iconKey: String = "more_horiz",
    val hasPastLogs: Boolean = false,
    val originalCost: Double = 1.0,
    val isSaving: Boolean = false,
    val validationError: String? = null,
    val showCostEditWarning: Boolean = false,
    val saved: Boolean = false,
)

class WantFormViewModel private constructor(
    private val mode: FormMode,
    private val wantActivityRepo: WantActivityRepository,
    private val wantLogRepo: WantLogRepository,
    private val userIdProvider: () -> String,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(WantFormUi(mode = mode))
    val state: StateFlow<WantFormUi> = _state.asStateFlow()

    constructor(mode: FormMode, container: AppContainer) : this(
        mode = mode,
        wantActivityRepo = container.wantActivityRepository,
        wantLogRepo = container.wantLogRepository,
        userIdProvider = { container.currentUserId() },
    )

    init { load() }

    private fun load() {
        viewModelScope.launch {
            when (val m = mode) {
                FormMode.New -> Unit
                is FormMode.Edit -> {
                    val userId = userIdProvider()
                    val w = wantActivityRepo.getAllWantActivitiesForUser(userId)
                        .firstOrNull { it.id == m.activityId } ?: return@launch
                    val pastLogs = wantLogRepo.getAllActiveLogsForUser(userId)
                        .any { it.activityId == m.activityId }
                    _state.value = WantFormUi(
                        mode = m,
                        name = w.name,
                        unit = w.unit,
                        costInput = w.costPerUnit.toString(),
                        iconKey = w.iconKey ?: "more_horiz",
                        originalCost = w.costPerUnit,
                        hasPastLogs = pastLogs,
                    )
                }
            }
        }
    }

    fun onName(v: String) { _state.value = _state.value.copy(name = v, validationError = null) }
    fun onUnit(v: String) { _state.value = _state.value.copy(unit = v) }
    fun onIconKey(v: String) { _state.value = _state.value.copy(iconKey = v) }

    fun onCostInput(v: String) {
        val parsed = v.toDoubleOrNull()
        val warning = mode is FormMode.Edit
            && _state.value.hasPastLogs
            && parsed != null
            && parsed != _state.value.originalCost
        _state.value = _state.value.copy(
            costInput = v,
            validationError = null,
            showCostEditWarning = warning,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    fun save(onDone: () -> Unit) {
        val s = _state.value
        val cost = s.costInput.toDoubleOrNull()
        if (s.name.isBlank()) {
            _state.value = s.copy(validationError = "Name required")
            return
        }
        if (cost == null || cost < 0.0) {
            _state.value = s.copy(validationError = "Cost must be ≥ 0")
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(isSaving = true)
            val userId = userIdProvider()
            val activity = when (val m = s.mode) {
                FormMode.New -> WantActivity(
                    id = Uuid.random().toString(),
                    name = s.name,
                    unit = s.unit,
                    costPerUnit = cost,
                    isCustom = true,
                    createdByUserId = userId,
                    iconKey = s.iconKey,
                    updatedAt = clock.now(),
                )
                is FormMode.Edit -> {
                    val existing = wantActivityRepo.getAllWantActivitiesForUser(userId)
                        .firstOrNull { it.id == m.activityId } ?: return@launch
                    existing.copy(
                        name = s.name,
                        unit = s.unit,
                        costPerUnit = cost,
                        iconKey = s.iconKey,
                        updatedAt = clock.now(),
                    )
                }
            }
            wantActivityRepo.saveWantActivity(activity, userId)
            _state.value = _state.value.copy(isSaving = false, saved = true)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val m = mode as? FormMode.Edit ?: return
        viewModelScope.launch {
            wantActivityRepo.hideWantActivity(m.activityId, userIdProvider(), clock.now())
            onDone()
        }
    }

    companion object {
        fun forTest(
            mode: FormMode,
            wantActivityRepo: WantActivityRepository,
            wantLogRepo: WantLogRepository,
            userIdProvider: () -> String,
            clock: Clock = Clock.System,
        ) = WantFormViewModel(mode, wantActivityRepo, wantLogRepo, userIdProvider, clock)
    }
}
```

- [ ] **Step 2: Write VM test**

`mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModelTest.kt`:
```kotlin
package com.jktdeveloper.habitto.ui.want

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.repository.LocalWantActivityRepository
import com.habittracker.data.repository.LocalWantLogRepository
import com.habittracker.data.repository.inMemoryDriver
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class WantFormViewModelTest {
    private val userId = "u1"
    private val fixedClock = object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(1_000_000) }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newRepos(): Pair<LocalWantActivityRepository, LocalWantLogRepository> {
        val db = HabitTrackerDatabase(inMemoryDriver())
        return LocalWantActivityRepository(db) to LocalWantLogRepository(db)
    }

    @Test
    fun `new mode saves a custom activity`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        val vm = WantFormViewModel.forTest(FormMode.New, wantRepo, logRepo, { userId }, fixedClock)
        vm.onName("Bingewatch")
        vm.onUnit("episode")
        vm.onCostInput("0.5")
        vm.onIconKey("local_movies")
        var done = false
        vm.save { done = true }
        assertTrue(done)
        val saved = wantRepo.getAllWantActivitiesForUser(userId).single()
        assertEquals("Bingewatch", saved.name)
        assertTrue(saved.isCustom)
        assertEquals("local_movies", saved.iconKey)
        assertEquals(0.5, saved.costPerUnit, 0.0)
    }

    @Test
    fun `edit mode loads existing fields`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        wantRepo.saveWantActivity(
            WantActivity(id = "a", name = "TikTok", unit = "minutes",
                         costPerUnit = 1.0, iconKey = "play_circle"),
            userId,
        )
        val vm = WantFormViewModel.forTest(FormMode.Edit("a"), wantRepo, logRepo, { userId }, fixedClock)
        val loaded = vm.state.first { it.name.isNotEmpty() }
        assertEquals("TikTok", loaded.name)
        assertEquals("play_circle", loaded.iconKey)
        assertEquals("1.0", loaded.costInput)
    }

    @Test
    fun `cost change triggers warning when past logs exist`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        wantRepo.saveWantActivity(
            WantActivity(id = "a", name = "TikTok", unit = "minutes", costPerUnit = 1.0),
            userId,
        )
        logRepo.insertLog(
            id = "l1", userId = userId, activityId = "a",
            quantity = 1.0, deviceMode = DeviceMode.OTHER,
            loggedAt = Instant.fromEpochMilliseconds(900_000),
        )
        val vm = WantFormViewModel.forTest(FormMode.Edit("a"), wantRepo, logRepo, { userId }, fixedClock)
        vm.state.first { it.hasPastLogs }
        vm.onCostInput("2.0")
        val s = vm.state.first { it.showCostEditWarning }
        assertTrue(s.showCostEditWarning)
    }

    @Test
    fun `cost change doesn't warn when no past logs`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        wantRepo.saveWantActivity(
            WantActivity(id = "a", name = "TikTok", unit = "minutes", costPerUnit = 1.0),
            userId,
        )
        val vm = WantFormViewModel.forTest(FormMode.Edit("a"), wantRepo, logRepo, { userId }, fixedClock)
        vm.state.first { !it.hasPastLogs && it.name.isNotEmpty() }
        vm.onCostInput("2.0")
        assertFalse(vm.state.first().showCostEditWarning)
    }

    @Test
    fun `validation rejects empty name and negative cost`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        val vm = WantFormViewModel.forTest(FormMode.New, wantRepo, logRepo, { userId }, fixedClock)
        vm.onName("")
        vm.onCostInput("1.0")
        var done = false
        vm.save { done = true }
        assertFalse(done)
        assertNotNull(vm.state.first().validationError)

        vm.onName("X")
        vm.onCostInput("-1")
        vm.save { done = true }
        assertFalse(done)
    }

    @Test
    fun `delete softHides the activity`() = runTest {
        val (wantRepo, logRepo) = newRepos()
        wantRepo.saveWantActivity(
            WantActivity(id = "a", name = "TikTok", unit = "minutes",
                         costPerUnit = 1.0, isCustom = true),
            userId,
        )
        val vm = WantFormViewModel.forTest(FormMode.Edit("a"), wantRepo, logRepo, { userId }, fixedClock)
        vm.state.first { it.name == "TikTok" }
        var done = false
        vm.delete { done = true }
        assertTrue(done)
        val all = wantRepo.getAllWantActivitiesForUser(userId).single()
        assertNotNull(all.hiddenAt)
    }
}
```

- [ ] **Step 3: Run VM tests**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*WantFormViewModelTest*" 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL, 6 tests passing.

- [ ] **Step 4: Implement WantFormScreen**

`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormScreen.kt`:
```kotlin
package com.jktdeveloper.habitto.ui.want

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.components.WantIconPicker
import com.jktdeveloper.habitto.ui.components.wantIconForKey

private val UNITS = listOf(
    "minutes", "servings", "match", "matches", "episode", "session",
    "item", "drinks", "cups", "pieces", "meals",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WantFormScreen(
    viewModel: WantFormViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var iconPickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.saved) { if (state.saved) onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.mode is FormMode.Edit) "Edit want" else "New want",
                         fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save({}) }, enabled = !state.isSaving) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = { iconPickerOpen = true }) {
                        Icon(wantIconForKey(state.iconKey), contentDescription = "Pick icon")
                    }
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onName,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Unit", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UNITS.forEach { unit ->
                    FilterChip(
                        selected = unit == state.unit,
                        onClick = { viewModel.onUnit(unit) },
                        label = { Text(unit) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Cost", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CostStepperRow(
                value = state.costInput,
                onChange = viewModel::onCostInput,
                unit = state.unit,
            )

            if (state.showCostEditWarning) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Editing this cost rewrites your spend history.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            state.validationError?.let { err ->
                Spacer(Modifier.height(12.dp))
                Text(err, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            val mode = state.mode
            if (mode is FormMode.Edit) {
                TextButton(onClick = { viewModel.delete({}) }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null,
                         tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete want", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (iconPickerOpen) {
        WantIconPicker(
            selected = state.iconKey,
            onPick = viewModel::onIconKey,
            onDismiss = { iconPickerOpen = false },
        )
    }
}

@Composable
private fun CostStepperRow(value: String, onChange: (String) -> Unit, unit: String) {
    val parsed = value.toDoubleOrNull() ?: 0.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            label = { Text("Cost (pt / $unit)") },
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { onChange(((parsed - 0.1).coerceAtLeast(0.0)).toRoundedString()) }) {
            Icon(Icons.Default.Remove, contentDescription = "Decrement")
        }
        IconButton(onClick = { onChange(((parsed + 0.1)).toRoundedString()) }) {
            Icon(Icons.Default.Add, contentDescription = "Increment")
        }
    }
    val previewPts = if (parsed > 0.0) {
        kotlin.math.ceil(parsed * 30).toInt().coerceAtLeast(1)
    } else 0
    Text(
        "(e.g. 30 $unit = $previewPts pt)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun Double.toRoundedString(): String {
    val rounded = ((this * 10).toInt()) / 10.0
    return rounded.toString()
}
```

- [ ] **Step 5: Build**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModel.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantFormScreen.kt \
    mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/want/WantFormViewModelTest.kt
rtk git commit -m "feat(want): WantForm — add/edit/delete + cost-edit warning + icon picker"
```

---

## Task 10: Wire navigation + entry points + ExchangeRate filter

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModel.kt`

- [ ] **Step 1: Add nav routes**

In `AppNavigation.kt`, inside `sealed class Screen(...)`, add three new routes alongside existing ones:
```kotlin
object WantList : Screen("want_list")
object WantDetail : Screen("want_detail/{wantId}") {
    const val ARG_ID = "wantId"
    fun route(id: String) = "want_detail/$id"
}
object WantForm : Screen("want_form?wantId={wantId}") {
    const val ARG_ID = "wantId"
    fun route(id: String? = null) = if (id == null) "want_form" else "want_form?wantId=$id"
}
```

Inside `NavHost { ... }` after the existing exchange-rate composable, add:
```kotlin
composable(Screen.WantList.route) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel {
        com.jktdeveloper.habitto.ui.want.WantListViewModel(container)
    }
    com.jktdeveloper.habitto.ui.want.WantListScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        onAddWant = { navController.navigate(Screen.WantForm.route()) },
        onEditWant = { id -> navController.navigate(Screen.WantForm.route(id)) },
        onOpenDetail = { id -> navController.navigate(Screen.WantDetail.route(id)) },
    )
}

composable(
    Screen.WantDetail.route,
    arguments = listOf(navArgument(Screen.WantDetail.ARG_ID) { type = NavType.StringType }),
) { backStack ->
    val id = backStack.arguments?.getString(Screen.WantDetail.ARG_ID).orEmpty()
    val vm = androidx.lifecycle.viewmodel.compose.viewModel {
        com.jktdeveloper.habitto.ui.want.WantDetailViewModel(id, container)
    }
    com.jktdeveloper.habitto.ui.want.WantDetailScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        onEdit = { navController.navigate(Screen.WantForm.route(id)) },
    )
}

composable(
    Screen.WantForm.route,
    arguments = listOf(navArgument(Screen.WantForm.ARG_ID) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }),
) { backStack ->
    val id = backStack.arguments?.getString(Screen.WantForm.ARG_ID)
    val mode = if (id == null) com.jktdeveloper.habitto.ui.want.FormMode.New
               else com.jktdeveloper.habitto.ui.want.FormMode.Edit(id)
    val vm = androidx.lifecycle.viewmodel.compose.viewModel(key = "want_form_$id") {
        com.jktdeveloper.habitto.ui.want.WantFormViewModel(mode, container)
    }
    com.jktdeveloper.habitto.ui.want.WantFormScreen(
        viewModel = vm,
        onClose = { navController.popBackStack() },
    )
}
```

Add the imports if missing:
```kotlin
import androidx.navigation.NavType
import androidx.navigation.navArgument
```

- [ ] **Step 2: YouHub "Wants" entry**

In `YouHubScreen.kt`, near the existing "Earn & spend" / "Exchange rate" row, insert:
```kotlin
ListItem(
    modifier = Modifier.fillMaxWidth().clickable { onOpenWants() },
    leadingContent = { Icon(Icons.Default.ShoppingBag, contentDescription = null) },
    headlineContent = { Text("Wants") },
    supportingContent = { Text("Manage what you spend points on") },
    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
)
```

Add `onOpenWants: () -> Unit` to `YouHubScreen`'s parameter list. Add the imports `androidx.compose.material.icons.filled.ShoppingBag` and `androidx.compose.material.icons.filled.ChevronRight` if not already present.

In `AppNavigation.kt`'s YouHub composable mount, pass:
```kotlin
onOpenWants = { navController.navigate(Screen.WantList.route) }
```

- [ ] **Step 3: Today long-press**

In `HomeScreen.kt`:
1. Add public param `onOpenWantDetail: (String) -> Unit` to `HomeScreen`'s signature.
2. Find `WantActivityCard(...)` (around line 284). Pass new param `onLongPress = { onOpenWantDetail(activity.id) }`.
3. In `WantActivityCard` definition, add `onLongPress: () -> Unit` param.
4. Replace the row's existing `Modifier.clickable { ... onTap() ... }` with:
```kotlin
.combinedClickable(
    onClick = { onTap() },
    onLongClick = { onLongPress() },
)
```
5. Add imports + opt-in:
```kotlin
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
```
And `@OptIn(ExperimentalFoundationApi::class)` on `WantActivityCard`.

In `AppNavigation.kt`'s Home composable mount, add:
```kotlin
onOpenWantDetail = { id -> navController.navigate(Screen.WantDetail.route(id)) }
```

- [ ] **Step 4: Rate-ladder migration banner state**

In `HomeViewModel.kt`, add:
```kotlin
val showRateLadderBanner: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
    container.appFlagsPreferences.seenRateLadderUpgradeBanner,
    container.wantLogRepository.observeAllActiveLogsForUser(container.currentUserId()),
) { seen, logs -> !seen && logs.isNotEmpty() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

fun markRateLadderBannerSeen() {
    viewModelScope.launch {
        container.appFlagsPreferences.setSeenRateLadderUpgradeBanner(true)
    }
}
```

In `HomeScreen.kt`, near the top of the screen body (above the want section), render:
```kotlin
val showBanner by viewModel.showRateLadderBanner.collectAsState()
if (showBanner) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().clickable {
            viewModel.markRateLadderBannerSeen()
            onOpenExchangeRate()
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Spend rates updated — see Exchange rate.",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            IconButton(onClick = viewModel::markRateLadderBannerSeen) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}
```

Add the imports as needed.

- [ ] **Step 5: Filter hidden wants in ExchangeRate comparison rows**

In `ExchangeRateViewModel.kt`, find where `wantActivities` is sourced. If it currently uses `getAllWantActivitiesForUser` or similar, switch to `getWantActivities(userId)` (which is now visibility-filtered by SQL Task 1). If it already uses `getWantActivities`, no change needed.

- [ ] **Step 6: Build + run android tests**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug :mobile:androidApp:testDebugUnitTest 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModel.kt
rtk git commit -m "feat(nav): WantList/Detail/Form routes + YouHub entry + Today long-press + rate banner"
```

---

## Task 11: Final smoke + push PR

- [ ] **Step 1: Run full shared test suite**

Run: `rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run full android tests**

Run: `rtk ./gradlew :mobile:androidApp:testDebugUnitTest 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Build debug + verify release compiles**

Run: `rtk ./gradlew :mobile:androidApp:assembleDebug :mobile:androidApp:assembleRelease 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 4: Manual smoke**

Install debug APK on emulator/device. Verify:

- [ ] Fresh install + onboarding → 14 seeded wants on Today.
- [ ] YouHub → Wants → list shows seeded section, alphabetical.
- [ ] Tap row → WantDetail opens with hero + last 7d timeline.
- [ ] WantDetail → Start timer → toast "Timer coming soon."
- [ ] WantDetail → Edit → form opens populated.
- [ ] Edit cost on a want with past logs → warning banner shows.
- [ ] Save → balance reflects new cost on next Today tap.
- [ ] WantList FAB → form opens in new mode → save → appears in Custom section.
- [ ] WantList trailing visibility_off → confirm + hide → row disappears.
- [ ] Overflow menu → Show hidden → third section shows hidden seeded.
- [ ] Tap visibility icon to restore → row returns to Seeded.
- [ ] Custom WantDetail → Delete → confirm → gone from list.
- [ ] ExchangeRate screen → comparison rows show only visible wants.
- [ ] Today: long-press a want row → opens WantDetail.
- [ ] Today: rate-ladder migration banner shows once for users with want logs; tap → ExchangeRate; banner dismissed; doesn't reappear.
- [ ] Reinstall debug APK + re-onboard → reconcile inserts no extra rows (idempotent).

- [ ] **Step 5: Push branch + open PR**

```bash
rtk git push -u origin feature/phase7-want-crud
gh pr create --title "Phase 7: Want CRUD + new rate ladder" --body "$(cat <<'EOF'
## Summary

Adds Want CRUD (list / detail / add / edit / hide / delete), reconciles
seeded wants to a curated 14-item list with explicit per-row icons, and
adopts the new exchange rate ladder (1.0 / 1.2 / 1.4 / 1.6 / 2.0×).

## Highlights

- New WantActivity columns: iconKey, hiddenAt (single-column hide;
  custom rows with hiddenAt = effectively deleted).
- Idempotent additive reconciliation on app start preserves customizations.
- Curated 13-glyph icon picker for both seeded + custom wants.
- WantList sections: Seeded · Custom · Hidden (toggle).
- WantDetail: hero + last 7d timeline + Start timer (stub) + Edit + Hide/Delete.
- WantForm: name + unit chips + cost stepper (0.1 step) + cost-edit retro warning.
- Today: long-press want row → WantDetail; one-shot rate-ladder migration banner.
- ExchangeRateScreen: hidden wants excluded from comparison rows.

## Test plan

- [x] All shared + android unit tests pass
- [x] Debug + release builds succeed
- [ ] Manual smoke (see plan Task 11 step 4)
EOF
)"
```

---

## Self-review

**Spec coverage:**
- ✅ Schema migration (Task 1)
- ✅ Repository hide/unhide + visibility filter (Task 2)
- ✅ 14-item seed + reconciliation (Task 3)
- ✅ Rate ladder update (Task 4)
- ✅ Migration banner pref + render (Task 5 + Task 10 step 4)
- ✅ Icon helper + picker (Task 6)
- ✅ WantList VM + Screen (Task 7)
- ✅ WantDetail VM + Screen (Task 8)
- ✅ WantForm VM + Screen + cost-edit warning (Task 9)
- ✅ Navigation + YouHub entry + Today long-press + ExchangeRate filter (Task 10)
- ✅ Smoke + PR (Task 11)

**Type consistency:**
- `iconKey: String?` consistent across model, SQL, repo, seed, picker.
- `hiddenAt: Instant?` consistent.
- `WantListUi` / `WantDetailUi` / `WantFormUi` field names match Screen↔VM pairs.
- `FormMode.New` / `FormMode.Edit(id)` consistent across VM, screen, nav.
- `Screen.WantList.route` / `Screen.WantDetail.route(id)` / `Screen.WantForm.route(id)` consistent.
- `resolveWantIcon(iconKey, name)` consistent across HomeScreen, WantListScreen, WantDetailScreen.

**Placeholder scan:** none.
