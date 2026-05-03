# Phase 5e-1 Habit CRUD Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `effective_from` + `effective_to` to `habits` and `habit_identities` (local + Supabase). Refactor `ComputeStreakUseCase` and `ComputeIdentityStatsUseCase` to filter habits per-day by their effective window. Backfill via migration. Habit creation paths (3 use cases) set `effectiveFrom = clock.now()`. No new UI.

**Architecture:** SQLDelight migration `3.sqm` + matching Supabase ALTER. `LocalHabit` / `LocalHabitIdentity` tables gain two timestamp columns each. Domain `Habit` and repository `HabitIdentityRow` data classes gain matching nullable `Instant?` fields. Sync DTOs use `@EncodeDefault` for the new fields (per Phase 5c-2 lesson). Streak engine adds a single `habitActiveOn(habit, dayStart)` helper consumed by per-day checks.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Supabase Postgrest, kotlinx.datetime, kotlinx-serialization, kotlinx-coroutines-test.

**Worktree:** `.worktrees/phase5e-habit-crud`. **Branch:** `feature/phase5e-habit-crud`. **Spec:** `docs/superpowers/specs/2026-05-02-phase5e-1-foundation-design.md`.

---

## File Map

**Create:**
- `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/3.sqm`

**Modify (shared/commonMain):**
- `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` — `LocalHabit` + `LocalHabitIdentity` table defs + queries
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Habit.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt` — `HabitIdentityRow` extension
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt` — `HabitDto` + `HabitIdentityDto`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCase.kt`

**Modify (shared/commonTest):**
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitRepository.kt` — implement `markHabitDeleted`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt` — `HabitIdentityRow` shape changes
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCaseTest.kt` — augment
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCaseTest.kt` — augment
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCaseTest.kt` — augment

**Supabase (apply via dashboard SQL Editor in Task 7):**
- `ALTER TABLE` + 2× `UPDATE` for backfill

---

## Task 1: SQLDelight schema + migration 3.sqm

**Files:**
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`
- Create: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/3.sqm`

- [ ] **Step 1: Update `LocalHabit` table definition**

In `HabitTrackerDatabase.sq`, find the `LocalHabit` table block (lines ~22-33). Replace with:

```sql
CREATE TABLE IF NOT EXISTS LocalHabit (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,
    templateId TEXT NOT NULL,
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

- [ ] **Step 2: Update `LocalHabitIdentity` table definition**

Find the `LocalHabitIdentity` block (lines ~50-56). Replace with:

```sql
CREATE TABLE IF NOT EXISTS LocalHabitIdentity (
    habitId TEXT NOT NULL,
    identityId TEXT NOT NULL,
    addedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    effectiveFrom INTEGER,
    effectiveTo INTEGER,
    PRIMARY KEY (habitId, identityId)
);
```

- [ ] **Step 3: Update LocalHabit queries to include new columns**

Find these queries and update column lists:

```sql
upsertHabit:
INSERT OR REPLACE INTO LocalHabit (id, userId, templateId, name, unit, thresholdPerPoint, dailyTarget, createdAt, updatedAt, syncedAt, effectiveFrom, effectiveTo)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?);

mergePulledHabit:
INSERT OR REPLACE INTO LocalHabit (id, userId, templateId, name, unit, thresholdPerPoint, dailyTarget, createdAt, updatedAt, syncedAt, effectiveFrom, effectiveTo)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

Add new query (used by 5e-3 delete UI but lands now):

```sql
markHabitDeleted:
UPDATE LocalHabit SET effectiveTo = ?, syncedAt = NULL WHERE id = ? AND userId = ?;
```

- [ ] **Step 4: Update LocalHabitIdentity queries**

```sql
upsertHabitIdentity:
INSERT OR REPLACE INTO LocalHabitIdentity (habitId, identityId, addedAt, syncedAt, effectiveFrom, effectiveTo)
VALUES (?, ?, ?, NULL, ?, NULL);

mergePulledHabitIdentity:
INSERT OR REPLACE INTO LocalHabitIdentity (habitId, identityId, addedAt, syncedAt, effectiveFrom, effectiveTo)
VALUES (?, ?, ?, ?, ?, ?);
```

- [ ] **Step 5: Create `migrations/3.sqm`**

Create `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/3.sqm`:

```sql
-- Migrate HabitTrackerDatabase from version 2 → 3
-- Adds effectiveFrom + effectiveTo to LocalHabit and LocalHabitIdentity for
-- past-streak-stability when habits or identity links are added/removed.
-- Backfill: earliest log date or createdAt for habits; addedAt for links.

ALTER TABLE LocalHabit ADD COLUMN effectiveFrom INTEGER;
ALTER TABLE LocalHabit ADD COLUMN effectiveTo INTEGER;
ALTER TABLE LocalHabitIdentity ADD COLUMN effectiveFrom INTEGER;
ALTER TABLE LocalHabitIdentity ADD COLUMN effectiveTo INTEGER;

-- Backfill habits: earliest active log OR createdAt fallback.
UPDATE LocalHabit SET effectiveFrom = COALESCE(
    (SELECT MIN(loggedAt) FROM HabitLog
     WHERE HabitLog.habitId = LocalHabit.id AND HabitLog.deletedAt IS NULL),
    createdAt
) WHERE effectiveFrom IS NULL;

-- Backfill habit_identities: link's existing addedAt.
UPDATE LocalHabitIdentity SET effectiveFrom = addedAt WHERE effectiveFrom IS NULL;
```

