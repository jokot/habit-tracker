# Phase 5e-3 — Habit Form + Delete + Custom Habit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add habit Create / Edit / Delete with free-form (custom) habits, plus matching confirm dialog for identity remove.

**Architecture:** One Compose screen `HabitFormScreen` (mode-aware via nav args) backed by `HabitFormViewModel`. Persistence through new `SaveHabitUseCase` + `DeleteHabitUseCase` + new `IdentityRepository.markHabitIdentityRemoved`. `Habit.templateId` becomes nullable for custom habits. Soft semantics: delete = `Habit.effectiveTo`, unlink = `LocalHabitIdentity.effectiveTo`.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Compose Material 3, kotlinx-datetime, kotlinx-coroutines (StateFlow + runTest), Supabase Postgrest sync.

---

## Spec

`docs/superpowers/specs/2026-05-04-phase5e-3-habit-form-delete-design.md`

## File Structure

| Layer | File | Responsibility |
|---|---|---|
| Domain model | `Habit.kt` | Add nullable `templateId` |
| Local DB | `HabitTrackerDatabase.sq` | Make `LocalHabit.templateId` nullable; add `markHabitIdentityRemoved` UPDATE; add `effectiveTo IS NULL` filter on `observeHabitsForIdentity` |
| Local DB migration | `migrations/4.sqm` | Rebuild `LocalHabit` with nullable `templateId` (SQLite limitation) |
| Local repo | `LocalHabitRepository.kt` | Map nullable templateId column |
| Local repo | `LocalIdentityRepository.kt` | Implement `markHabitIdentityRemoved`; existing `observeHabitsForIdentity` flows through generated query |
| Repo interface | `IdentityRepository.kt` | Add `markHabitIdentityRemoved` to interface |
| Test fake | `FakeIdentityRepository.kt` | Implement `markHabitIdentityRemoved` |
| Sync DTO | `PostgrestSupabaseSyncClient.kt` | `templateId: String?` on push + pull DTOs |
| Cloud schema | `supabase/migrations/20260504000000_habits_template_id_nullable.sql` | `ALTER COLUMN template_id DROP NOT NULL` |
| Use case | `SaveHabitUseCase.kt` | Create or update; identity link diff (add / soft-remove / resume) |
| Use case | `DeleteHabitUseCase.kt` | Soft delete via `markHabitDeleted` + sync trigger callback |
| ViewModel | `HabitFormViewModel.kt` | Form state, validation flags, dispatch save/delete |
| Screen | `HabitFormScreen.kt` | Create / edit UI per canvas `HabitFormMulti` |
| Wiring | `AppContainer.kt` | Construct + expose new use cases |
| Wiring | `AppNavigation.kt` | `Screen.HabitForm` route with optional `habitId` and `identityId` args |
| Entry point | `HabitListScreen.kt` | FAB → `HabitForm` (create) |
| Entry point | `HabitDetailScreen.kt` | Top-bar edit icon → `HabitForm` (edit) |
| Entry point + dialog | `IdentityDetailScreen.kt` | "+ Add habit" dashed row → `HabitForm` (create + identity pre-fill); Remove identity confirm dialog |
| Dialog state | `IdentityDetailViewModel.kt` | `showRemoveDialog: StateFlow<Boolean>` + begin/confirm/dismiss |

---

## Task 1: Make `Habit.templateId` Nullable (Schema + Model + Sync)

Atomic schema-and-model change. Preserves existing data.

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Habit.kt`
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` — change `templateId` column to nullable + adjust any inserts that referenced it
- Create: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/4.sqm`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt` — pass `templateId` (nullable) into the generated `upsertHabit` and read it from rows
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt` — DTO `templateId: String?` (push + pull); pull mapper preserves null
- Create: `supabase/migrations/20260504000000_habits_template_id_nullable.sql`
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/HabitRepositoryNullableTemplateTest.kt`

- [ ] **Step 1: Update `databaseSchemaVersion` to 4**

In `mobile/shared/build.gradle.kts` find the `sqldelight { databases { create("HabitTrackerDatabase") { ... schemaOutputDirectory.set(...) } } }` block and bump `schemaVersion = 4` (or wherever the SQLDelight `databases` block declares schema version). If no explicit version is set, leave alone — SQLDelight infers from migration filenames.

Run: `rtk grep -n "schemaVersion\|databaseSchemaVersion" mobile/shared/build.gradle.kts` to confirm the location. If absent, the migration filename `4.sqm` is sufficient.

- [ ] **Step 2: Write the failing test for nullable templateId roundtrip**

`mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/HabitRepositoryNullableTemplateTest.kt`:

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HabitRepositoryNullableTemplateTest {
    @Test
    fun `custom habit with null templateId roundtrips through fake repo`() = runTest {
        val repo = FakeHabitRepository()
        val custom = Habit(
            id = "h1",
            userId = "u1",
            templateId = null,
            name = "Walk outside",
            unit = "min",
            thresholdPerPoint = 15.0,
            dailyTarget = 2,
            createdAt = Instant.fromEpochSeconds(0),
            updatedAt = Instant.fromEpochSeconds(0),
        )
        repo.saveHabit(custom)
        val out = repo.getHabitsForUser("u1").single()
        assertNull(out.templateId)
        assertEquals("Walk outside", out.name)
    }
}
```

- [ ] **Step 3: Run test (will not compile — `templateId = null` invalid for `String`)**

`rtk ./gradlew :mobile:shared:compileTestKotlinMetadata 2>&1 | tail -20`
Expected: type-mismatch error on `templateId = null`.

- [ ] **Step 4: Update `Habit` model**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Habit.kt`:

```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class Habit(
    val id: String,
    val userId: String,
    /** Null for custom (free-form) habits not derived from a curated template. */
    val templateId: String?,
    val name: String,
    val unit: String,
    val thresholdPerPoint: Double,
    val dailyTarget: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncedAt: Instant? = null,
    val effectiveFrom: Instant? = null,
    val effectiveTo: Instant? = null,
)
```

- [ ] **Step 5: Update SQLDelight schema for `LocalHabit.templateId` nullable**

In `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`, the existing `LocalHabit` table declares `templateId TEXT NOT NULL`. Change to:

```sql
CREATE TABLE IF NOT EXISTS LocalHabit (
    id TEXT PRIMARY KEY NOT NULL,
    userId TEXT NOT NULL,
    templateId TEXT,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    thresholdPerPoint REAL NOT NULL,
    dailyTarget INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    effectiveFrom INTEGER,
    effectiveTo INTEGER
);
```

- [ ] **Step 6: Create migration `4.sqm`**

`mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/4.sqm`:

```sql
-- Habit.templateId becomes nullable (was NOT NULL).
-- SQLite cannot DROP NOT NULL via ALTER; rebuild the table.
CREATE TABLE LocalHabit_new (
    id TEXT PRIMARY KEY NOT NULL,
    userId TEXT NOT NULL,
    templateId TEXT,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    thresholdPerPoint REAL NOT NULL,
    dailyTarget INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    effectiveFrom INTEGER,
    effectiveTo INTEGER
);

INSERT INTO LocalHabit_new
    SELECT id, userId, templateId, name, unit, thresholdPerPoint,
           dailyTarget, createdAt, updatedAt, syncedAt, effectiveFrom, effectiveTo
    FROM LocalHabit;

DROP TABLE LocalHabit;

ALTER TABLE LocalHabit_new RENAME TO LocalHabit;

CREATE INDEX IF NOT EXISTS idx_habit_user ON LocalHabit(userId);
```

Verify the `idx_habit_user` index name matches whatever's already in `HabitTrackerDatabase.sq`. If a different index existed on the original table, recreate that one with the same name.

- [ ] **Step 7: Update `LocalHabitRepository` for nullable templateId**

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt` — wherever rows are mapped to/from `Habit`, change references like `habit.templateId` (passing into upsert) and `row.templateId` (reading from row) to handle the nullable type. The generated SQLDelight `LocalHabit` data class will already expose `templateId: String?` after the schema change. Most of the file should compile unchanged; remove any explicit non-null casts (`!!`) on `templateId`.

Use `rtk grep -n templateId mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt` to find each spot.

- [ ] **Step 8: Update sync DTOs**

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt` has two `Habit*` DTOs (push + pull). For each, change `val templateId: String` to `val templateId: String?`, and confirm the to-domain mapping (around lines 130-180 — search for `templateId =`) preserves null.