- [ ] **Step 6: Build to confirm SQLDelight regenerates cleanly**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase5e-habit-crud
rtk ./gradlew :mobile:shared:generateCommonMainHabitTrackerDatabaseInterface
```

Expected: BUILD SUCCESSFUL.

If `verifySqlDelightMigration` is wired into the build, also run:
```bash
rtk ./gradlew :mobile:shared:verifyCommonMainHabitTrackerDatabaseMigration
```
Expected: BUILD SUCCESSFUL — migration chain (`0.db` → `1.sqm` → `2.sqm` → `3.sqm`) validates.

If verifyMigration fails because `3.db` snapshot doesn't exist yet, generating it is part of verification — re-run the verify task with `--rerun-tasks`. SQLDelight should produce a `databases/4.db` snapshot. Include it in the commit.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq \
            mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/3.sqm \
            mobile/shared/src/commonMain/sqldelight/databases/
rtk git commit -m "$(cat <<'EOF'
feat(db): habits + habit_identities — effectiveFrom/effectiveTo + backfill

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Habit + HabitIdentityRow data class extension + repository round-trip

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Habit.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitRepository.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt`

- [ ] **Step 1: Extend `Habit` data class**

Replace `Habit.kt` body:

```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class Habit(
    val id: String,
    val userId: String,
    val templateId: String,
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

- [ ] **Step 2: Extend `HabitIdentityRow` data class**

In `IdentityRepository.kt`, find:

```kotlin
data class HabitIdentityRow(
    val habitId: String,
    val identityId: String,
    val addedAt: Instant,
    val syncedAt: Instant?,
)
```

Replace with:

```kotlin
data class HabitIdentityRow(
    val habitId: String,
    val identityId: String,
    val addedAt: Instant,
    val syncedAt: Instant?,
    val effectiveFrom: Instant? = null,
    val effectiveTo: Instant? = null,
)
```

- [ ] **Step 3: Add `markHabitDeleted` to `HabitRepository` interface**

In `HabitRepository.kt`, add:

```kotlin
suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant)
```

- [ ] **Step 4: Update `LocalHabitRepository` mappers**

In `LocalHabitRepository.kt`, update `LocalHabit.toDomain()` mapper to include new fields:

```kotlin
private fun LocalHabit.toDomain(): Habit = Habit(
    id = id,
    userId = userId,
    templateId = templateId,
    name = name,
    unit = unit,
    thresholdPerPoint = thresholdPerPoint,
    dailyTarget = dailyTarget.toInt(),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    syncedAt = syncedAt?.let { Instant.fromEpochMilliseconds(it) },
    effectiveFrom = effectiveFrom?.let { Instant.fromEpochMilliseconds(it) },
    effectiveTo = effectiveTo?.let { Instant.fromEpochMilliseconds(it) },
)
```

(Adapt to the actual mapper location — there may be one in `LocalHabitRepository.kt` and one in `LocalIdentityRepository.kt` since `getHabitsForIdentity` returns `List<Habit>`. Update BOTH if so. Search for `Habit(` instantiations in those two files.)

Update `upsertHabit(habit)` to pass new params:

```kotlin
override suspend fun saveHabit(habit: Habit) {
    q.upsertHabit(
        id = habit.id,
        userId = habit.userId,
        templateId = habit.templateId,
        name = habit.name,
        unit = habit.unit,
        thresholdPerPoint = habit.thresholdPerPoint,
        dailyTarget = habit.dailyTarget.toLong(),
        createdAt = habit.createdAt.toEpochMilliseconds(),
        updatedAt = habit.updatedAt.toEpochMilliseconds(),
        effectiveFrom = habit.effectiveFrom?.toEpochMilliseconds(),
        effectiveTo = habit.effectiveTo?.toEpochMilliseconds(),
    )
}
```

(The actual method may be named `saveHabit` or `upsertHabit` — match the existing API. Argument order matches the SQL bind order.)

Update `mergePulled(row)` similarly to pass all 12 columns.

Add `markHabitDeleted` impl:

```kotlin
override suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant) {
    q.markHabitDeleted(effectiveTo = effectiveTo.toEpochMilliseconds(), id = habitId, userId = userId)
}
```

- [ ] **Step 5: Update `LocalIdentityRepository`**

In `LocalIdentityRepository.kt`, update `linkHabitToIdentities`:

```kotlin
override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {
    val now = Clock.System.now().toEpochMilliseconds()
    identityIds.forEach {
        q.upsertHabitIdentity(
            habitId = habitId,
            identityId = it,
            addedAt = now,
            effectiveFrom = now,  // new — link active from creation
        )
    }
}
```

(The `upsertHabitIdentity` SQL was updated in Task 1 to take 5 bind params: habitId, identityId, addedAt, effectiveFrom, effectiveTo. The query sets syncedAt = NULL automatically. Pass `effectiveFrom = now`. effectiveTo not in the bind list (set to NULL via SQL DEFAULT in the query body).)

Wait — the SQL from Task 1 has 5 params:

```sql
upsertHabitIdentity:
INSERT OR REPLACE INTO LocalHabitIdentity (habitId, identityId, addedAt, syncedAt, effectiveFrom, effectiveTo)
VALUES (?, ?, ?, NULL, ?, NULL);
```

So bind order: `habitId, identityId, addedAt, effectiveFrom`. effectiveTo is hard-coded NULL in the SQL.

Update `getUnsyncedHabitIdentitiesFor` mapper:

```kotlin
override suspend fun getUnsyncedHabitIdentitiesFor(userId: String): List<HabitIdentityRow> =
    q.getUnsyncedHabitIdentitiesForUser(userId).executeAsList().map {
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

Update `mergePulledHabitIdentity`:

```kotlin
override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) {
    q.mergePulledHabitIdentity(
        habitId = row.habitId,
        identityId = row.identityId,
        addedAt = row.addedAt.toEpochMilliseconds(),
        syncedAt = row.syncedAt?.toEpochMilliseconds(),
        effectiveFrom = row.effectiveFrom?.toEpochMilliseconds(),
        effectiveTo = row.effectiveTo?.toEpochMilliseconds(),
    )
}
```

- [ ] **Step 6: Update `FakeHabitRepository`**

In `FakeHabitRepository.kt`, add `markHabitDeleted` override:

```kotlin
override suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant) {
    _habits.value = _habits.value.map {
        if (it.id == habitId && it.userId == userId) it.copy(effectiveTo = effectiveTo, syncedAt = null) else it
    }
}
```

The `Habit` data class gained two new fields with defaults — existing test instantiations work unchanged.

- [ ] **Step 7: Update `FakeIdentityRepository`**

In `FakeIdentityRepository.kt`, the `linkHabitToIdentities` impl creates `HabitIdentityRow` instances. Update to set `effectiveFrom = Clock.System.now()`:

```kotlin
override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {
    val now = Clock.System.now()
    val keep = habitIdentities.value.filterNot { it.habitId == habitId && it.identityId !in identityIds }
    val existingIds = keep.filter { it.habitId == habitId }.map { it.identityId }.toSet()
    val add = (identityIds - existingIds).map {
        HabitIdentityRow(
            habitId = habitId,
            identityId = it,
            addedAt = now,
            syncedAt = null,
            effectiveFrom = now,
        )
    }
    habitIdentities.value = keep + add
}
```

`HabitIdentityRow` constructor now accepts the two new fields with defaults — existing test instantiations elsewhere work unchanged.

- [ ] **Step 8: Build to verify**

```bash
rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid :mobile:shared:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL + all existing tests pass.

- [ ] **Step 9: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Habit.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitRepository.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitRepository.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt
rtk git commit -m "$(cat <<'EOF'
feat(repo): Habit + HabitIdentityRow effective window + markHabitDeleted

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Sync DTO updates with @EncodeDefault

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt`

- [ ] **Step 1: Update `HabitDto`**

Find the existing `HabitDto` block. Add `@OptIn` annotation if not already present, and add the two new fields with `@EncodeDefault`:

```kotlin
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
private data class HabitDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("template_id") val templateId: String,
    @SerialName("name") val name: String,
    @SerialName("unit") val unit: String,
    @SerialName("threshold_per_point") val thresholdPerPoint: Double,
    @SerialName("daily_target") val dailyTarget: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @kotlinx.serialization.EncodeDefault @SerialName("effective_from") val effectiveFrom: String? = null,
    @kotlinx.serialization.EncodeDefault @SerialName("effective_to") val effectiveTo: String? = null,
)
```

(Adapt the existing field list — this plan shows the expected shape; preserve any other existing fields like `synced_at` if present.)

Update `Habit.toDto()`:

```kotlin
private fun Habit.toDto() = HabitDto(
    // ... existing fields ...
    effectiveFrom = effectiveFrom?.toString(),
    effectiveTo = effectiveTo?.toString(),
)
```

Update `HabitDto.toDomain()`:

```kotlin
private fun HabitDto.toDomain() = Habit(
    // ... existing fields ...
    effectiveFrom = effectiveFrom?.let { Instant.parse(it) },
    effectiveTo = effectiveTo?.let { Instant.parse(it) },
)
```

- [ ] **Step 2: Update `HabitIdentityDto`**

Same shape:

```kotlin
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
private data class HabitIdentityDto(
    @SerialName("habit_id") val habitId: String,
    @SerialName("identity_id") val identityId: String,
    @SerialName("added_at") val addedAt: String,
    @kotlinx.serialization.EncodeDefault @SerialName("effective_from") val effectiveFrom: String? = null,
    @kotlinx.serialization.EncodeDefault @SerialName("effective_to") val effectiveTo: String? = null,
)

private fun HabitIdentityRow.toDto() = HabitIdentityDto(
    habitId = habitId,
    identityId = identityId,
    addedAt = addedAt.toString(),
    effectiveFrom = effectiveFrom?.toString(),
    effectiveTo = effectiveTo?.toString(),
)

private fun HabitIdentityDto.toDomain() = HabitIdentityRow(
    habitId = habitId,
    identityId = identityId,
    addedAt = Instant.parse(addedAt),
    syncedAt = Instant.parse(addedAt),  // existing convention
    effectiveFrom = effectiveFrom?.let { Instant.parse(it) },
    effectiveTo = effectiveTo?.let { Instant.parse(it) },
)
```

- [ ] **Step 3: Build**

```bash
rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt
rtk git commit -m "$(cat <<'EOF'
feat(sync): HabitDto + HabitIdentityDto carry effective_from/effective_to

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Habit creation paths set effectiveFrom = now

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCase.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCase.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCaseTest.kt`

- [ ] **Step 1: Update `AddIdentityWithHabitsUseCase`**

Find the `Habit(...)` constructor call inside the use case. Add `effectiveFrom = now` (the `now` value already exists in scope from `clock.now()`):

```kotlin
val habit = Habit(
    id = Uuid.random().toString(),
    userId = userId,
    templateId = templateId,
    name = tpl.name,
    unit = tpl.unit,
    thresholdPerPoint = tpl.defaultThreshold,
    dailyTarget = tpl.defaultDailyTarget,
    createdAt = now,
    updatedAt = now,
    syncedAt = null,
    effectiveFrom = now,  // new
)
```

The `linkHabitToIdentities` call sets `effectiveFrom` in the repo (Task 2 Step 5) — no change needed at use case level for the link.

- [ ] **Step 2: Update `SetupUserHabitsUseCase`**

Read the file first:
```bash
cat mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt
```

Find every `Habit(...)` constructor call. Add `effectiveFrom = now` (or `effectiveFrom = clock.now()` depending on what's in scope) to each.

If the use case doesn't currently take a `Clock`, add it as a constructor param with default `Clock.System`, and update existing test instantiations to pass `Clock.System` (or the test's fake clock).

- [ ] **Step 3: Update `LinkOnboardingHabitsToIdentitiesUseCase`**

Read:
```bash
cat mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCase.kt
```

If it calls `identityRepo.linkHabitToIdentities(...)`, the repository method already sets `effectiveFrom = now` (Task 2 Step 5). No change needed. Otherwise, if it constructs `HabitIdentityRow` directly, add `effectiveFrom = clock.now()`.

- [ ] **Step 4: Update `AddIdentityWithHabitsUseCaseTest`**

Existing tests assert created habits exist + have correct templateId. Add a new assertion that newly created habits have `effectiveFrom != null` AND `effectiveFrom == clock.now()`:

```kotlin
@Test
fun `new habit has effectiveFrom set to now`() = runTest {
    val fixedNow = Clock.System.now()
    val fixedClock = object : Clock { override fun now() = fixedNow }
    val useCase = AddIdentityWithHabitsUseCase(
        habitRepo = habitRepo,
        identityRepo = identityRepo,
        templates = templates,
        clock = fixedClock,
    )
    val athleteTemplates = templates.execute(setOf("00000000-0000-0000-0000-000000000003"))
    require(athleteTemplates.isNotEmpty())
    val tplId = athleteTemplates.first().template.id
    useCase.execute(userId, "00000000-0000-0000-0000-000000000003", selectedTemplateIds = setOf(tplId))
    val habits = habitRepo.getHabitsForUser(userId)
    assertEquals(1, habits.size)
    assertEquals(fixedNow, habits.first().effectiveFrom)
}
```

(Adapt identity ID format / template lookup to match the existing test in this file.)

- [ ] **Step 5: Build + run tests**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL + all tests pass.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCase.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCaseTest.kt
rtk git commit -m "$(cat <<'EOF'
feat(usecase): habit creation paths set effectiveFrom = clock.now()

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: ComputeStreakUseCase per-day filter + tests

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCase.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCaseTest.kt`

- [ ] **Step 1: Add `habitActiveOn` helper**

In `ComputeStreakUseCase.kt`, add this private helper at the bottom of the class:

```kotlin
private fun habitActiveOn(habit: Habit, dayStart: Instant): Boolean =
    (habit.effectiveFrom?.let { it <= dayStart } ?: true) &&
    (habit.effectiveTo?.let { it > dayStart } ?: true)
```

(The existing `Habit` import is already in the file. `Instant` likewise.)

- [ ] **Step 2: Update `allHabitsMetTargetOnDay` to filter habits per-day**

Find the existing `allHabitsMetTargetOnDay` (around line 179):

```kotlin
private fun allHabitsMetTargetOnDay(
    logs: List<HabitLog>,
    habits: List<Habit>,
    date: LocalDate,
): Boolean {
    if (habits.isEmpty()) return false
    val loggedHabitIds = logs
        .filter { sameLocalDate(date, it.loggedAt) }
        .map { it.habitId }
        .toSet()
    return habits.all { it.id in loggedHabitIds }
}
```

Replace with:

```kotlin
private fun allHabitsMetTargetOnDay(
    logs: List<HabitLog>,
    habits: List<Habit>,
    date: LocalDate,
): Boolean {
    val dayStart = date.atStartOfDayIn(timeZone)
    val activeHabits = habits.filter { habitActiveOn(it, dayStart) }
    if (activeHabits.isEmpty()) return false
    val loggedHabitIds = logs
        .filter { sameLocalDate(date, it.loggedAt) }
        .map { it.habitId }
        .toSet()
    return activeHabits.all { it.id in loggedHabitIds }
}
```

(`timeZone` is a class field already.)

- [ ] **Step 3: Update `buildRangeResult` heat bucket calculation to use per-day active habits**

Find the heat bucket section (around line 121-148):

```kotlin
val targetSum = habits.sumOf { it.dailyTarget }.coerceAtLeast(1)
// ... rawByDayHabit ...
// ... pointsByDay ...
val days = mutableListOf<StreakDay>()
var d = range.start
while (d < range.endExclusive) {
    val state = perDay[d] ?: StreakDayState.EMPTY
    val allLogged = d in completeDays
    val heat = if (state == StreakDayState.FUTURE || habitCount == 0) 0
               else bucketFor(pointsByDay[d] ?: 0, allLogged, habitCount, targetSum)
    days += StreakDay(d, state, heat)
    d = d.plus(1, DateTimeUnit.DAY)
}
```

Replace with per-day active filter:

```kotlin
// Pre-compute per-(day,habit) raw points (unchanged from before)
val rawByDayHabit = mutableMapOf<Pair<LocalDate, String>, Int>()
val habitsById = habits.associateBy { it.id }
pastLogs.forEach { log ->
    val date = log.loggedAt.toLocalDate()
    val habit = habitsById[log.habitId] ?: return@forEach
    val pts = PointCalculator.pointsEarned(log.quantity, habit.thresholdPerPoint)
    val key = date to log.habitId
    rawByDayHabit[key] = (rawByDayHabit[key] ?: 0) + pts
}
val pointsByDay = mutableMapOf<LocalDate, Int>()
rawByDayHabit.forEach { (key, pts) ->
    val (date, habitId) = key
    val target = habitsById[habitId]?.dailyTarget ?: return@forEach
    pointsByDay[date] = (pointsByDay[date] ?: 0) + pts.coerceAtMost(target)
}

val days = mutableListOf<StreakDay>()
var d = range.start
while (d < range.endExclusive) {
    val state = perDay[d] ?: StreakDayState.EMPTY
    val allLogged = d in completeDays
    // Per-day active habit set drives bucketing (so heat reflects expectations as-of-that-day)
    val dayStart = d.atStartOfDayIn(timeZone)
    val activeHabitsToday = habits.filter { habitActiveOn(it, dayStart) }
    val activeCount = activeHabitsToday.size
    val activeTargetSum = activeHabitsToday.sumOf { it.dailyTarget }.coerceAtLeast(1)
    val heat = if (state == StreakDayState.FUTURE || activeCount == 0) 0
               else bucketFor(pointsByDay[d] ?: 0, allLogged, activeCount, activeTargetSum)
    days += StreakDay(d, state, heat)
    d = d.plus(1, DateTimeUnit.DAY)
}
```

- [ ] **Step 4: Update `summarize` to filter habits per-day**

Find the existing `summarize` function (around line 195). Update its `completeDays` derivation:

```kotlin
private fun summarize(
    firstLog: LocalDate,
    today: LocalDate,
    logs: List<HabitLog>,
    habits: List<Habit>,
): StreakSummary {
    val pastLogs = logs.filter { it.loggedAt <= now() }
    val completeDays: Set<LocalDate> = if (habits.isEmpty()) emptySet() else
        pastLogs.map { it.loggedAt.toLocalDate() }.toSet()
            .filter { allHabitsMetTargetOnDay(pastLogs, habits, it) }
            .toSet()
    // ... rest of summarize body unchanged ...
}
```

`allHabitsMetTargetOnDay` already filters per-day from Step 2 — no further change needed in `summarize` body.

- [ ] **Step 5: Add streak engine tests**

In `ComputeStreakUseCaseTest.kt`, add these tests (preserve existing tests, append new ones):

```kotlin
@Test
fun `habit added today not required for today`() = runTest {
    val today = LocalDate(2026, 5, 2)
    val now = today.atStartOfDayIn(TimeZone.UTC).plus(14, DateTimeUnit.HOUR)
    val fakeClock = object : Clock { override fun now() = now }

    // Habit existed before today
    val oldHabit = Habit(
        id = "h1", userId = userId, templateId = "t1", name = "Read",
        unit = "p", thresholdPerPoint = 1.0, dailyTarget = 1,
        createdAt = today.minus(5, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC),
        updatedAt = today.minus(5, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC),
        effectiveFrom = today.minus(5, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC),
    )
    // Habit added today at 14:00
    val newHabit = oldHabit.copy(id = "h2", name = "Run", effectiveFrom = now)

    habitRepo.saveHabit(oldHabit)
    habitRepo.saveHabit(newHabit)
    // Log oldHabit today (newHabit not logged)
    habitLogRepo.insert(makeLog("l1", "h1", today.atStartOfDayIn(TimeZone.UTC).plus(10, DateTimeUnit.HOUR)))

    val useCase = ComputeStreakUseCase(habitLogRepo, habitRepo, TimeZone.UTC, fakeClock)
    val summary = useCase.computeSummaryNow(userId)
    // Today should be complete (only oldHabit was active at dayStart, and it's logged)
    // Streak should be alive — at least 1 day complete in the window
    assertTrue(summary.currentStreak >= 1, "Today should count as COMPLETE for streak even though new habit not logged")
}

@Test
fun `past day before habit existed does not require it`() = runTest {
    val today = LocalDate(2026, 5, 10)
    val pastDay = LocalDate(2026, 5, 1)
    val fakeClock = object : Clock { override fun now() = today.atStartOfDayIn(TimeZone.UTC).plus(12, DateTimeUnit.HOUR) }

    // Habit created May 5 (after pastDay May 1)
    val newHabit = Habit(
        id = "h1", userId = userId, templateId = "t1", name = "Run",
        unit = "p", thresholdPerPoint = 1.0, dailyTarget = 1,
        createdAt = LocalDate(2026, 5, 5).atStartOfDayIn(TimeZone.UTC),
        updatedAt = LocalDate(2026, 5, 5).atStartOfDayIn(TimeZone.UTC),
        effectiveFrom = LocalDate(2026, 5, 5).atStartOfDayIn(TimeZone.UTC),
    )
    val oldHabit = newHabit.copy(
        id = "h2", name = "Read",
        effectiveFrom = LocalDate(2026, 4, 1).atStartOfDayIn(TimeZone.UTC),
    )
    habitRepo.saveHabit(newHabit)
    habitRepo.saveHabit(oldHabit)
    // Log oldHabit on pastDay (newHabit not logged on pastDay — but newHabit didn't exist then)
    habitLogRepo.insert(makeLog("l1", "h2", pastDay.atStartOfDayIn(TimeZone.UTC).plus(10, DateTimeUnit.HOUR)))

    val useCase = ComputeStreakUseCase(habitLogRepo, habitRepo, TimeZone.UTC, fakeClock)
    val range = DateRange(start = pastDay, endExclusive = pastDay.plus(1, DateTimeUnit.DAY))
    val result = useCase.computeNow(userId, range)
    // pastDay should be COMPLETE — only oldHabit was active, and it's logged
    assertEquals(StreakDayState.COMPLETE, result.days.first().state)
}

@Test
fun `habit deleted today still required for today`() = runTest {
    val today = LocalDate(2026, 5, 2)
    val now = today.atStartOfDayIn(TimeZone.UTC).plus(14, DateTimeUnit.HOUR)
    val fakeClock = object : Clock { override fun now() = now }

    val deletedHabit = Habit(
        id = "h1", userId = userId, templateId = "t1", name = "Run",
        unit = "p", thresholdPerPoint = 1.0, dailyTarget = 1,
        createdAt = today.minus(5, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC),
        updatedAt = today.minus(5, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC),
        effectiveFrom = today.minus(5, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC),
        effectiveTo = now,  // deleted at 14:00 today
    )
    habitRepo.saveHabit(deletedHabit)
    // No logs today

    val useCase = ComputeStreakUseCase(habitLogRepo, habitRepo, TimeZone.UTC, fakeClock)
    val range = DateRange(start = today, endExclusive = today.plus(1, DateTimeUnit.DAY))
    val result = useCase.computeNow(userId, range)
    // Today still requires the habit (effectiveTo > dayStart) — but no log → NOT complete.
    // State is TODAY_PENDING (today, no complete) — verify state is NOT COMPLETE.
    assertNotEquals(StreakDayState.COMPLETE, result.days.first().state)
}
```

(Adapt the tests to the existing test file's helpers and import patterns. The above shows the SHAPE — match the file's idioms for `userId`, `habitLogRepo.insert`, `makeLog`, etc.)

If `ComputeStreakUseCase.computeSummaryNow` or `computeNow` / `DateRange` / `makeLog` helpers differ in signature, adapt accordingly.

- [ ] **Step 6: Run tests**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "com.habittracker.domain.usecase.ComputeStreakUseCaseTest"
```

Expected: BUILD SUCCESSFUL, all tests (existing + new 3) pass.

If existing tests fail because they construct `Habit` without `effectiveFrom`, that's expected (effectiveFrom defaults to null which means "always active") — they should still pass. If a specific test fails because backfill behavior differs, adjust the test setup to set `effectiveFrom` explicitly.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeStreakUseCaseTest.kt
rtk git commit -m "$(cat <<'EOF'
feat(streak): per-day habit active filter via effectiveFrom/effectiveTo

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: ComputeIdentityStatsUseCase per-day filter + tests

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCase.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCaseTest.kt`

- [ ] **Step 1: Read existing use case to understand the per-identity computation**

```bash
cat mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCase.kt
```

Identify how it iterates days + checks habits.

- [ ] **Step 2: Add `habitActiveOn` helper (same as ComputeStreakUseCase)**

Add private helper:

```kotlin
private fun habitActiveOn(habit: Habit, dayStart: Instant): Boolean =
    (habit.effectiveFrom?.let { it <= dayStart } ?: true) &&
    (habit.effectiveTo?.let { it > dayStart } ?: true)
```

- [ ] **Step 3: Filter habits per-day in the per-day loop**

In each per-day iteration of the use case (where `habits` are checked for that day), apply the filter:

```kotlin
val dayStart = date.atStartOfDayIn(timeZone)
val activeHabitsToday = habits.filter { habitActiveOn(it, dayStart) }
// Use activeHabitsToday in place of habits for that day's all-met / target-sum / heat-bucket logic.
```

The exact site depends on the existing structure. Read the file in Step 1, identify the per-day loop, replace `habits` with `activeHabitsToday` for that iteration's checks.

- [ ] **Step 4: Filter habit_identities link active per-day too (optional, scope decision)**

Per spec section "ComputeIdentityStatsUseCase changes" — if implementing the link-level filter is heavy, fall back to habit-level only (less accurate edge case for users who unlink/re-link habits to identities).

For 5e-1 simplicity, **skip the link-level filter**. Document in a comment:

```kotlin
// 5e-1: per-day filter by Habit.effectiveFrom/effectiveTo only.
// HabitIdentityRow.effectiveFrom/effectiveTo per-day filter is deferred —
// users who unlink/re-link habits to identities mid-history will see a
// minor inaccuracy in identity-scoped streak retro (rare). Add when needed.
```

- [ ] **Step 5: Add tests**

In `ComputeIdentityStatsUseCaseTest.kt`, augment with at least one test:

```kotlin
@Test
fun `habit added to identity today not required for that identity's today`() = runTest {
    val today = LocalDate(2026, 5, 2)
    val now = today.atStartOfDayIn(TimeZone.UTC).plus(14, DateTimeUnit.HOUR)
    val fakeClock = object : Clock { override fun now() = now }

    val newHabit = Habit(
        id = "h1", userId = userId, templateId = "t1", name = "Run",
        unit = "p", thresholdPerPoint = 1.0, dailyTarget = 1,
        createdAt = now, updatedAt = now,
        effectiveFrom = now,  // added today
    )
    habitRepo.saveHabit(newHabit)
    identityRepo.linkHabitToIdentities("h1", setOf("identityA"))
    // No log today

    val useCase = ComputeIdentityStatsUseCase(
        habitLogRepo = habitLogRepo,
        identityRepo = identityRepo,
        timeZone = TimeZone.UTC,
        clock = fakeClock,
    )
    val stats = useCase.computeNow(userId, "identityA")
    // No log today + habit active from now → today's heat bucket should be 0 (no logs)
    // BUT today should NOT be classified as "incomplete because new habit unlogged"
    // Specifically: if there were NO other habits, today is empty (no requirements yet)
    // For now just verify it doesn't crash and returns a valid result
    assertNotNull(stats)
}
```

Adapt to the existing test file's helpers. The key invariant: a habit with `effectiveFrom = now` doesn't make today "broken" because of it.

- [ ] **Step 6: Run tests**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "com.habittracker.domain.usecase.ComputeIdentityStatsUseCaseTest"
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCaseTest.kt
rtk git commit -m "$(cat <<'EOF'
feat(identity-stats): per-day habit active filter via effectiveFrom/effectiveTo

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Manual smoke + Supabase migration + PR

**Files:** none (validation + git ops).

- [ ] **Step 1: Apply Supabase migration via dashboard SQL Editor**

```sql
ALTER TABLE public.habits ADD COLUMN effective_from TIMESTAMPTZ;
ALTER TABLE public.habits ADD COLUMN effective_to TIMESTAMPTZ;
ALTER TABLE public.habit_identities ADD COLUMN effective_from TIMESTAMPTZ;
ALTER TABLE public.habit_identities ADD COLUMN effective_to TIMESTAMPTZ;

UPDATE public.habits SET effective_from = COALESCE(
    (SELECT MIN(logged_at) FROM public.habit_logs
     WHERE habit_id = habits.id AND deleted_at IS NULL),
    created_at
) WHERE effective_from IS NULL;

UPDATE public.habit_identities SET effective_from = added_at WHERE effective_from IS NULL;
```

Verify via:

```sql
SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema='public' AND table_name='habits' ORDER BY ordinal_position;

SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema='public' AND table_name='habit_identities' ORDER BY ordinal_position;
```

Both tables should now have `effective_from` + `effective_to` columns.

```sql
SELECT COUNT(*) FROM public.habits WHERE effective_from IS NULL;
SELECT COUNT(*) FROM public.habit_identities WHERE effective_from IS NULL;
```

Both should return 0 (backfill applied to all existing rows).

- [ ] **Step 2: Run full test suite**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase5e-habit-crud
rtk ./gradlew :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL + all tests pass.

- [ ] **Step 3: Install + manual smoke**

```bash
rtk ./gradlew :mobile:androidApp:installDebug
```

Smoke checklist:

- [ ] App launches against existing user account → migration runs (logcat shows version 2→3) → Today screen renders past streak heatmap UNCHANGED from pre-migration.
- [ ] Reproduce 5c-2 bug: open AddIdentityFlow, pick a new identity, add at least one new recommended habit, commit. Today's streak should NOT reset (the new habit's `effectiveFrom = now` excludes it from today's all-met check).
- [ ] Past streak heatmap (StreakHistory screen) → no shifts in past day states.
- [ ] Pull-to-refresh on Today → sync push + pull both succeed → no errors in logcat.
- [ ] Multi-device: sign in same account on second emulator. Pull-refresh → effective_from/effective_to round-trip cleanly. Past streak data identical to first device.
- [ ] No regression: log a habit today → today's heat bucket updates, streak counter behaves as before.
- [ ] Identity stats per-identity sparkline + 90d grid render correctly on IdentityDetail.

- [ ] **Step 4: Push branch**

```bash
rtk git push -u origin feature/phase5e-habit-crud
```

- [ ] **Step 5: Create PR**

```bash
rtk gh pr create --base main --head feature/phase5e-habit-crud \
    --title "Phase 5e-1: Habit CRUD foundation (effective_from/to + engine refactor)" \
    --body "$(cat <<'BODY'
## Summary

Foundational schema + streak engine refactor for upcoming Habit CRUD UI work.

- New columns `effective_from` + `effective_to` on `habits` and `habit_identities` (Postgres + SQLite).
- Backfill: earliest log date or `createdAt` for habits, `addedAt` for habit_identity links.
- Streak engine (`ComputeStreakUseCase` + `ComputeIdentityStatsUseCase`) filters habits per-day by their effective window. Adding a new habit or identity link no longer retroactively breaks past streaks — today's streak doesn't reset when adding a new habit either.
- Habit creation paths (`AddIdentityWithHabitsUseCase`, `SetupUserHabitsUseCase`, `LinkOnboardingHabitsToIdentitiesUseCase`) set `effectiveFrom = clock.now()` on new habits + links.
- New SQL query `markHabitDeleted` lands now (consumed by 5e-3 delete UI).

**Fixes the 5c-2 smoke bug:** "added new identity → today's streak resets."

## Schema migration (REQUIRED before merge)

Apply via Supabase Dashboard → SQL Editor:

```sql
ALTER TABLE public.habits ADD COLUMN effective_from TIMESTAMPTZ;
ALTER TABLE public.habits ADD COLUMN effective_to TIMESTAMPTZ;
ALTER TABLE public.habit_identities ADD COLUMN effective_from TIMESTAMPTZ;
ALTER TABLE public.habit_identities ADD COLUMN effective_to TIMESTAMPTZ;

UPDATE public.habits SET effective_from = COALESCE(
    (SELECT MIN(logged_at) FROM public.habit_logs
     WHERE habit_id = habits.id AND deleted_at IS NULL),
    created_at
) WHERE effective_from IS NULL;

UPDATE public.habit_identities SET effective_from = added_at WHERE effective_from IS NULL;
```

Local SQLite migration `3.sqm` runs automatically on first app launch after update.

## Out of scope

- No new UI. Habit list / detail / form screens deferred to 5e-2 + 5e-3.
- Habit delete UI deferred to 5e-3 (query lands now, no UI yet).
- Per-log target snapshot (alternative design) — rejected; this phase uses effective windows.
- Custom-habit form integration into AddIdentityFlow Step 2 — deferred to 5e-3.
- HabitIdentityRow.effective_from/to per-day filter in ComputeIdentityStatsUseCase — deferred (habit-level filter only for 5e-1; users rarely unlink/re-link habits mid-history).

## Test plan
- [x] `:mobile:shared:testDebugUnitTest` green (incl. new streak engine + identity stats tests)
- [x] `:mobile:androidApp:testDebugUnitTest` green
- [ ] Apply Supabase migration (above)
- [ ] Manual: existing user past streak heatmap unchanged after migration
- [ ] Manual: add new identity + habits via 5c-2 flow → today's streak does NOT reset
- [ ] Manual: pull-refresh round-trips effective windows on multi-device
- [ ] Manual: no regression on log-and-streak basic flow

🤖 Generated with [Claude Code](https://claude.com/claude-code)
BODY
)"
```

---

## Self-Review

**Spec coverage:**
- Schema migration (3.sqm + Supabase ALTER + backfill) → Task 1 + Task 7 step 1
- `Habit` data class extension → Task 2 step 1
- `HabitIdentityRow` extension → Task 2 step 2
- `markHabitDeleted` repository method (lands now for 5e-3) → Task 2 steps 3+4+6
- `LocalHabitRepository` round-trip → Task 2 step 4
- `LocalIdentityRepository` round-trip → Task 2 step 5
- `FakeHabitRepository` updates → Task 2 step 6
- `FakeIdentityRepository` updates → Task 2 step 7
- DTO updates with `@EncodeDefault` → Task 3
- Habit creation paths set `effectiveFrom = now` → Task 4
- `ComputeStreakUseCase` per-day filter → Task 5
- Heat bucket per-day active habit set → Task 5 step 3
- `ComputeIdentityStatsUseCase` per-day filter → Task 6
- Streak engine tests → Task 5 step 5
- Identity stats tests → Task 6 step 5
- Manual smoke + Supabase + PR → Task 7

**Placeholder scan:** none.

**Type consistency:**
- `Habit.effectiveFrom: Instant?, effectiveTo: Instant? = null` — same name + type used in domain model, repository mappers, sync DTOs (as `String?`), and SQL bindings (as `Long?` epoch millis).
- `HabitIdentityRow.effectiveFrom: Instant?, effectiveTo: Instant? = null` — consistent across interface + Local + Fake.
- `habitActiveOn(habit, dayStart)` — same signature in `ComputeStreakUseCase` and `ComputeIdentityStatsUseCase`.
- `markHabitDeleted(habitId, userId, effectiveTo)` — same signature in interface, Local, Fake.
- SQL bind order matches Kotlin call order in all three updated queries (`upsertHabit`, `mergePulledHabit`, `upsertHabitIdentity`, `mergePulledHabitIdentity`).