- [ ] **Step 9: Run shared tests**

`rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL — `HabitRepositoryNullableTemplateTest` passes plus all existing tests.

- [ ] **Step 10: Build android app to verify call sites compile**

`rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Create Supabase migration**

`supabase/migrations/20260504000000_habits_template_id_nullable.sql`:

```sql
-- Allow custom habits (templateId = null) to land in cloud.
ALTER TABLE habits ALTER COLUMN template_id DROP NOT NULL;
```

- [ ] **Step 12: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Habit.kt \
    mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq \
    mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/4.sqm \
    mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt \
    mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/HabitRepositoryNullableTemplateTest.kt \
    supabase/migrations/20260504000000_habits_template_id_nullable.sql
rtk git commit -m "feat(habit): templateId becomes nullable for custom habits"
```

---

## Task 2: `markHabitIdentityRemoved` Repository Method

Soft-unlink habit from identity. New SQL UPDATE, new method on interface + impl + fake.

**Files:**
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` — add `markHabitIdentityRemoved` query
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt` — interface method
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt` — impl
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt` — fake impl
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/MarkHabitIdentityRemovedTest.kt`

- [ ] **Step 1: Write the failing test**

`mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/MarkHabitIdentityRemovedTest.kt`:

```kotlin
package com.habittracker.data.repository

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MarkHabitIdentityRemovedTest {
    @Test
    fun `mark soft-unlink sets effectiveTo and clears syncedAt`() = runTest {
        val repo = FakeIdentityRepository()
        repo.linkHabitToIdentities("h1", setOf("i1"))
        // Force the link "synced" first so we can verify it gets cleared
        repo.markHabitIdentitySynced("h1", "i1", Instant.fromEpochSeconds(100))

        val cutoff = Instant.fromEpochSeconds(500)
        repo.markHabitIdentityRemoved("h1", "i1", cutoff)

        val rows = repo.getHabitIdentityLinksForUser("anyUser")
            .filter { it.habitId == "h1" && it.identityId == "i1" }
        val row = rows.singleOrNull()
        assertNotNull(row)
        assertEquals(cutoff, row.effectiveTo)
        assertNull(row.syncedAt)
    }
}
```

- [ ] **Step 2: Run test (compile fail — method missing on interface)**

`rtk ./gradlew :mobile:shared:compileTestKotlinMetadata 2>&1 | tail -10`
Expected: unresolved reference `markHabitIdentityRemoved`.

- [ ] **Step 3: Add interface method**

In `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt`, after `getHabitIdentityLinksForUser` (around line 39):

```kotlin
suspend fun markHabitIdentityRemoved(habitId: String, identityId: String, effectiveTo: Instant)
```

- [ ] **Step 4: Add SQLDelight query**

In `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`, after the existing `LocalHabitIdentity` queries (search for `upsertHabitIdentity:` to find the section), add:

```sql
markHabitIdentityRemoved:
UPDATE LocalHabitIdentity
SET effectiveTo = :effectiveTo, syncedAt = NULL
WHERE habitId = :habitId AND identityId = :identityId;
```

- [ ] **Step 5: Implement on `LocalIdentityRepository`**

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt`:

```kotlin
override suspend fun markHabitIdentityRemoved(
    habitId: String,
    identityId: String,
    effectiveTo: Instant,
) {
    db.localHabitIdentityQueries.markHabitIdentityRemoved(
        effectiveTo = effectiveTo.toEpochMilliseconds(),
        habitId = habitId,
        identityId = identityId,
    )
}
```

(Verify the queries-class accessor name matches what's already used in this file. If queries are accessed via `db.queries.markHabitIdentityRemoved(...)` instead, follow that pattern.)

- [ ] **Step 6: Implement on `FakeIdentityRepository`**

`mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt`. Find the existing `linkHabitToIdentities` impl. After it, add:

```kotlin
override suspend fun markHabitIdentityRemoved(
    habitId: String,
    identityId: String,
    effectiveTo: Instant,
) {
    val links = habitIdentityLinks.value.toMutableList()
    val idx = links.indexOfFirst { it.habitId == habitId && it.identityId == identityId }
    if (idx >= 0) {
        links[idx] = links[idx].copy(effectiveTo = effectiveTo, syncedAt = null)
        habitIdentityLinks.value = links
    }
}
```

(If the backing field is named differently — check the file — substitute that name.)

- [ ] **Step 7: Run test**

`rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*MarkHabitIdentityRemoved*" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run full shared test suite to confirm no regressions**

`rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
rtk git add mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq \
    mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt \
    mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/MarkHabitIdentityRemovedTest.kt
rtk git commit -m "feat(repo): markHabitIdentityRemoved soft-unlink via effectiveTo"
```

---

## Task 3: Filter `observeHabitsForIdentity` By Active Link

Currently the query returns rows regardless of `LocalHabitIdentity.effectiveTo`. Add `effectiveTo IS NULL` filter so unlinked habits drop out of the identity's habit list immediately.

**Files:**
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` — add `AND li.effectiveTo IS NULL` to the `observeHabitsForIdentity` query
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt` — same filter on fake's `observeHabitsForIdentity`
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/ObserveHabitsForIdentityFilterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveHabitsForIdentityFilterTest {
    @Test
    fun `unlinked habit (effectiveTo set) is excluded from observeHabitsForIdentity`() = runTest {
        val repo = FakeIdentityRepository()
        val h1 = makeHabit("h1", "u1")
        val h2 = makeHabit("h2", "u1")
        repo.seedHabit(h1)
        repo.seedHabit(h2)
        repo.linkHabitToIdentities("h1", setOf("identityX"))
        repo.linkHabitToIdentities("h2", setOf("identityX"))

        // Unlink h2
        repo.markHabitIdentityRemoved("h2", "identityX", Instant.fromEpochSeconds(500))

        val active = repo.observeHabitsForIdentity("u1", "identityX").first()
        assertEquals(listOf("h1"), active.map { it.id })
    }

    private fun makeHabit(id: String, userId: String) = Habit(
        id = id, userId = userId, templateId = null, name = id, unit = "x",
        thresholdPerPoint = 1.0, dailyTarget = 1,
        createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
    )
}
```

- [ ] **Step 2: Run test — should fail (current fake returns both habits)**

`rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*ObserveHabitsForIdentityFilter*" 2>&1 | tail -15`
Expected: assertion fails — both `h1` and `h2` returned.

- [ ] **Step 3: Update SQLDelight query**

In `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`, find `observeHabitsForIdentity:` (search by name). The current query joins `LocalHabit` and `LocalHabitIdentity`. Add `AND li.effectiveTo IS NULL` to the WHERE clause:

```sql
observeHabitsForIdentity:
SELECT h.*
FROM LocalHabit h
INNER JOIN LocalHabitIdentity li ON li.habitId = h.id
WHERE h.userId = :userId
  AND li.identityId = :identityId
  AND li.effectiveTo IS NULL
ORDER BY h.name COLLATE NOCASE ASC;
```

(Match the existing alias style in the file. If the existing query uses different aliases or column casing, only add the new `AND` condition.)

- [ ] **Step 4: Update `FakeIdentityRepository.observeHabitsForIdentity`**

In `FakeIdentityRepository.kt`, find `override fun observeHabitsForIdentity(...)`. Add link active filter:

```kotlin
override fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>> =
    combine(seedHabits, habitIdentityLinks) { habits, links ->
        val activeHabitIds = links
            .filter { it.identityId == identityId && it.effectiveTo == null }
            .map { it.habitId }
            .toSet()
        habits.filter { it.userId == userId && it.id in activeHabitIds }
    }
```

(Backing-field names may differ — adjust to match what's already in the file. If the original uses `MutableStateFlow<List<HabitIdentityRow>>` etc., just add the `effectiveTo == null` predicate to the existing filter.)

- [ ] **Step 5: Run test**

`rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*ObserveHabitsForIdentityFilter*" 2>&1 | tail -10`
Expected: PASS.

- [ ] **Step 6: Run full shared suite**

`rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq \
    mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/ObserveHabitsForIdentityFilterTest.kt
rtk git commit -m "feat(repo): observeHabitsForIdentity drops soft-unlinked habits"
```

---

## Task 4: `SaveHabitUseCase`

Create or update a habit + diff identity links.

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SaveHabitUseCase.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SaveHabitUseCaseTest.kt`

- [ ] **Step 1: Write the failing tests**

`mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SaveHabitUseCaseTest.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFails

class SaveHabitUseCaseTest {
    private val now = Instant.fromEpochSeconds(1_000)
    private val fixedClock = object : Clock { override fun now(): Instant = now }

    @Test
    fun `create custom habit inserts with null templateId and links identities at now`() = runTest {
        val habits = FakeHabitRepository()
        val identities = FakeIdentityRepository()
        val sut = SaveHabitUseCase(habits, identities, fixedClock)

        val newId = sut.create(
            userId = "u1",
            name = "Walk outside",
            unit = "min",
            threshold = 15.0,
            target = 2,
            identityIds = setOf("identityA", "identityB"),
            templateId = null,
        )

        val saved = habits.getHabitsForUser("u1").single()
        assertEquals(newId, saved.id)
        assertNull(saved.templateId)
        assertEquals("Walk outside", saved.name)
        assertEquals(now, saved.effectiveFrom)
        assertNull(saved.effectiveTo)
        val links = identities.getHabitIdentityLinksForUser("u1")
            .filter { it.habitId == newId }
        assertEquals(setOf("identityA", "identityB"), links.map { it.identityId }.toSet())
        links.forEach {
            assertEquals(now, it.effectiveFrom)
            assertNull(it.effectiveTo)
        }
    }

    @Test
    fun `update mutates fields and leaves links untouched when set unchanged`() = runTest {
        val habits = FakeHabitRepository()
        val identities = FakeIdentityRepository()
        habits.saveHabit(seedHabit("h1", templateId = "tpl"))
        identities.linkHabitToIdentities("h1", setOf("ix"))

        val sut = SaveHabitUseCase(habits, identities, fixedClock)
        sut.update(
            userId = "u1",
            habitId = "h1",
            name = "Renamed",
            unit = "reps",
            threshold = 5.0,
            target = 3,
            newIdentityIds = setOf("ix"),
        )

        val out = habits.getHabitsForUser("u1").single()
        assertEquals("Renamed", out.name)
        assertEquals("reps", out.unit)
        assertEquals(5.0, out.thresholdPerPoint)
        assertEquals(3, out.dailyTarget)
        assertEquals(now, out.updatedAt)
        assertEquals("tpl", out.templateId)
    }

    @Test
    fun `update with link diff adds new and soft-removes old`() = runTest {
        val habits = FakeHabitRepository()
        val identities = FakeIdentityRepository()
        habits.saveHabit(seedHabit("h1"))
        identities.linkHabitToIdentities("h1", setOf("ix"))

        val sut = SaveHabitUseCase(habits, identities, fixedClock)
        sut.update(
            userId = "u1",
            habitId = "h1",
            name = "n",
            unit = "u",
            threshold = 1.0,
            target = 1,
            newIdentityIds = setOf("iy"), // remove ix, add iy
        )

        val links = identities.getHabitIdentityLinksForUser("u1")
            .filter { it.habitId == "h1" }
        val ix = links.single { it.identityId == "ix" }
        val iy = links.single { it.identityId == "iy" }
        assertEquals(now, ix.effectiveTo)
        assertNull(iy.effectiveTo)
        assertEquals(now, iy.effectiveFrom)
    }

    @Test
    fun `update resumes a previously-removed identity by clearing effectiveTo`() = runTest {
        val habits = FakeHabitRepository()
        val identities = FakeIdentityRepository()
        habits.saveHabit(seedHabit("h1"))
        identities.linkHabitToIdentities("h1", setOf("ix"))
        val originalLinkAt = Instant.fromEpochSeconds(100)
        // Forge an earlier effectiveFrom on the existing link via re-link (fake auto-sets)
        // then remove it
        identities.markHabitIdentityRemoved("h1", "ix", Instant.fromEpochSeconds(500))

        val sut = SaveHabitUseCase(habits, identities, fixedClock)
        sut.update(
            userId = "u1",
            habitId = "h1",
            name = "n",
            unit = "u",
            threshold = 1.0,
            target = 1,
            newIdentityIds = setOf("ix"), // re-add ix
        )

        val link = identities.getHabitIdentityLinksForUser("u1")
            .single { it.habitId == "h1" && it.identityId == "ix" }
        assertNull(link.effectiveTo) // resumed
        // effectiveFrom intentionally NOT advanced (per spec — gap glossed over)
    }

    @Test
    fun `create rejects empty name`() = runTest {
        val sut = SaveHabitUseCase(FakeHabitRepository(), FakeIdentityRepository(), fixedClock)
        assertFails {
            sut.create("u1", "  ", "min", 1.0, 1, setOf("ix"), null)
        }
    }

    @Test
    fun `create rejects empty identity set`() = runTest {
        val sut = SaveHabitUseCase(FakeHabitRepository(), FakeIdentityRepository(), fixedClock)
        assertFails {
            sut.create("u1", "Name", "min", 1.0, 1, emptySet(), null)
        }
    }

    @Test
    fun `create rejects threshold <= 0`() = runTest {
        val sut = SaveHabitUseCase(FakeHabitRepository(), FakeIdentityRepository(), fixedClock)
        assertFails {
            sut.create("u1", "Name", "min", 0.0, 1, setOf("ix"), null)
        }
    }

    @Test
    fun `create rejects target lt 1`() = runTest {
        val sut = SaveHabitUseCase(FakeHabitRepository(), FakeIdentityRepository(), fixedClock)
        assertFails {
            sut.create("u1", "Name", "min", 1.0, 0, setOf("ix"), null)
        }
    }

    @Test
    fun `create rejects empty unit`() = runTest {
        val sut = SaveHabitUseCase(FakeHabitRepository(), FakeIdentityRepository(), fixedClock)
        assertFails {
            sut.create("u1", "Name", "  ", 1.0, 1, setOf("ix"), null)
        }
    }

    private fun seedHabit(id: String, templateId: String? = null) = Habit(
        id = id, userId = "u1", templateId = templateId, name = "old",
        unit = "u", thresholdPerPoint = 1.0, dailyTarget = 1,
        createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
    )
}
```

- [ ] **Step 2: Run test (use case missing)**

`rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*SaveHabitUseCase*" 2>&1 | tail -10`
Expected: unresolved reference `SaveHabitUseCase`.

- [ ] **Step 3: Implement `SaveHabitUseCase`**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SaveHabitUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SaveHabitUseCase(
    private val habitRepo: HabitRepository,
    private val identityRepo: IdentityRepository,
    private val clock: Clock = Clock.System,
) {
    /** Create a new habit. Returns the new habit's id. */
    suspend fun create(
        userId: String,
        name: String,
        unit: String,
        threshold: Double,
        target: Int,
        identityIds: Set<String>,
        templateId: String?,
    ): String {
        validate(name = name, unit = unit, threshold = threshold, target = target, identityIds = identityIds)
        val now = clock.now()
        val id = Uuid.random().toString()
        habitRepo.saveHabit(
            Habit(
                id = id,
                userId = userId,
                templateId = templateId,
                name = name.trim(),
                unit = unit.trim(),
                thresholdPerPoint = threshold,
                dailyTarget = target,
                createdAt = now,
                updatedAt = now,
                syncedAt = null,
                effectiveFrom = now,
                effectiveTo = null,
            )
        )
        identityIds.forEach { identityRepo.linkHabitToIdentities(id, setOf(it)) }
        return id
    }

    /** Update an existing habit. Diffs identity links: add new, soft-remove dropped, resume previously-removed. */
    suspend fun update(
        userId: String,
        habitId: String,
        name: String,
        unit: String,
        threshold: Double,
        target: Int,
        newIdentityIds: Set<String>,
    ) {
        validate(name = name, unit = unit, threshold = threshold, target = target, identityIds = newIdentityIds)
        val now = clock.now()
        val existing = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
            ?: error("habit $habitId not found for user $userId")
        habitRepo.saveHabit(
            existing.copy(
                name = name.trim(),
                unit = unit.trim(),
                thresholdPerPoint = threshold,
                dailyTarget = target,
                updatedAt = now,
                syncedAt = null,
            )
        )

        val allLinks = identityRepo.getHabitIdentityLinksForUser(userId).filter { it.habitId == habitId }
        val activeLinkIds = allLinks.filter { it.effectiveTo == null }.map { it.identityId }.toSet()

        val toAdd = newIdentityIds - activeLinkIds
        val toRemove = activeLinkIds - newIdentityIds

        // linkHabitToIdentities is additive + idempotent; for resume cases, the underlying
        // upsert clears effectiveTo by re-inserting the row with effectiveTo = null.
        toAdd.forEach { identityRepo.linkHabitToIdentities(habitId, setOf(it)) }
        toRemove.forEach { identityRepo.markHabitIdentityRemoved(habitId, it, now) }
    }

    private fun validate(
        name: String,
        unit: String,
        threshold: Double,
        target: Int,
        identityIds: Set<String>,
    ) {
        require(name.trim().isNotEmpty()) { "name must not be blank" }
        require(unit.trim().isNotEmpty()) { "unit must not be blank" }
        require(threshold > 0.0) { "threshold must be > 0" }
        require(target >= 1) { "target must be >= 1" }
        require(identityIds.isNotEmpty()) { "at least one identity required" }
    }
}
```

> Note: the resume case relies on `linkHabitToIdentities` performing INSERT OR REPLACE which sets `effectiveTo = null` on the new row. Verify this behavior by inspecting `LocalIdentityRepository.linkHabitToIdentities` (around line 140 of that file) and `FakeIdentityRepository.linkHabitToIdentities`. If either sets `effectiveTo` to anything other than `null` for an existing row, fix that as part of this task.

- [ ] **Step 4: Verify `linkHabitToIdentities` resume behavior in fake**

In `FakeIdentityRepository.linkHabitToIdentities`, ensure the upsert resets `effectiveTo = null` on existing rows. If the existing impl uses `addOrIgnore` semantics (skips existing), change it to upsert that always sets `effectiveTo = null`. Example:

```kotlin
override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {
    val now = clock.now() // if no clock injected, use a sentinel like Instant.fromEpochSeconds(0)
    val current = habitIdentityLinks.value.toMutableList()
    identityIds.forEach { identityId ->
        val idx = current.indexOfFirst { it.habitId == habitId && it.identityId == identityId }
        val row = if (idx >= 0) {
            current[idx].copy(effectiveTo = null, syncedAt = null)
        } else {
            HabitIdentityRow(
                habitId = habitId,
                identityId = identityId,
                addedAt = now,
                syncedAt = null,
                effectiveFrom = now,
                effectiveTo = null,
            )
        }
        if (idx >= 0) current[idx] = row else current.add(row)
    }
    habitIdentityLinks.value = current
}
```

If the file already has a clock or `now` available, reuse it. Otherwise add a `private val nowInstant: () -> Instant = { Instant.fromEpochSeconds(0) }` and call it.

- [ ] **Step 5: Verify `LocalIdentityRepository.linkHabitToIdentities` matches semantics**

Read `LocalIdentityRepository.kt` around line 140. The existing `upsertHabitIdentity` SQL likely uses `INSERT OR REPLACE` which clobbers all columns. Confirm the upsert sets `effectiveTo = NULL` in the SQL — if not, change the SQL `upsertHabitIdentity` block in `HabitTrackerDatabase.sq` to explicitly set `effectiveTo = NULL` on conflict. (If the existing upsert passes a `:effectiveTo` parameter, change call sites to pass null when relinking.)

- [ ] **Step 6: Run test**

`rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*SaveHabitUseCase*" 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run full shared suite**

`rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SaveHabitUseCase.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SaveHabitUseCaseTest.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt
rtk git commit -m "feat(usecase): SaveHabitUseCase — create/update with link diff"
```

---

## Task 5: `DeleteHabitUseCase`

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/DeleteHabitUseCase.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/DeleteHabitUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeleteHabitUseCaseTest {
    @Test
    fun `delete sets effectiveTo to clock now`() = runTest {
        val now = Instant.fromEpochSeconds(2000)
        val clock = object : Clock { override fun now(): Instant = now }
        val habits = FakeHabitRepository()
        habits.saveHabit(
            Habit(
                id = "h1", userId = "u1", templateId = null, name = "n",
                unit = "u", thresholdPerPoint = 1.0, dailyTarget = 1,
                createdAt = Instant.fromEpochSeconds(0),
                updatedAt = Instant.fromEpochSeconds(0),
            )
        )
        val sut = DeleteHabitUseCase(habits, clock)

        sut.execute("u1", "h1")

        val out = habits.getHabitsForUser("u1").single()
        assertEquals(now, out.effectiveTo)
        assertNull(out.syncedAt)
    }
}
```

- [ ] **Step 2: Run test**

`rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*DeleteHabitUseCase*" 2>&1 | tail -10`
Expected: unresolved reference `DeleteHabitUseCase`.

- [ ] **Step 3: Implement use case**

`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/DeleteHabitUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository
import kotlinx.datetime.Clock

class DeleteHabitUseCase(
    private val habitRepo: HabitRepository,
    private val clock: Clock = Clock.System,
) {
    suspend fun execute(userId: String, habitId: String) {
        habitRepo.markHabitDeleted(habitId, userId, clock.now())
    }
}
```

- [ ] **Step 4: Run test**

`rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*DeleteHabitUseCase*" 2>&1 | tail -10`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/DeleteHabitUseCase.kt \
    mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/DeleteHabitUseCaseTest.kt
rtk git commit -m "feat(usecase): DeleteHabitUseCase — soft delete via effectiveTo"
```

---

## Task 6: `HabitFormViewModel`

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitFormViewModel.kt`
- Test: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/habit/HabitFormViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

`mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/habit/HabitFormViewModelTest.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.habit

import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.domain.usecase.SaveHabitUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HabitFormViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `create mode starts with empty fields and identityId pre-fill if provided`() = runTest {
        val (vm, _, _, _) = makeVm(habitId = null, prefillIdentityId = "ix")
        val state = vm.state.first()
        assertEquals("", state.name)
        assertEquals(setOf("ix"), state.selectedIdentityIds)
        assertFalse(state.canSave) // empty name etc.
        assertEquals(HabitFormMode.Create, state.mode)
    }

    @Test
    fun `edit mode loads existing habit fields`() = runTest {
        val habits = StubHabitRepo(
            initial = listOf(
                Habit(
                    id = "h1", userId = "u1", templateId = "tpl", name = "Read",
                    unit = "min", thresholdPerPoint = 10.0, dailyTarget = 2,
                    createdAt = Instant.fromEpochSeconds(0),
                    updatedAt = Instant.fromEpochSeconds(0),
                )
            )
        )
        val identities = StubIdentityRepo(
            links = listOf(HabitIdentityRow("h1", "ix", Instant.fromEpochSeconds(0), null)),
            userIdentities = listOf(Identity("ix", "Reader", "Reader", "📖", null, null, false, null, false)),
        )
        val (vm, _, _, _) = makeVm(habitId = "h1", habits = habits, identities = identities)
        val state = vm.state.first { it.mode == HabitFormMode.Edit }
        assertEquals("Read", state.name)
        assertEquals("min", state.unit)
        assertEquals(10.0, state.threshold)
        assertEquals(2, state.target)
        assertEquals(setOf("ix"), state.selectedIdentityIds)
        assertTrue(state.canSave)
    }

    @Test
    fun `canSave false when name empty`() = runTest {
        val (vm, _, _, _) = makeVm(habitId = null)
        vm.onNameChange("  ")
        vm.onUnitChange("min")
        vm.onThresholdChange(1.0)
        vm.onTargetChange(1)
        vm.onIdentitiesChange(setOf("ix"))
        assertFalse(vm.state.first().canSave)
    }

    @Test
    fun `canSave false when no identities selected`() = runTest {
        val (vm, _, _, _) = makeVm(habitId = null)
        vm.onNameChange("Walk")
        vm.onUnitChange("min")
        vm.onThresholdChange(1.0)
        vm.onTargetChange(1)
        assertFalse(vm.state.first().canSave)
    }

    @Test
    fun `save in create mode dispatches create with provided fields`() = runTest {
        val (vm, _, _, recorder) = makeVm(habitId = null)
        vm.onNameChange("Walk")
        vm.onUnitChange("min")
        vm.onThresholdChange(15.0)
        vm.onTargetChange(2)
        vm.onIdentitiesChange(setOf("ix"))
        vm.save()
        assertEquals("create", recorder.last)
    }

    @Test
    fun `delete dispatches DeleteHabitUseCase`() = runTest {
        val habits = StubHabitRepo(
            initial = listOf(
                Habit(
                    id = "h1", userId = "u1", templateId = null, name = "n",
                    unit = "u", thresholdPerPoint = 1.0, dailyTarget = 1,
                    createdAt = Instant.fromEpochSeconds(0),
                    updatedAt = Instant.fromEpochSeconds(0),
                )
            )
        )
        val (vm, _, _, recorder) = makeVm(habitId = "h1", habits = habits)
        vm.delete()
        assertEquals("delete", recorder.last)
    }

    private data class Bundle(
        val vm: HabitFormViewModel,
        val habits: StubHabitRepo,
        val identities: StubIdentityRepo,
        val recorder: Recorder,
    )

    private fun makeVm(
        habitId: String?,
        prefillIdentityId: String? = null,
        habits: StubHabitRepo = StubHabitRepo(),
        identities: StubIdentityRepo = StubIdentityRepo(),
    ): Bundle {
        val recorder = Recorder()
        val save = object : SaveHabitUseCase(habits, identities, fixedClock) {
            override suspend fun create(
                userId: String, name: String, unit: String, threshold: Double,
                target: Int, identityIds: Set<String>, templateId: String?,
            ): String { recorder.last = "create"; return "newId" }
            override suspend fun update(
                userId: String, habitId: String, name: String, unit: String,
                threshold: Double, target: Int, newIdentityIds: Set<String>,
            ) { recorder.last = "update" }
        }
        val delete = object : DeleteHabitUseCase(habits, fixedClock) {
            override suspend fun execute(userId: String, habitId: String) { recorder.last = "delete" }
        }
        val vm = HabitFormViewModel(
            habitId = habitId,
            prefillIdentityId = prefillIdentityId,
            userIdProvider = { "u1" },
            saveUseCase = save,
            deleteUseCase = delete,
            habitRepo = habits,
            identityRepo = identities,
            triggerSync = {},
        )
        return Bundle(vm, habits, identities, recorder)
    }

    private val fixedClock = object : Clock { override fun now(): Instant = Instant.fromEpochSeconds(1000) }

    private class Recorder { var last: String? = null }
}

// Minimal stubs (file-local). Real repos exist in commonTest but aren't on the
// androidApp test classpath; inline stubs keep the test self-contained.
private class StubHabitRepo(initial: List<Habit> = emptyList()) : HabitRepository {
    private val data = initial.toMutableList()
    override suspend fun getHabitsForUser(userId: String) = data.filter { it.userId == userId }
    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> = flowOf(data.filter { it.userId == userId })
    override suspend fun saveHabit(habit: Habit) { data.removeAll { it.id == habit.id }; data.add(habit) }
    override suspend fun deleteHabit(habitId: String, userId: String) { data.removeAll { it.id == habitId } }
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {}
    override suspend fun clearForUser(userId: String) { data.removeAll { it.userId == userId } }
    override suspend fun getUnsyncedFor(userId: String) = emptyList<Habit>()
    override suspend fun markSynced(id: String, syncedAt: Instant) {}
    override suspend fun getByIdsForUser(userId: String, ids: List<String>) = data.filter { it.id in ids }
    override suspend fun mergePulled(row: Habit) {}
    override suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant) {}
}
private class StubIdentityRepo(
    val links: List<HabitIdentityRow> = emptyList(),
    val userIdentities: List<Identity> = emptyList(),
) : IdentityRepository {
    override suspend fun getAllIdentities() = emptyList<Identity>()
    override suspend fun upsertIdentities(identities: List<Identity>) {}
    override fun observeUserIdentities(userId: String) = flowOf(userIdentities)
    override suspend fun setUserIdentities(userId: String, identityIds: Set<String>) {}
    override suspend fun clearUserIdentitiesForUser(userId: String) {}
    override suspend fun getUnsyncedUserIdentitiesFor(userId: String) = emptyList<UserIdentityRow>()
    override suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant) {}
    override suspend fun mergePulledUserIdentity(row: UserIdentityRow) {}
    override suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean) {}
    override suspend fun clearPinForUser(userId: String) {}
    override suspend fun updateWhyText(userId: String, identityId: String, whyText: String?) {}
    override suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant) {}
    override suspend fun setPinAtomically(userId: String, identityId: String) {}
    override suspend fun getPinnedIdentityIdForUser(userId: String) = null
    override suspend fun getUserIdentityRow(userId: String, identityId: String) = null
    override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {}
    override suspend fun clearHabitIdentitiesForUser(userId: String) {}
    override suspend fun getUnsyncedHabitIdentitiesFor(userId: String) = emptyList<HabitIdentityRow>()
    override suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant) {}
    override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) {}
    override fun observeHabitsForIdentity(userId: String, identityId: String) = flowOf(emptyList<Habit>())
    override suspend fun getHabitIdentityLinksForUser(userId: String) = links
    override suspend fun markHabitIdentityRemoved(habitId: String, identityId: String, effectiveTo: Instant) {}
}
```

> Adjust the `Identity(...)` constructor at the top of the file to match the real `Identity` data class signature — copy the constructor call from any existing test that constructs `Identity` (e.g. `ComputeIdentityStatsUseCaseTest`).

- [ ] **Step 2: Run test**

`rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*HabitFormViewModel*" 2>&1 | tail -10`
Expected: unresolved reference `HabitFormViewModel`, `HabitFormMode`.

- [ ] **Step 3: Implement `HabitFormViewModel`**

`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitFormViewModel.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.domain.usecase.SaveHabitUseCase
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HabitFormMode { Create, Edit }

data class HabitFormState(
    val mode: HabitFormMode = HabitFormMode.Create,
    val name: String = "",
    val unit: String = "",
    val threshold: Double = 1.0,
    val target: Int = 1,
    val selectedIdentityIds: Set<String> = emptySet(),
    val availableIdentities: List<Identity> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = name.trim().isNotEmpty()
            && unit.trim().isNotEmpty()
            && threshold > 0.0
            && target >= 1
            && selectedIdentityIds.isNotEmpty()
            && !isSaving
}

class HabitFormViewModel(
    private val habitId: String?,
    private val prefillIdentityId: String?,
    private val userIdProvider: () -> String,
    private val saveUseCase: SaveHabitUseCase,
    private val deleteUseCase: DeleteHabitUseCase,
    private val habitRepo: HabitRepository,
    private val identityRepo: IdentityRepository,
    private val triggerSync: () -> Unit,
) : ViewModel() {

    constructor(container: AppContainer, habitId: String?, prefillIdentityId: String?) : this(
        habitId = habitId,
        prefillIdentityId = prefillIdentityId,
        userIdProvider = { container.currentUserId() },
        saveUseCase = container.saveHabitUseCase,
        deleteUseCase = container.deleteHabitUseCase,
        habitRepo = container.habitRepository,
        identityRepo = container.identityRepository,
        triggerSync = { com.jktdeveloper.habitto.sync.SyncTriggers.enqueue(container.appContext, com.habittracker.data.sync.SyncReason.POST_LOG) },
    )

    private val _state = MutableStateFlow(HabitFormState())
    val state: StateFlow<HabitFormState> = _state.asStateFlow()

    private val _saveSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveSuccess: SharedFlow<Unit> = _saveSuccess.asSharedFlow()

    private val _deleteSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleteSuccess: SharedFlow<Unit> = _deleteSuccess.asSharedFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val userId = userIdProvider()
        val identities = identityRepo.observeUserIdentities(userId).first()
        if (habitId == null) {
            _state.update {
                HabitFormState(
                    mode = HabitFormMode.Create,
                    selectedIdentityIds = prefillIdentityId?.let { setOf(it) } ?: emptySet(),
                    availableIdentities = identities,
                    isLoading = false,
                )
            }
            return
        }
        val habit = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
        if (habit == null) {
            _state.update { it.copy(isLoading = false, error = "Habit not found") }
            return
        }
        val activeLinks = identityRepo.getHabitIdentityLinksForUser(userId)
            .filter { it.habitId == habitId && it.effectiveTo == null }
            .map { it.identityId }
            .toSet()
        _state.update {
            HabitFormState(
                mode = HabitFormMode.Edit,
                name = habit.name,
                unit = habit.unit,
                threshold = habit.thresholdPerPoint,
                target = habit.dailyTarget,
                selectedIdentityIds = activeLinks,
                availableIdentities = identities,
                isLoading = false,
            )
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(name = v) }
    fun onUnitChange(v: String) = _state.update { it.copy(unit = v) }
    fun onThresholdChange(v: Double) = _state.update { it.copy(threshold = v) }
    fun onTargetChange(v: Int) = _state.update { it.copy(target = v) }
    fun onIdentitiesChange(ids: Set<String>) = _state.update { it.copy(selectedIdentityIds = ids) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val userId = userIdProvider()
            runCatching {
                if (s.mode == HabitFormMode.Create) {
                    saveUseCase.create(
                        userId = userId,
                        name = s.name,
                        unit = s.unit,
                        threshold = s.threshold,
                        target = s.target,
                        identityIds = s.selectedIdentityIds,
                        templateId = null,
                    )
                } else {
                    saveUseCase.update(
                        userId = userId,
                        habitId = habitId!!,
                        name = s.name,
                        unit = s.unit,
                        threshold = s.threshold,
                        target = s.target,
                        newIdentityIds = s.selectedIdentityIds,
                    )
                }
            }.onSuccess {
                triggerSync()
                _saveSuccess.tryEmit(Unit)
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.message ?: "Save failed") }
            }
        }
    }

    fun delete() {
        if (habitId == null) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val userId = userIdProvider()
            runCatching { deleteUseCase.execute(userId, habitId) }
                .onSuccess {
                    triggerSync()
                    _deleteSuccess.tryEmit(Unit)
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.message ?: "Delete failed") }
                }
        }
    }
}
```

- [ ] **Step 4: Run test**

`rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*HabitFormViewModel*" 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitFormViewModel.kt \
    mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/habit/HabitFormViewModelTest.kt
rtk git commit -m "feat(ui): HabitFormViewModel — create/edit/delete state + dispatch"
```

---

## Task 7: `HabitFormScreen`

UI per canvas `HabitFormMulti` (screens.jsx:2633). No tests for Compose UI in this codebase (manual smoke). Wire Save/Delete to VM.

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitFormScreen.kt`

- [ ] **Step 1: Implement screen**

`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitFormScreen.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.habit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.Identity
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.habitIcon
import com.jktdeveloper.habitto.ui.components.identityIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitFormScreen(
    viewModel: HabitFormViewModel,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.saveSuccess.collect { onSaved() }
    }
    LaunchedEffect(viewModel) {
        viewModel.deleteSuccess.collect { onDeleted() }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete habit?") },
            text = { Text("Past activity stays in your history. Future days will exclude it.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.mode == HabitFormMode.Edit) "Edit habit" else "New habit",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = state.canSave,
                    ) {
                        Text(
                            "Save",
                            color = if (state.canSave) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
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
            item { NameRow(state, viewModel::onNameChange) }
            item { IdentitiesSection(state, viewModel::onIdentitiesChange) }
            item { GoalSection(state, viewModel::onThresholdChange, viewModel::onTargetChange, viewModel::onUnitChange) }
            if (state.mode == HabitFormMode.Edit) {
                item { DeleteRow(onClick = { showDeleteDialog = true }) }
            }
            state.error?.let { item { ErrorBanner(it) } }
        }
    }
}

@Composable
private fun NameRow(state: HabitFormState, onNameChange: (String) -> Unit) {
    val firstHueId = state.selectedIdentityIds.firstOrNull()?.let { id ->
        state.availableIdentities.firstOrNull { it.id == id }?.name?.lowercase()
    }
    val hue = if (firstHueId != null) IdentityHue.forIdentityId(firstHueId) else 0f
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HabitGlyph(icon = habitIcon(state.name.ifBlank { "default" }), hue = hue, size = 56.dp)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                placeholder = { Text("e.g. Walk outside") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun IdentitiesSection(state: HabitFormState, onChange: (Set<String>) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("Identities", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "at least 1 required",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Who you become by doing this. Affects all selected identities' streaks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        val selected = state.availableIdentities.filter { it.id in state.selectedIdentityIds }
        val unselected = state.availableIdentities.filter { it.id !in state.selectedIdentityIds }

        // Selected chips
        FlowChips {
            selected.forEach { id ->
                SelectedIdentityPill(id, onRemove = { onChange(state.selectedIdentityIds - id.id) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (unselected.isNotEmpty()) {
            Text(
                "Suggested",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
            )
            FlowChips {
                unselected.forEach { id ->
                    SuggestionPill(id, onAdd = { onChange(state.selectedIdentityIds + id.id) })
                }
            }
        }
    }
}

@Composable
private fun FlowChips(content: @Composable () -> Unit) {
    // Simple wrap row — Material 3 has FlowRow but kept minimal here.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SelectedIdentityPill(identity: Identity, onRemove: () -> Unit) {
    val hue = IdentityHue.forIdentityId(identity.name.lowercase())
    val bg = Color.hsl(hue, 0.50f, 0.94f)
    val fg = Color.hsl(hue, 0.50f, 0.30f)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HabitGlyph(icon = identityIcon(identity.name), hue = hue, size = 24.dp)
            Text(identity.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = fg)
            }
        }
    }
}

@Composable
private fun SuggestionPill(identity: Identity, onAdd: () -> Unit) {
    val hue = IdentityHue.forIdentityId(identity.name.lowercase())
    Surface(
        onClick = onAdd,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HabitGlyph(icon = identityIcon(identity.name), hue = hue, size = 20.dp)
            Text("+ ${identity.name}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GoalSection(
    state: HabitFormState,
    onThreshold: (Double) -> Unit,
    onTarget: (Int) -> Unit,
    onUnit: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text("Goal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = if (state.threshold == state.threshold.toLong().toDouble())
                    state.threshold.toLong().toString() else state.threshold.toString(),
                onValueChange = { v -> v.toDoubleOrNull()?.let(onThreshold) },
                label = { Text("1 point per") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.target.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let(onTarget) },
                label = { Text("Daily target") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.unit,
            onValueChange = onUnit,
            label = { Text("Unit (e.g. min, reps, pages)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeleteRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Delete habit")
        }
    }
}

@Composable
private fun ErrorBanner(msg: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            msg,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}
```

- [ ] **Step 2: Build android app**

`rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL. (Container wiring + nav follow in next tasks; this just verifies the screen compiles.)

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitFormScreen.kt
rtk git commit -m "feat(ui): HabitFormScreen — name, identities, goal, delete dialog"
```

---

## Task 8: Wire `AppContainer` + `AppNavigation`

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` — expose `saveHabitUseCase` + `deleteHabitUseCase`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` — add `Screen.HabitForm` + composable mount

- [ ] **Step 1: Add use cases to `AppContainer`**

In `AppContainer.kt`, find where existing use cases are constructed (look for `setupUserHabitsUseCase = ...`). Add:

```kotlin
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.domain.usecase.SaveHabitUseCase

val saveHabitUseCase = SaveHabitUseCase(habitRepository, identityRepository)
val deleteHabitUseCase = DeleteHabitUseCase(habitRepository)
```

- [ ] **Step 2: Add nav route**

In `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`, after the `HabitDetail` Screen object (around the existing `Screen.HabitDetail`):

```kotlin
object HabitForm : Screen("habit_form?habitId={habitId}&identityId={identityId}") {
    const val ARG_HABIT_ID = "habitId"
    const val ARG_IDENTITY_ID = "identityId"
    fun route(habitId: String? = null, identityId: String? = null): String {
        val params = buildList {
            habitId?.let { add("habitId=$it") }
            identityId?.let { add("identityId=$it") }
        }.joinToString("&")
        return if (params.isEmpty()) "habit_form" else "habit_form?$params"
    }
}
```

(Match the existing `Screen` sealed-class pattern — sibling objects already follow this style.)

- [ ] **Step 3: Mount the composable**

In `AppNavigation.kt`'s `NavHost { ... }`, after the `HabitDetail` `composable(...)` block, add:

```kotlin
composable(
    route = Screen.HabitForm.route,
    arguments = listOf(
        androidx.navigation.navArgument(Screen.HabitForm.ARG_HABIT_ID) {
            type = androidx.navigation.NavType.StringType
            nullable = true
            defaultValue = null
        },
        androidx.navigation.navArgument(Screen.HabitForm.ARG_IDENTITY_ID) {
            type = androidx.navigation.NavType.StringType
            nullable = true
            defaultValue = null
        },
    ),
) { entry ->
    val habitId = entry.arguments?.getString(Screen.HabitForm.ARG_HABIT_ID)
    val identityId = entry.arguments?.getString(Screen.HabitForm.ARG_IDENTITY_ID)
    val vm = viewModel { HabitFormViewModel(container, habitId = habitId, prefillIdentityId = identityId) }
    com.jktdeveloper.habitto.ui.habit.HabitFormScreen(
        viewModel = vm,
        onClose = { navController.popBackStack() },
        onSaved = { navController.popBackStack() },
        onDeleted = { navController.popBackStack() },
    )
}
```

- [ ] **Step 4: Build android app**

`rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "feat(nav): HabitForm route + AppContainer wires save/delete use cases"
```

---

## Task 9: Wire `HabitListScreen` FAB

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListScreen.kt` — add FAB and `onAddHabit` callback
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` — pass nav callback

- [ ] **Step 1: Add `onAddHabit` parameter + FAB to `HabitListScreen`**

Find the `Scaffold` in `HabitListScreen.kt`. Add `floatingActionButton` slot:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onBack: () -> Unit,
    onHabitClick: (String) -> Unit,
    onAddIdentityClick: () -> Unit = {},
    onAddHabit: () -> Unit = {},
) {
    // ... existing state + Scaffold(...) — add to the Scaffold call:
    Scaffold(
        topBar = { /* existing */ },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onAddHabit,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New habit") },
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding -> /* existing body */ }
}
```

- [ ] **Step 2: Wire callback in `AppNavigation`**

In the `composable(Screen.HabitList.route)` block:

```kotlin
HabitListScreen(
    viewModel = vm,
    onBack = { navController.popBackStack() },
    onHabitClick = { id -> navController.navigate(Screen.HabitDetail.route(id)) },
    onAddIdentityClick = { navController.navigate(Screen.AddIdentity.route) },
    onAddHabit = { navController.navigate(Screen.HabitForm.route()) },
)
```

- [ ] **Step 3: Build**

`rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListScreen.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "feat(habit): HabitList FAB navigates to HabitForm (create)"
```

---

## Task 10: Wire `HabitDetailScreen` Edit Icon

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailScreen.kt` — add `onEdit` callback + edit icon in TopAppBar
- Modify: `AppNavigation.kt` — pass `onEdit` nav

- [ ] **Step 1: Add `onEdit` parameter + edit icon**

In `HabitDetailScreen.kt`, the existing TopAppBar has only a back button. Add `onEdit`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    viewModel: HabitDetailViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit = {},
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
                actions = {
                    val s = state
                    if (s is HabitDetailState.Loaded) {
                        IconButton(onClick = { onEdit(s.habit.id) }) {
                            Icon(
                                androidx.compose.material.icons.Icons.Outlined.Edit,
                                contentDescription = "Edit habit",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding -> /* existing body */ }
}
```

(Add `import androidx.compose.material.icons.outlined.Edit` if not already present.)

- [ ] **Step 2: Wire in nav**

In `AppNavigation.kt`'s `composable(Screen.HabitDetail.route)` block:

```kotlin
HabitDetailScreen(
    viewModel = vm,
    onBack = { navController.popBackStack() },
    onEdit = { id -> navController.navigate(Screen.HabitForm.route(habitId = id)) },
)
```

- [ ] **Step 3: Build**

`rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailScreen.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "feat(habit): HabitDetail edit icon → HabitForm (edit mode)"
```

---

## Task 11: `IdentityDetailScreen` "+ Add habit" Row + Remove Identity Confirm Dialog

Two changes in one file family. Test the dialog flow.

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`
- Test: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModelDialogTest.kt`

- [ ] **Step 1: Write failing dialog flow test**

```kotlin
package com.jktdeveloper.habitto.ui.identity

import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.IdentityStats
import com.habittracker.domain.usecase.ComputeIdentityStatsUseCase
import com.habittracker.domain.usecase.PinIdentityUseCase
import com.habittracker.domain.usecase.RemoveIdentityUseCase
import com.habittracker.domain.usecase.UnpinIdentityUseCase
import com.habittracker.domain.usecase.UpdateIdentityWhyUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IdentityDetailViewModelDialogTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `beginRemove shows dialog without firing remove`() = runTest {
        val (vm, removeRecorder) = makeVm()
        vm.beginRemove()
        assertTrue(vm.showRemoveDialog.first())
        assertFalse(removeRecorder.called)
    }

    @Test
    fun `dismissRemoveDialog hides without firing remove`() = runTest {
        val (vm, removeRecorder) = makeVm()
        vm.beginRemove()
        vm.dismissRemoveDialog()
        assertFalse(vm.showRemoveDialog.first())
        assertFalse(removeRecorder.called)
    }

    @Test
    fun `confirmRemove fires use case and emits success`() = runTest {
        val (vm, removeRecorder) = makeVm()
        vm.beginRemove()
        vm.confirmRemove()
        advanceUntilIdle()
        assertTrue(removeRecorder.called)
    }

    private class Recorder { var called: Boolean = false }

    private fun makeVm(): Pair<IdentityDetailViewModel, Recorder> {
        val rec = Recorder()
        val identity = Identity("ix", "Reader", "desc", "icon", null, null, false, null, false)
        val identities = StubIdentityRepoMin(listOf(identity))
        val stats = object : ComputeIdentityStatsUseCase(StubHabitLogRepo(), identities) {
            override suspend fun computeNow(userId: String, identityId: String) = IdentityStats.empty(identityId)
        }
        val remove = object : RemoveIdentityUseCase(identities) {
            override suspend fun execute(userId: String, identityId: String) { rec.called = true }
        }
        return IdentityDetailViewModel.forTest(
            identityRepo = identities,
            statsUseCase = stats,
            pinUseCase = object : PinIdentityUseCase(identities) {},
            unpinUseCase = object : UnpinIdentityUseCase(identities) {},
            removeUseCase = remove,
            updateWhyUseCase = object : UpdateIdentityWhyUseCase(identities) {},
            userIdProvider = { "u1" },
            identityId = "ix",
        ) to rec
    }
}

private class StubHabitLogRepo : com.habittracker.data.repository.HabitLogRepository {
    override fun observeAllActiveLogsForUser(userId: String) = flowOf(emptyList<com.habittracker.domain.model.HabitLog>())
    override suspend fun getAllActiveLogsForUser(userId: String) = emptyList<com.habittracker.domain.model.HabitLog>()
    override fun observeActiveLogsBetween(userId: String, startInclusive: Instant, endExclusive: Instant) = flowOf(emptyList<com.habittracker.domain.model.HabitLog>())
    override suspend fun firstActiveLogAt(userId: String) = null
    override suspend fun insertLog(id: String, userId: String, habitId: String, quantity: Double, loggedAt: Instant) {}
    override suspend fun deleteLog(id: String, userId: String) {}
    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {}
    override suspend fun clearForUser(userId: String) {}
    override suspend fun getUnsyncedFor(userId: String) = emptyList<com.habittracker.domain.model.HabitLog>()
    override suspend fun markSynced(id: String, syncedAt: Instant) {}
    override suspend fun mergePulled(row: com.habittracker.domain.model.HabitLog) {}
}

private class StubIdentityRepoMin(private val users: List<Identity>) : IdentityRepository {
    override suspend fun getAllIdentities() = emptyList<Identity>()
    override suspend fun upsertIdentities(identities: List<Identity>) {}
    override fun observeUserIdentities(userId: String) = flowOf(users)
    override suspend fun setUserIdentities(userId: String, identityIds: Set<String>) {}
    override suspend fun clearUserIdentitiesForUser(userId: String) {}
    override suspend fun getUnsyncedUserIdentitiesFor(userId: String) = emptyList<UserIdentityRow>()
    override suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant) {}
    override suspend fun mergePulledUserIdentity(row: UserIdentityRow) {}
    override suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean) {}
    override suspend fun clearPinForUser(userId: String) {}
    override suspend fun updateWhyText(userId: String, identityId: String, whyText: String?) {}
    override suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant) {}
    override suspend fun setPinAtomically(userId: String, identityId: String) {}
    override suspend fun getPinnedIdentityIdForUser(userId: String) = null
    override suspend fun getUserIdentityRow(userId: String, identityId: String) = null
    override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {}
    override suspend fun clearHabitIdentitiesForUser(userId: String) {}
    override suspend fun getUnsyncedHabitIdentitiesFor(userId: String) = emptyList<HabitIdentityRow>()
    override suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant) {}
    override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) {}
    override fun observeHabitsForIdentity(userId: String, identityId: String) = flowOf(emptyList<Habit>())
    override suspend fun getHabitIdentityLinksForUser(userId: String) = emptyList<HabitIdentityRow>()
    override suspend fun markHabitIdentityRemoved(habitId: String, identityId: String, effectiveTo: Instant) {}
}
```

> Match the real `Identity` constructor signature — copy from `ComputeIdentityStatsUseCaseTest`. Same for `IdentityStats.empty(...)` and any `*UseCase` `forTest` factories — copy their actual signatures from the existing code.

- [ ] **Step 2: Run test (will fail — methods missing)**

`rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*IdentityDetailViewModelDialog*" 2>&1 | tail -10`
Expected: unresolved `beginRemove`, `confirmRemove`, `dismissRemoveDialog`, `showRemoveDialog`.

- [ ] **Step 3: Add dialog state to ViewModel**

In `IdentityDetailViewModel.kt`:

```kotlin
private val _showRemoveDialog = MutableStateFlow(false)
val showRemoveDialog: StateFlow<Boolean> = _showRemoveDialog.asStateFlow()

fun beginRemove() { _showRemoveDialog.value = true }

fun dismissRemoveDialog() { _showRemoveDialog.value = false }

fun confirmRemove() {
    _showRemoveDialog.value = false
    removeIdentity()
}
```

(Keep the existing `removeIdentity()` private — or rename to `private fun executeRemove()`. The screen now calls `beginRemove` / `confirmRemove` instead of `removeIdentity` directly.)

Add `import kotlinx.coroutines.flow.MutableStateFlow` etc. at the top if missing.

- [ ] **Step 4: Run test**

`rtk ./gradlew :mobile:androidApp:testDebugUnitTest --tests "*IdentityDetailViewModelDialog*" 2>&1 | tail -10`
Expected: PASS.

- [ ] **Step 5: Update `IdentityDetailScreen` to render dialog + "+ Add habit" row**

In `IdentityDetailScreen.kt`:

(a) Add `onAddHabit: () -> Unit = {}` to the screen signature, and pass through to `Body(..., onAddHabit = onAddHabit)`:

```kotlin
@Composable
fun IdentityDetailScreen(
    viewModel: IdentityDetailViewModel,
    onBack: () -> Unit,
    onRemoveSuccess: () -> Unit = {},
    onHabitClick: (String) -> Unit = {},
    onAddHabit: () -> Unit = {},
) {
    // ... existing collectAsState, LaunchedEffect ...

    val showDialog by viewModel.showRemoveDialog.collectAsState()
    if (showDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveDialog,
            title = { Text("Remove identity?") },
            text = { Text("Removing keeps your habits — they stay associated with the identities they support.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmRemove,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemoveDialog) { Text("Cancel") }
            },
        )
    }

    Scaffold(/* existing */) { padding ->
        when (val s = state) {
            // existing branches
            is IdentityDetailState.Loaded -> Body(state = s, padding = padding, viewModel = viewModel, onHabitClick = onHabitClick, onAddHabit = onAddHabit)
        }
    }
}
```

(b) In `Body`, after the `state.habits.forEach { ... HabitRow(...) }` loop, add the "+ Add habit" row:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    state.habits.forEach { habit ->
        HabitRow(
            habit = habit,
            hue = hue,
            otherIdentities = state.otherIdentitiesByHabit[habit.id].orEmpty(),
            onClick = { onHabitClick(habit.id) },
        )
    }
    AddHabitRow(onClick = onAddHabit)
}
```

(c) Define `AddHabitRow` near the other private composables in this file:

```kotlin
@Composable
private fun AddHabitRow(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
                strokeWidth = 1.dp,
                dashLength = 6.dp,
                gapLength = 4.dp,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Add habit",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
```

(d) Replace the existing `onRemove = viewModel::removeIdentity` callback in `ManageActions` invocation with `onRemove = viewModel::beginRemove`.

Add the imports if missing: `androidx.compose.material3.AlertDialog`, `androidx.compose.material.icons.filled.Add`, `com.jktdeveloper.habitto.ui.components.dashedBorder`.

- [ ] **Step 6: Wire callbacks in `AppNavigation`**

In `composable(Screen.IdentityDetail.route)`:

```kotlin
com.jktdeveloper.habitto.ui.identity.IdentityDetailScreen(
    viewModel = vm,
    onBack = { navController.popBackStack() },
    onRemoveSuccess = { navController.popBackStack() },
    onHabitClick = { hid -> navController.navigate(Screen.HabitDetail.route(hid)) },
    onAddHabit = { navController.navigate(Screen.HabitForm.route(identityId = id)) },
)
```

(`id` is already in scope as the navArgument variable; if not, fetch it from `backStackEntry.arguments`.)

- [ ] **Step 7: Build android app**

`rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run android unit tests**

`rtk ./gradlew :mobile:androidApp:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailScreen.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModel.kt \
    mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt \
    mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModelDialogTest.kt
rtk git commit -m "feat(identity): IdentityDetail '+ Add habit' row + Remove identity confirm dialog"
```

---

## Task 12: Final Smoke + Verification

**Files:** none modified — verification only.

- [ ] **Step 1: Run full shared test suite**

`rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run android unit tests**

`rtk ./gradlew :mobile:androidApp:testDebugUnitTest 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Build APK**

`rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual smoke checklist (developer runs on device or emulator)**

- [ ] Open app → tap "You" → "Habits" → see HabitList → tap **FAB "New habit"** → form opens with empty fields, Save disabled
- [ ] Type name, pick at least one identity from "Suggested" chips, type unit, threshold, target → Save enables
- [ ] Tap **Save** → returns to HabitList → new habit appears
- [ ] Tap habit → HabitDetail opens → tap **edit icon (top right)** → form pre-filled with habit data
- [ ] Change name → Save → returns to detail → name updated
- [ ] In edit mode, tap **Delete habit** at bottom → confirm dialog → tap Delete → habit removed from list (soft delete; effectiveTo set)
- [ ] Open IdentityDetail (any identity) → scroll to habits list → tap **"+ Add habit"** dashed row → form opens with that identity pre-selected
- [ ] In IdentityDetail, tap **"Remove identity"** → confirm dialog appears → tap Cancel → dialog closes, identity stays
- [ ] Tap "Remove identity" again → tap **Remove** → identity removed
- [ ] Sign in to a known account that has data → verify past streak / heat unaffected by any of the new operations on a fresh habit
- [ ] Pull-down sync indicator (or trigger app foreground) → verify new custom habit pushes to cloud (`templateId` is null in payload)

- [ ] **Step 5: Apply Supabase migration to remote staging DB**

Coordinate with project owner. Run via Supabase dashboard SQL editor or `supabase db push`:

```sql
ALTER TABLE habits ALTER COLUMN template_id DROP NOT NULL;
```

(Or rely on the file at `supabase/migrations/20260504000000_habits_template_id_nullable.sql` being included in the next deploy.)

- [ ] **Step 6: Final commit (if any cleanup needed)**

```bash
rtk git status
# If clean, no commit needed.
```

---

## Self-Review Notes

**Spec coverage check:**
- ✅ `templateId` nullable — Task 1
- ✅ `markHabitIdentityRemoved` — Task 2
- ✅ `observeHabitsForIdentity` filter — Task 3
- ✅ `SaveHabitUseCase` — Task 4
- ✅ `DeleteHabitUseCase` — Task 5
- ✅ `HabitFormViewModel` — Task 6
- ✅ `HabitFormScreen` — Task 7
- ✅ AppContainer + AppNavigation route — Task 8
- ✅ `HabitListScreen` FAB — Task 9
- ✅ `HabitDetailScreen` edit icon — Task 10
- ✅ `IdentityDetailScreen` "+ Add habit" + Remove confirm dialog — Task 11
- ✅ Manual smoke + Supabase migration — Task 12
- ✅ Spec deferrals (custom-habit in AddIdentityFlow, icon picker, unit dedupe, reorder, past-identities section) explicitly out of scope per spec

**Type consistency:** Method names `create` / `update` / `execute` / `markHabitIdentityRemoved` / `markHabitDeleted` / `beginRemove` / `confirmRemove` / `dismissRemoveDialog` consistent across tasks. `templateId: String?` consistent. `effectiveTo: Instant` consistent.

**Risk:** Task 4's "resume" semantic depends on `linkHabitToIdentities` upsert clearing `effectiveTo`. Task 4 step 4-5 verify and fix this. If the existing impl uses ignore-on-conflict, the test will catch it.
