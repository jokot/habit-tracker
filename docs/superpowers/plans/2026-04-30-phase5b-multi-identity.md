# Phase 5b: Multi-Identity Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace single-identity onboarding with a multi-identity model. Persist user→identity and habit→identity associations across local SQLDelight + Supabase. Surface identities on Home (strip) and You hub (card). No management screens — those ship in 5c.

**Architecture:** Two new SQLDelight tables (`LocalUserIdentity`, `LocalHabitIdentity`) + matching Supabase tables with RLS. Extend existing `IdentityRepository` interface. New + replaced use cases. `SyncEngine` push/pull extended. Onboarding step 1 becomes multi-select; step 2 shows union with overlap badge. Home + You hub render read-only identity surfaces.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Supabase Postgrest, Compose Material 3, Robolectric for Android tests, kotlin.test for common tests.

**Spec:** `docs/superpowers/specs/2026-04-30-phase5b-multi-identity-design.md`

**Worktree:** `.worktrees/phase5b-multi-identity`
**Branch:** `feature/phase5b-multi-identity` (already checked out)

---

## File Structure

### Created

| File | Responsibility |
|---|---|
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/TemplateWithIdentities.kt` | Onboarding step-2 row model |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserIdentitiesUseCase.kt` | Replace user's identity set |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetUserIdentitiesUseCase.kt` | Flow of user's identities |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentitiesUseCase.kt` | Union templates across selected identities |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCase.kt` | Insert HabitIdentity rows post-habit-creation |
| `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt` | In-memory test double |
| `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserIdentitiesUseCaseTest.kt` | |
| `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCaseTest.kt` | |
| `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentitiesUseCaseTest.kt` | |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityChip.kt` | Pill chip for Home strip |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityStrip.kt` | "I AM" + chips row |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHubCard.kt` | You hub stacked-avatar card |
| `supabase/migrations/20260430000000_user_identities_habit_identities.sql` | Postgres tables + RLS |

### Modified

| File | What changes |
|---|---|
| `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` | Add 2 tables + queries |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt` | Extend interface w/ multi-identity methods |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt` | Implement new methods |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SupabaseSyncClient.kt` | Add 4 sync methods |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt` | Implement 4 sync methods + DTOs |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncEngine.kt` | Wire push/pull |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SyncWatermarkStore.kt` | Add `USER_IDENTITIES`, `HABIT_IDENTITIES` enum entries |
| `mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/FakeSupabaseSyncClient.kt` | Implement 4 new methods |
| `mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/SyncEngineTest.kt` | Cover identity push/pull |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt` | Return `Map<String, String>` |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentityUseCase.kt` | DELETE this file |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` | Wire repo + use cases |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingViewModel.kt` | Multi-select state + new finish sequence |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt` | Multi-select toggle + overlap badge |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt` | Expose `userIdentities` flow |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt` | Render `IdentityStrip` |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubViewModel.kt` | Expose `userIdentities` flow |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt` | Render `IdentityHubCard` |

---

## Conventions

- **Working dir for all bash:** `/Users/jokot/dev/habit-tracker/.worktrees/phase5b-multi-identity` (the worktree). Implementer must `cd` there or use absolute paths.
- **Build commands:** `rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid` for shared module; `rtk ./gradlew :mobile:androidApp:compileDebugKotlin` for app module; `rtk ./gradlew :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest` for tests; `rtk ./gradlew :mobile:androidApp:assembleDebug` for full APK.
- **Commit per task** — keep history clean, atomic per concern.
- **Test pattern:** common-module tests use Fakes (no in-memory SQLDelight driver in this codebase). Android-module tests use Robolectric.
- **Imports:** plan shows `import` lines only when adding a NEW symbol that isn't already imported in the file. When modifying existing code, assume the engineer adds imports as needed.

---

## Tasks

### Task 1: SQLDelight schema — new tables + queries

**Files:**
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`

- [ ] **Step 1: Add the two new CREATE TABLE statements after `LocalIdentity` (around line 41)**

Insert this block immediately after the `CREATE TABLE IF NOT EXISTS LocalIdentity (...);` block and before `CREATE TABLE IF NOT EXISTS LocalWantActivity`:

```sql
CREATE TABLE IF NOT EXISTS LocalUserIdentity (
    userId TEXT NOT NULL,
    identityId TEXT NOT NULL,
    addedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    PRIMARY KEY (userId, identityId)
);

CREATE TABLE IF NOT EXISTS LocalHabitIdentity (
    habitId TEXT NOT NULL,
    identityId TEXT NOT NULL,
    addedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    PRIMARY KEY (habitId, identityId)
);

CREATE INDEX IF NOT EXISTS idx_user_identity_user ON LocalUserIdentity(userId);
CREATE INDEX IF NOT EXISTS idx_habit_identity_habit ON LocalHabitIdentity(habitId);
CREATE INDEX IF NOT EXISTS idx_habit_identity_identity ON LocalHabitIdentity(identityId);
```

- [ ] **Step 2: Add LocalUserIdentity queries at the end of the file**

Append after the existing `mergePulledWantActivity:` block:

```sql
-- LocalUserIdentity queries
upsertUserIdentity:
INSERT OR REPLACE INTO LocalUserIdentity (userId, identityId, addedAt, syncedAt)
VALUES (?, ?, ?, NULL);

mergePulledUserIdentity:
INSERT OR REPLACE INTO LocalUserIdentity (userId, identityId, addedAt, syncedAt)
VALUES (?, ?, ?, ?);

getUserIdentities:
SELECT * FROM LocalUserIdentity WHERE userId = ? ORDER BY addedAt ASC;

deleteUserIdentity:
DELETE FROM LocalUserIdentity WHERE userId = ? AND identityId = ?;

deleteAllUserIdentitiesForUser:
DELETE FROM LocalUserIdentity WHERE userId = ?;

getUnsyncedUserIdentities:
SELECT * FROM LocalUserIdentity WHERE userId = ? AND syncedAt IS NULL;

markUserIdentitySynced:
UPDATE LocalUserIdentity SET syncedAt = ? WHERE userId = ? AND identityId = ?;

-- LocalHabitIdentity queries
upsertHabitIdentity:
INSERT OR REPLACE INTO LocalHabitIdentity (habitId, identityId, addedAt, syncedAt)
VALUES (?, ?, ?, NULL);

mergePulledHabitIdentity:
INSERT OR REPLACE INTO LocalHabitIdentity (habitId, identityId, addedAt, syncedAt)
VALUES (?, ?, ?, ?);

getHabitIdentities:
SELECT * FROM LocalHabitIdentity WHERE habitId = ?;

deleteHabitIdentitiesForHabit:
DELETE FROM LocalHabitIdentity WHERE habitId = ?;

clearHabitIdentitiesForUser:
DELETE FROM LocalHabitIdentity
WHERE habitId IN (SELECT id FROM LocalHabit WHERE userId = ?);

getUnsyncedHabitIdentitiesForUser:
SELECT li.*
FROM LocalHabitIdentity li
INNER JOIN LocalHabit h ON h.id = li.habitId
WHERE h.userId = ? AND li.syncedAt IS NULL;

markHabitIdentitySynced:
UPDATE LocalHabitIdentity SET syncedAt = ? WHERE habitId = ? AND identityId = ?;

getHabitsForIdentity:
SELECT h.*
FROM LocalHabit h
INNER JOIN LocalHabitIdentity li ON li.habitId = h.id
WHERE h.userId = ? AND li.identityId = ?;
```

- [ ] **Step 3: Build to verify SQL parses + generated bindings compile**

Run: `cd /Users/jokot/dev/habit-tracker/.worktrees/phase5b-multi-identity && rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`. If SQL syntax is wrong, SQLDelight will fail at code-generation step with a clear error.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq
rtk git commit -m "feat(db): add LocalUserIdentity + LocalHabitIdentity tables and queries"
```

---

### Task 2: Extend IdentityRepository interface + impl + Fake

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt`

- [ ] **Step 1: Extend the interface**

Replace the entire contents of `IdentityRepository.kt`:

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface IdentityRepository {
    // Seed reference (existing)
    suspend fun getAllIdentities(): List<Identity>
    suspend fun upsertIdentities(identities: List<Identity>)

    // User → identities (new)
    fun observeUserIdentities(userId: String): Flow<List<Identity>>
    suspend fun setUserIdentities(userId: String, identityIds: Set<String>)
    suspend fun clearUserIdentitiesForUser(userId: String)
    suspend fun getUnsyncedUserIdentitiesFor(userId: String): List<UserIdentityRow>
    suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant)
    suspend fun mergePulledUserIdentity(row: UserIdentityRow)

    // Habit → identities (new)
    suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>)
    suspend fun clearHabitIdentitiesForUser(userId: String)
    suspend fun getUnsyncedHabitIdentitiesFor(userId: String): List<HabitIdentityRow>
    suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant)
    suspend fun mergePulledHabitIdentity(row: HabitIdentityRow)
    fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>>
}

data class UserIdentityRow(
    val userId: String,
    val identityId: String,
    val addedAt: Instant,
    val syncedAt: Instant?,
)

data class HabitIdentityRow(
    val habitId: String,
    val identityId: String,
    val addedAt: Instant,
    val syncedAt: Instant?,
)
```

- [ ] **Step 2: Implement the new methods in LocalIdentityRepository**

Replace the entire contents of `LocalIdentityRepository.kt`:

```kotlin
package com.habittracker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalHabit
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class LocalIdentityRepository(
    private val db: HabitTrackerDatabase,
) : IdentityRepository {

    private val q get() = db.habitTrackerDatabaseQueries

    // ── seed ─────────────────────────────────────────────────────────────

    override suspend fun getAllIdentities(): List<Identity> =
        q.getAllIdentities().executeAsList().map {
            Identity(id = it.id, name = it.name, description = it.description, icon = it.icon)
        }

    override suspend fun upsertIdentities(identities: List<Identity>) {
        identities.forEach {
            q.upsertIdentity(id = it.id, name = it.name, description = it.description, icon = it.icon)
        }
    }

    // ── user identities ──────────────────────────────────────────────────

    override fun observeUserIdentities(userId: String): Flow<List<Identity>> {
        val userIdsFlow = q.getUserIdentities(userId).asFlow().mapToList(Dispatchers.Default)
        val seedFlow = q.getAllIdentities().asFlow().mapToList(Dispatchers.Default)
        return combine(userIdsFlow, seedFlow) { userIds, seeds ->
            val seedMap = seeds.associate { it.id to Identity(it.id, it.name, it.description, it.icon) }
            userIds.mapNotNull { seedMap[it.identityId] }
        }
    }

    override suspend fun setUserIdentities(userId: String, identityIds: Set<String>) {
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = q.getUserIdentities(userId).executeAsList().map { it.identityId }.toSet()
        val toAdd = identityIds - existing
        val toRemove = existing - identityIds
        toRemove.forEach { q.deleteUserIdentity(userId = userId, identityId = it) }
        toAdd.forEach { q.upsertUserIdentity(userId = userId, identityId = it, addedAt = now) }
    }

    override suspend fun clearUserIdentitiesForUser(userId: String) {
        q.deleteAllUserIdentitiesForUser(userId)
    }

    override suspend fun getUnsyncedUserIdentitiesFor(userId: String): List<UserIdentityRow> =
        q.getUnsyncedUserIdentities(userId).executeAsList().map {
            UserIdentityRow(
                userId = it.userId,
                identityId = it.identityId,
                addedAt = Instant.fromEpochMilliseconds(it.addedAt),
                syncedAt = it.syncedAt?.let(Instant::fromEpochMilliseconds),
            )
        }

    override suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant) {
        q.markUserIdentitySynced(syncedAt.toEpochMilliseconds(), userId, identityId)
    }

    override suspend fun mergePulledUserIdentity(row: UserIdentityRow) {
        q.mergePulledUserIdentity(
            userId = row.userId,
            identityId = row.identityId,
            addedAt = row.addedAt.toEpochMilliseconds(),
            syncedAt = row.syncedAt?.toEpochMilliseconds(),
        )
    }

    // ── habit identities ─────────────────────────────────────────────────

    override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {
        val now = Clock.System.now().toEpochMilliseconds()
        identityIds.forEach { q.upsertHabitIdentity(habitId = habitId, identityId = it, addedAt = now) }
    }

    override suspend fun clearHabitIdentitiesForUser(userId: String) {
        q.clearHabitIdentitiesForUser(userId)
    }

    override suspend fun getUnsyncedHabitIdentitiesFor(userId: String): List<HabitIdentityRow> =
        q.getUnsyncedHabitIdentitiesForUser(userId).executeAsList().map {
            HabitIdentityRow(
                habitId = it.habitId,
                identityId = it.identityId,
                addedAt = Instant.fromEpochMilliseconds(it.addedAt),
                syncedAt = it.syncedAt?.let(Instant::fromEpochMilliseconds),
            )
        }

    override suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant) {
        q.markHabitIdentitySynced(syncedAt.toEpochMilliseconds(), habitId, identityId)
    }

    override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) {
        q.mergePulledHabitIdentity(
            habitId = row.habitId,
            identityId = row.identityId,
            addedAt = row.addedAt.toEpochMilliseconds(),
            syncedAt = row.syncedAt?.toEpochMilliseconds(),
        )
    }

    override fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>> =
        q.getHabitsForIdentity(userId, identityId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }
}

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
)
```

- [ ] **Step 3: Create FakeIdentityRepository for tests**

Create `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt`:

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeIdentityRepository(
    private val seed: List<Identity> = emptyList(),
) : IdentityRepository {

    private val seedFlow = MutableStateFlow(seed)
    private val userIdentities = MutableStateFlow<List<UserIdentityRow>>(emptyList())
    private val habitIdentities = MutableStateFlow<List<HabitIdentityRow>>(emptyList())
    private val habits = MutableStateFlow<List<Habit>>(emptyList())

    fun seedHabit(habit: Habit) {
        habits.value = habits.value.filterNot { it.id == habit.id } + habit
    }

    val userIdentitiesSnapshot: List<UserIdentityRow> get() = userIdentities.value
    val habitIdentitiesSnapshot: List<HabitIdentityRow> get() = habitIdentities.value

    override suspend fun getAllIdentities(): List<Identity> = seedFlow.value

    override suspend fun upsertIdentities(identities: List<Identity>) {
        seedFlow.value = identities
    }

    override fun observeUserIdentities(userId: String): Flow<List<Identity>> =
        combine(userIdentities, seedFlow) { rows, seeds ->
            val map = seeds.associateBy { it.id }
            rows.filter { it.userId == userId }
                .sortedBy { it.addedAt }
                .mapNotNull { map[it.identityId] }
        }

    override suspend fun setUserIdentities(userId: String, identityIds: Set<String>) {
        val now = Clock.System.now()
        val existing = userIdentities.value.filter { it.userId == userId }
        val keep = existing.filter { it.identityId in identityIds }
        val add = (identityIds - keep.map { it.identityId }.toSet()).map {
            UserIdentityRow(userId = userId, identityId = it, addedAt = now, syncedAt = null)
        }
        val others = userIdentities.value.filter { it.userId != userId }
        userIdentities.value = others + keep + add
    }

    override suspend fun clearUserIdentitiesForUser(userId: String) {
        userIdentities.value = userIdentities.value.filter { it.userId != userId }
    }

    override suspend fun getUnsyncedUserIdentitiesFor(userId: String): List<UserIdentityRow> =
        userIdentities.value.filter { it.userId == userId && it.syncedAt == null }

    override suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant) {
        userIdentities.value = userIdentities.value.map {
            if (it.userId == userId && it.identityId == identityId) it.copy(syncedAt = syncedAt) else it
        }
    }

    override suspend fun mergePulledUserIdentity(row: UserIdentityRow) {
        userIdentities.value = userIdentities.value.filterNot { it.userId == row.userId && it.identityId == row.identityId } + row
    }

    override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {
        val now = Clock.System.now()
        val keep = habitIdentities.value.filterNot { it.habitId == habitId && it.identityId !in identityIds }
        val existingIds = keep.filter { it.habitId == habitId }.map { it.identityId }.toSet()
        val add = (identityIds - existingIds).map {
            HabitIdentityRow(habitId = habitId, identityId = it, addedAt = now, syncedAt = null)
        }
        habitIdentities.value = keep + add
    }

    override suspend fun clearHabitIdentitiesForUser(userId: String) {
        val userHabitIds = habits.value.filter { it.userId == userId }.map { it.id }.toSet()
        habitIdentities.value = habitIdentities.value.filterNot { it.habitId in userHabitIds }
    }

    override suspend fun getUnsyncedHabitIdentitiesFor(userId: String): List<HabitIdentityRow> {
        val userHabitIds = habits.value.filter { it.userId == userId }.map { it.id }.toSet()
        return habitIdentities.value.filter { it.habitId in userHabitIds && it.syncedAt == null }
    }

    override suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant) {
        habitIdentities.value = habitIdentities.value.map {
            if (it.habitId == habitId && it.identityId == identityId) it.copy(syncedAt = syncedAt) else it
        }
    }

    override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) {
        habitIdentities.value = habitIdentities.value.filterNot { it.habitId == row.habitId && it.identityId == row.identityId } + row
    }

    override fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>> =
        combine(habits, habitIdentities) { hs, his ->
            val habitIds = his.filter { it.identityId == identityId }.map { it.habitId }.toSet()
            hs.filter { it.userId == userId && it.id in habitIds }
        }
}
```

- [ ] **Step 4: Build (shared + tests should compile)**

```bash
rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid :mobile:shared:compileTestKotlinJvm 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt
rtk git commit -m "feat(repo): IdentityRepository multi-identity surface + impl + fake"
```

---

### Task 3: SetupUserIdentitiesUseCase

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserIdentitiesUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserIdentitiesUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

Create the test file:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SetupUserIdentitiesUseCaseTest {

    private val seed = listOf(
        Identity("a", "Reader", "", ""),
        Identity("b", "Athlete", "", ""),
        Identity("c", "Calm", "", ""),
    )

    @Test
    fun `replaces user identity set`() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = SetupUserIdentitiesUseCase(repo)

        sut.execute("user-1", setOf("a", "b")).getOrThrow()

        val rows = repo.userIdentitiesSnapshot.filter { it.userId == "user-1" }.map { it.identityId }.toSet()
        assertEquals(setOf("a", "b"), rows)
    }

    @Test
    fun `replace removes prior identity not in new set`() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = SetupUserIdentitiesUseCase(repo)
        sut.execute("user-1", setOf("a", "b")).getOrThrow()

        sut.execute("user-1", setOf("b", "c")).getOrThrow()

        val rows = repo.userIdentitiesSnapshot.filter { it.userId == "user-1" }.map { it.identityId }.toSet()
        assertEquals(setOf("b", "c"), rows)
    }

    @Test
    fun `empty set clears user`() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = SetupUserIdentitiesUseCase(repo)
        sut.execute("user-1", setOf("a")).getOrThrow()

        sut.execute("user-1", emptySet()).getOrThrow()

        val rows = repo.userIdentitiesSnapshot.filter { it.userId == "user-1" }
        assertEquals(0, rows.size)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (use case not defined)**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*SetupUserIdentitiesUseCaseTest*" 2>&1 | tail -10
```
Expected: compile failure on `SetupUserIdentitiesUseCase`.

- [ ] **Step 3: Implement the use case**

Create `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserIdentitiesUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository

class SetupUserIdentitiesUseCase(private val identityRepository: IdentityRepository) {
    suspend fun execute(userId: String, identityIds: Set<String>): Result<Unit> =
        runCatching {
            identityRepository.setUserIdentities(userId, identityIds)
        }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*SetupUserIdentitiesUseCaseTest*" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`, 3 tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserIdentitiesUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/SetupUserIdentitiesUseCaseTest.kt
rtk git commit -m "feat(usecase): SetupUserIdentitiesUseCase"
```

---

### Task 4: GetUserIdentitiesUseCase

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetUserIdentitiesUseCase.kt`

This is a thin Flow exposer — no separate test needed (covered transitively by HomeViewModel/YouHubViewModel tests in tasks 14/15).

- [ ] **Step 1: Implement**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow

class GetUserIdentitiesUseCase(private val identityRepository: IdentityRepository) {
    fun execute(userId: String): Flow<List<Identity>> =
        identityRepository.observeUserIdentities(userId)
}
```

- [ ] **Step 2: Build**

```bash
rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetUserIdentitiesUseCase.kt
rtk git commit -m "feat(usecase): GetUserIdentitiesUseCase"
```

---

### Task 5: TemplateWithIdentities + GetHabitTemplatesForIdentitiesUseCase

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/TemplateWithIdentities.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentitiesUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentitiesUseCaseTest.kt`
- Delete: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentityUseCase.kt`

- [ ] **Step 1: Add the model class**

Create `TemplateWithIdentities.kt`:

```kotlin
package com.habittracker.domain.model

data class TemplateWithIdentities(
    val template: HabitTemplate,
    val recommendedBy: Set<Identity>,
)
```

- [ ] **Step 2: Write the failing test**

Create `GetHabitTemplatesForIdentitiesUseCaseTest.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetHabitTemplatesForIdentitiesUseCaseTest {

    private val sut = GetHabitTemplatesForIdentitiesUseCase()

    @Test
    fun `empty input returns empty`() {
        assertEquals(emptyList(), sut.execute(emptySet()))
    }

    @Test
    fun `single identity returns its templates`() {
        val firstIdentityId = SeedData.identityHabitMap.keys.first()
        val expectedSize = SeedData.identityHabitMap[firstIdentityId]!!.size

        val out = sut.execute(setOf(firstIdentityId))

        assertEquals(expectedSize, out.size)
        out.forEach { row ->
            assertEquals(setOf(firstIdentityId), row.recommendedBy.map { it.id }.toSet())
        }
    }

    @Test
    fun `union dedupes shared template across two identities`() {
        // Find any two identities sharing at least one template; if seed has none, this assert
        // will be skipped via assertTrue gate. Most realistic seeds have overlap.
        val ids = SeedData.identityHabitMap.keys.toList()
        val pair = ids.firstNotNullOfOrNull { a ->
            ids.firstOrNull { b -> b != a && SeedData.identityHabitMap[a]!!.intersect(SeedData.identityHabitMap[b]!!.toSet()).isNotEmpty() }
                ?.let { a to it }
        }
        assertTrue(pair != null, "Seed has no overlapping templates between identities — extend seed or rewrite test.")
        val (a, b) = pair!!
        val sharedTemplateId = SeedData.identityHabitMap[a]!!.intersect(SeedData.identityHabitMap[b]!!.toSet()).first()

        val out = sut.execute(setOf(a, b))

        val shared = out.first { it.template.id == sharedTemplateId }
        assertEquals(setOf(a, b), shared.recommendedBy.map { it.id }.toSet())
        // No duplicate rows for the shared template
        assertEquals(1, out.count { it.template.id == sharedTemplateId })
    }
}
```

- [ ] **Step 3: Run test — expect compile failure (use case not defined)**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*GetHabitTemplatesForIdentitiesUseCaseTest*" 2>&1 | tail -10
```
Expected: compile failure.

- [ ] **Step 4: Implement the use case**

Create `GetHabitTemplatesForIdentitiesUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.TemplateWithIdentities

class GetHabitTemplatesForIdentitiesUseCase {
    fun execute(identityIds: Set<String>): List<TemplateWithIdentities> {
        if (identityIds.isEmpty()) return emptyList()
        val identityMap = SeedData.identities.associateBy { it.id }
        // templateId -> set of identities recommending it
        val templateToIdentities = mutableMapOf<String, MutableSet<Identity>>()
        // Preserve insertion order using LinkedHashMap-backed mutableMap
        identityIds.forEach { idId ->
            val identity = identityMap[idId] ?: return@forEach
            SeedData.identityHabitMap[idId].orEmpty().forEach { templateId ->
                templateToIdentities.getOrPut(templateId) { mutableSetOf() }.add(identity)
            }
        }
        return templateToIdentities.mapNotNull { (templateId, identities) ->
            val template = SeedData.habitTemplates[templateId] ?: return@mapNotNull null
            TemplateWithIdentities(template = template, recommendedBy = identities.toSet())
        }
    }
}
```

- [ ] **Step 5: Run test — expect PASS**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*GetHabitTemplatesForIdentitiesUseCaseTest*" 2>&1 | tail -10
```
Expected: PASS.

- [ ] **Step 6: Delete the obsolete single-identity use case**

```bash
rm mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentityUseCase.kt
```

- [ ] **Step 7: Build (the now-deleted use case may have one caller — onboarding ViewModel — which is rewritten in Task 13. Build will fail on AndroidApp module. That's acceptable for this commit; it's fixed in Task 13.)**

Verify the SHARED module compiles cleanly:

```bash
rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid :mobile:shared:testDebugUnitTest 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`. (App module is allowed to be temporarily broken until Task 13.)

- [ ] **Step 8: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/TemplateWithIdentities.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentitiesUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentitiesUseCaseTest.kt
rtk git rm mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentityUseCase.kt
rtk git commit -m "feat(usecase): GetHabitTemplatesForIdentitiesUseCase replaces single-identity variant"
```

---

### Task 6: Extend SetupUserHabitsUseCase return type to Map<templateId, habitId>

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt`

The current signature returns `Result<Unit>`. We need `Result<Map<String, String>>` (templateId → habitId) so the link step can use the assigned habit ids.

- [ ] **Step 1: Replace the use case**

Replace `SetupUserHabitsUseCase.kt` contents:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitTemplate
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SetupUserHabitsUseCase(private val habitRepository: HabitRepository) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(userId: String, templates: List<HabitTemplate>): Result<Map<String, String>> =
        runCatching {
            val now = Clock.System.now()
            val mapping = mutableMapOf<String, String>()
            templates.forEach { template ->
                val habitId = Uuid.random().toString()
                habitRepository.saveHabit(
                    Habit(
                        id = habitId,
                        userId = userId,
                        templateId = template.id,
                        name = template.name,
                        unit = template.unit,
                        thresholdPerPoint = template.defaultThreshold,
                        dailyTarget = template.defaultDailyTarget,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                mapping[template.id] = habitId
            }
            mapping.toMap()
        }
}
```

- [ ] **Step 2: Build (shared module). Caller — OnboardingViewModel — is updated in Task 13.**

```bash
rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid :mobile:shared:testDebugUnitTest 2>&1 | tail -10
```
Expected: shared module + tests pass. (App module still broken until Task 13.)

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt
rtk git commit -m "refactor(usecase): SetupUserHabitsUseCase returns templateId→habitId map"
```

---

### Task 7: LinkOnboardingHabitsToIdentitiesUseCase

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

Create `LinkOnboardingHabitsToIdentitiesUseCaseTest.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkOnboardingHabitsToIdentitiesUseCaseTest {

    private val seed = listOf(
        Identity("reader", "Reader", "", ""),
        Identity("athlete", "Athlete", "", ""),
        Identity("calm", "Calm", "", ""),
    )

    // Stub the seed map. The real SeedData.identityHabitMap is fixed at module level.
    // Test relies on the actual map values; pick any two seeded identities sharing a template.
    @Test
    fun `habit template recommended by 2 selected identities yields 2 join rows`() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = LinkOnboardingHabitsToIdentitiesUseCase(repo)
        // Arrange a case where templateA is recommended by identities {x,y} (selected).
        // Stub via setupFromMap below.
        val templateIdToHabitId = mapOf("tpl-1" to "habit-1")
        val recommendedBy = mapOf("tpl-1" to setOf("reader", "athlete"))

        sut.executeWithMap(
            userId = "user-1",
            templateIdToHabitId = templateIdToHabitId,
            selectedIdentityIds = setOf("reader", "athlete"),
            templateRecommendedBy = recommendedBy,
        ).getOrThrow()

        val rows = repo.habitIdentitiesSnapshot.filter { it.habitId == "habit-1" }.map { it.identityId }.toSet()
        assertEquals(setOf("reader", "athlete"), rows)
    }

    @Test
    fun `habit template recommended by 2 identities only 1 selected yields 1 row`() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = LinkOnboardingHabitsToIdentitiesUseCase(repo)
        val recommendedBy = mapOf("tpl-1" to setOf("reader", "athlete"))

        sut.executeWithMap(
            userId = "user-1",
            templateIdToHabitId = mapOf("tpl-1" to "habit-1"),
            selectedIdentityIds = setOf("reader"),
            templateRecommendedBy = recommendedBy,
        ).getOrThrow()

        val rows = repo.habitIdentitiesSnapshot.filter { it.habitId == "habit-1" }.map { it.identityId }.toSet()
        assertEquals(setOf("reader"), rows)
    }

    @Test
    fun `habit not recommended by any selected identity yields no rows`() = runTest {
        val repo = FakeIdentityRepository(seed = seed)
        val sut = LinkOnboardingHabitsToIdentitiesUseCase(repo)
        val recommendedBy = mapOf("tpl-1" to setOf("calm"))

        sut.executeWithMap(
            userId = "user-1",
            templateIdToHabitId = mapOf("tpl-1" to "habit-1"),
            selectedIdentityIds = setOf("reader", "athlete"),
            templateRecommendedBy = recommendedBy,
        ).getOrThrow()

        assertEquals(0, repo.habitIdentitiesSnapshot.size)
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*LinkOnboardingHabitsToIdentitiesUseCaseTest*" 2>&1 | tail -10
```
Expected: compile failure.

- [ ] **Step 3: Implement the use case**

Create `LinkOnboardingHabitsToIdentitiesUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import com.habittracker.data.repository.IdentityRepository

class LinkOnboardingHabitsToIdentitiesUseCase(
    private val identityRepository: IdentityRepository,
) {
    /**
     * Production entry: uses real SeedData.identityHabitMap to look up which identities
     * recommend each template.
     */
    suspend fun execute(
        userId: String,
        templateIdToHabitId: Map<String, String>,
        selectedIdentityIds: Set<String>,
    ): Result<Unit> = executeWithMap(
        userId = userId,
        templateIdToHabitId = templateIdToHabitId,
        selectedIdentityIds = selectedIdentityIds,
        templateRecommendedBy = buildRecommendationMap(templateIdToHabitId.keys),
    )

    /**
     * Test entry: lets tests inject the recommendation map directly.
     */
    suspend fun executeWithMap(
        userId: String,
        templateIdToHabitId: Map<String, String>,
        selectedIdentityIds: Set<String>,
        templateRecommendedBy: Map<String, Set<String>>,
    ): Result<Unit> = runCatching {
        // userId arg reserved for future per-user scoping; currently unused — link rows live
        // under habit ids which already carry user scope via LocalHabit.userId.
        @Suppress("UNUSED_PARAMETER") val _u = userId
        templateIdToHabitId.forEach { (templateId, habitId) ->
            val recommenders = templateRecommendedBy[templateId].orEmpty()
            val applied = recommenders.intersect(selectedIdentityIds)
            if (applied.isNotEmpty()) {
                identityRepository.linkHabitToIdentities(habitId, applied)
            }
        }
    }

    private fun buildRecommendationMap(templateIds: Set<String>): Map<String, Set<String>> {
        val out = mutableMapOf<String, MutableSet<String>>()
        SeedData.identityHabitMap.forEach { (identityId, recommendedTemplates) ->
            recommendedTemplates.forEach { templateId ->
                if (templateId in templateIds) {
                    out.getOrPut(templateId) { mutableSetOf() }.add(identityId)
                }
            }
        }
        return out
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "*LinkOnboardingHabitsToIdentitiesUseCaseTest*" 2>&1 | tail -10
```
Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCaseTest.kt
rtk git commit -m "feat(usecase): LinkOnboardingHabitsToIdentitiesUseCase"
```

---

### Task 8: Extend SupabaseSyncClient interface + Fake

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SupabaseSyncClient.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/FakeSupabaseSyncClient.kt`

- [ ] **Step 1: Extend the interface**

Replace `SupabaseSyncClient.kt`:

```kotlin
package com.habittracker.data.sync

import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog

interface SupabaseSyncClient {
    suspend fun upsertHabit(row: Habit)
    suspend fun upsertWantActivity(row: WantActivity, ownerUserId: String)
    suspend fun upsertHabitLog(row: HabitLog)
    suspend fun upsertWantLog(row: WantLog)
    suspend fun upsertUserIdentity(row: UserIdentityRow)
    suspend fun upsertHabitIdentity(row: HabitIdentityRow)

    suspend fun fetchHabitsSince(userId: String, sinceMs: Long): List<Habit>
    suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity>
    suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog>
    suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog>
    suspend fun fetchUserIdentitiesSince(userId: String, sinceMs: Long): List<UserIdentityRow>
    suspend fun fetchHabitIdentitiesSince(userId: String, sinceMs: Long): List<HabitIdentityRow>
}
```

- [ ] **Step 2: Extend the fake**

In `FakeSupabaseSyncClient.kt`, add fields + 6 new method overrides. Append the following inside the class (and include the imports at the top):

At the top of the file, ADD imports:
```kotlin
import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.UserIdentityRow
```

Add the following members after the existing ones (find the closing brace of the class and add before it):

```kotlin
    val userIdentities = mutableListOf<UserIdentityRow>()
    val habitIdentities = mutableListOf<HabitIdentityRow>()

    override suspend fun upsertUserIdentity(row: UserIdentityRow) {
        failIfNeeded()
        userIdentities.removeAll { it.userId == row.userId && it.identityId == row.identityId }
        userIdentities.add(row)
    }

    override suspend fun upsertHabitIdentity(row: HabitIdentityRow) {
        failIfNeeded()
        habitIdentities.removeAll { it.habitId == row.habitId && it.identityId == row.identityId }
        habitIdentities.add(row)
    }

    override suspend fun fetchUserIdentitiesSince(userId: String, sinceMs: Long): List<UserIdentityRow> {
        failIfNeeded()
        return userIdentities.filter { it.userId == userId && (it.syncedAt?.toEpochMilliseconds() ?: it.addedAt.toEpochMilliseconds()) > sinceMs }
    }

    override suspend fun fetchHabitIdentitiesSince(userId: String, sinceMs: Long): List<HabitIdentityRow> {
        failIfNeeded()
        // userId scoping handled at server-side via RLS; the fake stores all rows so we filter by sinceMs only.
        @Suppress("UNUSED_PARAMETER") val _u = userId
        return habitIdentities.filter { (it.syncedAt?.toEpochMilliseconds() ?: it.addedAt.toEpochMilliseconds()) > sinceMs }
    }
```

- [ ] **Step 3: Build (shared + tests). Postgrest impl is updated in Task 9 — until then it has compile errors. Verify only the surfaces touched here:**

```bash
rtk ./gradlew :mobile:shared:compileTestKotlinJvm 2>&1 | tail -15
```
Expected: errors only in `PostgrestSupabaseSyncClient.kt` (missing overrides). Acceptable for this commit.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SupabaseSyncClient.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/FakeSupabaseSyncClient.kt
rtk git commit -m "feat(sync): SupabaseSyncClient surface for user/habit identities"
```

---

### Task 9: PostgrestSupabaseSyncClient — implement the 6 new methods

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt`

- [ ] **Step 1: Add the implementations + DTOs**

Open the file. At the top, add imports:

```kotlin
import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.UserIdentityRow
```

Inside the class, add 6 method overrides after `fetchWantLogsSince`:

```kotlin
    override suspend fun upsertUserIdentity(row: UserIdentityRow) {
        supabase.postgrest.from("user_identities").upsert(row.toDto())
    }

    override suspend fun upsertHabitIdentity(row: HabitIdentityRow) {
        supabase.postgrest.from("habit_identities").upsert(row.toDto())
    }

    override suspend fun fetchUserIdentitiesSince(userId: String, sinceMs: Long): List<UserIdentityRow> =
        supabase.postgrest.from("user_identities")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("added_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("added_at", Order.ASCENDING)
            }
            .decodeList<UserIdentityDto>()
            .map { it.toDomain() }

    override suspend fun fetchHabitIdentitiesSince(userId: String, sinceMs: Long): List<HabitIdentityRow> =
        // RLS filters server-side by habit ownership; client passes userId only for parity
        supabase.postgrest.from("habit_identities")
            .select {
                filter {
                    gt("added_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("added_at", Order.ASCENDING)
            }
            .decodeList<HabitIdentityDto>()
            .map { it.toDomain() }
            .also { @Suppress("UNUSED_VARIABLE") val _u = userId }
```

At the bottom of the file (after existing DTOs), add the DTO + mapper definitions:

```kotlin
@Serializable
private data class UserIdentityDto(
    @SerialName("user_id") val userId: String,
    @SerialName("identity_id") val identityId: String,
    @SerialName("added_at") val addedAt: String,
)

private fun UserIdentityRow.toDto() = UserIdentityDto(
    userId = userId,
    identityId = identityId,
    addedAt = addedAt.toString(),
)

private fun UserIdentityDto.toDomain() = UserIdentityRow(
    userId = userId,
    identityId = identityId,
    addedAt = Instant.parse(addedAt),
    syncedAt = Instant.parse(addedAt),
)

@Serializable
private data class HabitIdentityDto(
    @SerialName("habit_id") val habitId: String,
    @SerialName("identity_id") val identityId: String,
    @SerialName("added_at") val addedAt: String,
)

private fun HabitIdentityRow.toDto() = HabitIdentityDto(
    habitId = habitId,
    identityId = identityId,
    addedAt = addedAt.toString(),
)

private fun HabitIdentityDto.toDomain() = HabitIdentityRow(
    habitId = habitId,
    identityId = identityId,
    addedAt = Instant.parse(addedAt),
    syncedAt = Instant.parse(addedAt),
)
```

- [ ] **Step 2: Build**

```bash
rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt
rtk git commit -m "feat(sync): Postgrest impl for user/habit identity push+pull"
```

---

### Task 10: SyncEngine wiring + watermarks + tests

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SyncWatermarkStore.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncEngine.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/SyncEngineTest.kt`

- [ ] **Step 1: Add watermark enum entries**

In `SyncWatermarkStore.kt`, replace the enum:

```kotlin
enum class SyncTable(val key: String) {
    HABITS("habits"),
    HABIT_LOGS("habit_logs"),
    WANT_ACTIVITIES("want_activities"),
    WANT_LOGS("want_logs"),
    USER_IDENTITIES("user_identities"),
    HABIT_IDENTITIES("habit_identities"),
}
```

- [ ] **Step 2: Wire SyncEngine constructor + push + pull**

In `SyncEngine.kt`, update the constructor parameters and add identity push/pull. Replace the class declaration line + push/pull bodies:

Find:
```kotlin
class SyncEngine(
    private val habitRepo: HabitRepository,
    private val habitLogRepo: HabitLogRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val wantLogRepo: WantLogRepository,
    private val supabase: SupabaseSyncClient,
    private val watermarks: WatermarkReader,
    private val identity: SyncIdentity,
) {
```

Replace with:
```kotlin
class SyncEngine(
    private val habitRepo: HabitRepository,
    private val habitLogRepo: HabitLogRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val wantLogRepo: WantLogRepository,
    private val identityRepo: IdentityRepository,
    private val supabase: SupabaseSyncClient,
    private val watermarks: WatermarkReader,
    private val identity: SyncIdentity,
) {
```

At the top of the file, add the import:
```kotlin
import com.habittracker.data.repository.IdentityRepository
```

In the existing `push(userId)` method, add at the end (right before `return count`):

```kotlin
        identityRepo.getUnsyncedUserIdentitiesFor(userId).forEach { row ->
            val stamped = row.copy(syncedAt = now)
            supabase.upsertUserIdentity(stamped)
            identityRepo.markUserIdentitySynced(row.userId, row.identityId, now)
            count++
        }
        identityRepo.getUnsyncedHabitIdentitiesFor(userId).forEach { row ->
            val stamped = row.copy(syncedAt = now)
            supabase.upsertHabitIdentity(stamped)
            identityRepo.markHabitIdentitySynced(row.habitId, row.identityId, now)
            count++
        }
```

In the existing `pull(userId)` method, change the return expression:

```kotlin
    private suspend fun pull(userId: String): Int =
        pullHabits(userId) +
            pullWantActivities(userId) +
            pullHabitLogs(userId) +
            pullWantLogs(userId) +
            pullUserIdentities(userId) +
            pullHabitIdentities(userId)
```

Add two private methods at the end of the class:

```kotlin
    private suspend fun pullUserIdentities(userId: String): Int {
        val last = watermarks.get(SyncTable.USER_IDENTITIES)
        val remote = supabase.fetchUserIdentitiesSince(userId, last)
        if (remote.isEmpty()) return 0
        remote.forEach { row -> identityRepo.mergePulledUserIdentity(row) }
        val maxTs = remote.maxOf { it.syncedAt?.toEpochMilliseconds() ?: it.addedAt.toEpochMilliseconds() }
        watermarks.set(SyncTable.USER_IDENTITIES, maxTs)
        return remote.size
    }

    private suspend fun pullHabitIdentities(userId: String): Int {
        val last = watermarks.get(SyncTable.HABIT_IDENTITIES)
        val remote = supabase.fetchHabitIdentitiesSince(userId, last)
        if (remote.isEmpty()) return 0
        remote.forEach { row -> identityRepo.mergePulledHabitIdentity(row) }
        val maxTs = remote.maxOf { it.syncedAt?.toEpochMilliseconds() ?: it.addedAt.toEpochMilliseconds() }
        watermarks.set(SyncTable.HABIT_IDENTITIES, maxTs)
        return remote.size
    }
```

- [ ] **Step 3: Update SyncEngineTest**

Open `SyncEngineTest.kt`. Find every `SyncEngine(` constructor call in the test setup and add `identityRepo = FakeIdentityRepository(),` as a new constructor argument before `supabase = ...`.

Add a new test method at the end of the class:

```kotlin
    @Test
    fun `sync pushes unsynced user and habit identities`() = runTest {
        val identityRepo = com.habittracker.data.repository.FakeIdentityRepository()
        val (engine, supabase) = newEngineWith(identityRepo)
        // Seed a habit so habit identity is owned by user
        engine.testSeedHabit(habit = stubHabit("h1", "user-1"))
        identityRepo.setUserIdentities("user-1", setOf("reader"))
        identityRepo.linkHabitToIdentities("h1", setOf("reader"))

        engine.sync(SyncReason.MANUAL)

        kotlin.test.assertEquals(1, supabase.userIdentities.size)
        kotlin.test.assertEquals(1, supabase.habitIdentities.size)
    }
```

If the test file currently doesn't have a `newEngineWith` helper or `testSeedHabit` extension, look for the existing setup pattern in `SyncEngineTest.kt` (the test already has one — this method should match its style). The implementer should adapt the pattern. If the helper signature differs, follow the in-file convention.

(The test illustrates the intent — the implementer must align it with the existing test fixture style. If unsure, dispatch a clarification rather than guess.)

- [ ] **Step 4: Run tests**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SyncWatermarkStore.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncEngine.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/SyncEngineTest.kt
rtk git commit -m "feat(sync): SyncEngine wires user/habit identity push+pull"
```

---

### Task 11: Supabase migration SQL

**Files:**
- Create: `supabase/migrations/20260430000000_user_identities_habit_identities.sql`

- [ ] **Step 1: Create the migration file**

```sql
-- Phase 5b: multi-identity foundation
-- Two many-to-many join tables connecting users ↔ identities and habits ↔ identities.
-- RLS scopes by ownership.

CREATE TABLE IF NOT EXISTS public.user_identities (
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    identity_id TEXT NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, identity_id)
);

CREATE TABLE IF NOT EXISTS public.habit_identities (
    habit_id UUID NOT NULL REFERENCES public.habits(id) ON DELETE CASCADE,
    identity_id TEXT NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (habit_id, identity_id)
);

CREATE INDEX IF NOT EXISTS idx_user_identities_user ON public.user_identities(user_id);
CREATE INDEX IF NOT EXISTS idx_habit_identities_habit ON public.habit_identities(habit_id);

ALTER TABLE public.user_identities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.habit_identities ENABLE ROW LEVEL SECURITY;

-- user_identities: any operation only on rows owned by current user.
CREATE POLICY "user_identities_select_own"
    ON public.user_identities FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "user_identities_insert_own"
    ON public.user_identities FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "user_identities_update_own"
    ON public.user_identities FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "user_identities_delete_own"
    ON public.user_identities FOR DELETE
    USING (auth.uid() = user_id);

-- habit_identities: scoped via the habits table.
CREATE POLICY "habit_identities_select_own"
    ON public.habit_identities FOR SELECT
    USING (EXISTS (
        SELECT 1 FROM public.habits h
        WHERE h.id = habit_identities.habit_id AND h.user_id = auth.uid()
    ));

CREATE POLICY "habit_identities_insert_own"
    ON public.habit_identities FOR INSERT
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.habits h
        WHERE h.id = habit_identities.habit_id AND h.user_id = auth.uid()
    ));

CREATE POLICY "habit_identities_update_own"
    ON public.habit_identities FOR UPDATE
    USING (EXISTS (
        SELECT 1 FROM public.habits h
        WHERE h.id = habit_identities.habit_id AND h.user_id = auth.uid()
    ));

CREATE POLICY "habit_identities_delete_own"
    ON public.habit_identities FOR DELETE
    USING (EXISTS (
        SELECT 1 FROM public.habits h
        WHERE h.id = habit_identities.habit_id AND h.user_id = auth.uid()
    ));
```

- [ ] **Step 2: Apply locally if Supabase CLI is set up — otherwise skip and document**

```bash
ls supabase/ 2>&1
```

If `supabase/` directory exists with a `config.toml`:
```bash
rtk supabase db push 2>&1 | tail -10
```

If the CLI isn't configured, document in the commit message that the migration must be applied manually via the Supabase dashboard before deploying. (The user has a remote-only setup typically.)

- [ ] **Step 3: Commit**

```bash
rtk git add supabase/migrations/20260430000000_user_identities_habit_identities.sql
rtk git commit -m "feat(supabase): user_identities + habit_identities tables with RLS"
```

---

### Task 12: AppContainer wiring

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`

- [ ] **Step 1: Read the current AppContainer to find the wiring style**

```bash
rtk grep -n "IdentityRepository\|SyncEngine(\|SetupUserHabitsUseCase\|GetHabitTemplatesForIdentity" mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt | head -20
```

Identify the lines where `IdentityRepository` is constructed and where `SyncEngine` is constructed.

- [ ] **Step 2: Update SyncEngine wiring**

The `SyncEngine` constructor now takes `identityRepo`. Find the constructor call in `AppContainer.kt` and add the parameter:

```kotlin
        SyncEngine(
            habitRepo = habitRepository,
            habitLogRepo = habitLogRepository,
            wantActivityRepo = wantActivityRepository,
            wantLogRepo = wantLogRepository,
            identityRepo = identityRepository,
            supabase = ...,
            watermarks = ...,
            identity = ...,
        )
```

Match the existing arg order/style — just add `identityRepo = identityRepository,` in the right spot.

- [ ] **Step 3: Add the new use cases to the container**

After existing use case definitions, add:

```kotlin
    val getUserIdentitiesUseCase by lazy { GetUserIdentitiesUseCase(identityRepository) }
    val setupUserIdentitiesUseCase by lazy { SetupUserIdentitiesUseCase(identityRepository) }
    val getHabitTemplatesForIdentitiesUseCase by lazy { GetHabitTemplatesForIdentitiesUseCase() }
    val linkOnboardingHabitsToIdentitiesUseCase by lazy { LinkOnboardingHabitsToIdentitiesUseCase(identityRepository) }
```

Remove the line that defined the old single-identity use case (`val getHabitTemplatesForIdentityUseCase`).

Add imports:

```kotlin
import com.habittracker.domain.usecase.GetUserIdentitiesUseCase
import com.habittracker.domain.usecase.SetupUserIdentitiesUseCase
import com.habittracker.domain.usecase.GetHabitTemplatesForIdentitiesUseCase
import com.habittracker.domain.usecase.LinkOnboardingHabitsToIdentitiesUseCase
```

Remove `import com.habittracker.domain.usecase.GetHabitTemplatesForIdentityUseCase` if present.

- [ ] **Step 4: Build (full app)**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlin 2>&1 | tail -15
```

If errors point to OnboardingViewModel, that's expected — Task 13 fixes it. If errors are elsewhere in AppContainer, fix imports / wiring inline.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt
rtk git commit -m "feat(container): wire IdentityRepository + multi-identity use cases"
```

---

### Task 13: OnboardingViewModel rewrite

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingViewModel.kt`
- Create: `mobile/androidApp/src/androidUnitTest/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingViewModelMultiIdentityTest.kt` (Robolectric test — only if existing Android tests use Robolectric; if there are no existing onboarding tests, skip the test creation and move on)

- [ ] **Step 1: Rewrite the ViewModel**

Replace `OnboardingViewModel.kt` with:

```kotlin
package com.jktdeveloper.habitto.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jktdeveloper.habitto.AppContainer
import com.habittracker.data.local.SeedData
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.TemplateWithIdentities
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep { IDENTITY, HABITS, WANTS, SYNC }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.IDENTITY,
    val identities: List<Identity> = SeedData.identities,
    val selectedIdentityIds: Set<String> = emptySet(),
    val habitTemplates: List<TemplateWithIdentities> = emptyList(),
    val selectedTemplateIds: Set<String> = emptySet(),
    val wantActivities: List<WantActivity> = SeedData.wantActivities,
    val selectedActivityIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>()
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    fun toggleIdentity(identityId: String) {
        val current = _uiState.value.selectedIdentityIds.toMutableSet()
        if (current.contains(identityId)) current.remove(identityId) else current.add(identityId)
        val newTemplates = container.getHabitTemplatesForIdentitiesUseCase.execute(current)
        val newTemplateIds = newTemplates.map { it.template.id }.toSet()
        val keptSelections = _uiState.value.selectedTemplateIds.intersect(newTemplateIds)
        _uiState.value = _uiState.value.copy(
            selectedIdentityIds = current,
            habitTemplates = newTemplates,
            selectedTemplateIds = keptSelections,
        )
    }

    fun continueFromIdentity() {
        if (_uiState.value.selectedIdentityIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(step = OnboardingStep.HABITS)
    }

    fun toggleHabit(templateId: String) {
        val current = _uiState.value.selectedTemplateIds.toMutableSet()
        if (current.contains(templateId)) current.remove(templateId) else current.add(templateId)
        _uiState.value = _uiState.value.copy(selectedTemplateIds = current)
    }

    fun continueFromHabits() {
        _uiState.value = _uiState.value.copy(step = OnboardingStep.WANTS)
    }

    fun toggleWantActivity(activityId: String) {
        val current = _uiState.value.selectedActivityIds.toMutableSet()
        if (current.contains(activityId)) current.remove(activityId) else current.add(activityId)
        _uiState.value = _uiState.value.copy(selectedActivityIds = current)
    }

    fun continueFromWants() {
        _uiState.value = _uiState.value.copy(step = OnboardingStep.SYNC)
    }

    fun back() {
        val current = _uiState.value.step
        val prev = when (current) {
            OnboardingStep.IDENTITY -> return
            OnboardingStep.HABITS -> OnboardingStep.IDENTITY
            OnboardingStep.WANTS -> OnboardingStep.HABITS
            OnboardingStep.SYNC -> OnboardingStep.WANTS
        }
        _uiState.value = _uiState.value.copy(step = prev)
    }

    fun finish() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val userId = container.currentUserId()
            val state = _uiState.value
            val selectedTemplates = state.habitTemplates
                .filter { it.template.id in state.selectedTemplateIds }
                .map { it.template }
            val selectedActivities = state.wantActivities.filter { it.id in state.selectedActivityIds }

            container.setupUserIdentitiesUseCase
                .execute(userId, state.selectedIdentityIds)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            val templateIdToHabitId = container.setupUserHabitsUseCase
                .execute(userId, selectedTemplates)
                .getOrElse { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            container.linkOnboardingHabitsToIdentitiesUseCase
                .execute(userId, templateIdToHabitId, state.selectedIdentityIds)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            container.setupUserWantActivitiesUseCase
                .execute(userId, selectedActivities)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            _finished.emit(Unit)
        }
    }
}
```

- [ ] **Step 2: Build the app module**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlin 2>&1 | tail -15
```
Expected: errors only in `OnboardingScreen.kt` (uses old `selectedIdentityId` field). That's fixed in Task 14.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingViewModel.kt
rtk git commit -m "feat(onboarding): ViewModel multi-select state + new finish sequence"
```

---

### Task 14: OnboardingScreen — multi-select toggle + overlap badge

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Update step 1 (Identity grid)**

Find the call to `IdentityStepBody`. Update it to pass `selectedIds` and `onToggle` instead of `selectedId` / `onSelect`:

In `OnboardingScreen` composable, replace:
```kotlin
            OnboardingStep.IDENTITY -> IdentityStepBody(
                identities = uiState.identities,
                selectedId = uiState.selectedIdentityId,
                onSelect = viewModel::selectIdentity,
                modifier = Modifier.padding(innerPadding),
            )
```
with:
```kotlin
            OnboardingStep.IDENTITY -> IdentityStepBody(
                identities = uiState.identities,
                selectedIds = uiState.selectedIdentityIds,
                onToggle = viewModel::toggleIdentity,
                modifier = Modifier.padding(innerPadding),
            )
```

Update `IdentityStepBody` signature:
```kotlin
@Composable
private fun IdentityStepBody(
    identities: List<Identity>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        items(identities, key = { it.id }) { identity ->
            IdentityGridCell(
                identity = identity,
                selected = identity.id in selectedIds,
                onSelect = { onToggle(identity.id) },
            )
        }
    }
}
```

`IdentityGridCell` itself doesn't need changes — its `selected: Boolean` already reflects whatever rule is passed. Only the parent decides whether multiple cells can be selected at once, and now they can.

- [ ] **Step 2: Update step 1 advisory hint and Next-gate**

Find the Scaffold `topBar = { Column(...) {` block where step copy renders. After the subtitle text, add a hint text only on the IDENTITY step:

```kotlin
                if (currentStep == OnboardingStep.IDENTITY) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Pick 1–4 to start. You can add more later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
```

The `Next` button gate is already in `OnboardingBottomBar` — verify the `primaryEnabled` lambda still uses:
```kotlin
                primaryEnabled = when (currentStep) {
                    OnboardingStep.IDENTITY -> uiState.selectedIdentityIds.isNotEmpty()
                    else -> true
                },
```
(rename `selectedIdentityId != null` to `selectedIdentityIds.isNotEmpty()`).

- [ ] **Step 3: Update step 2 (Habits) — render overlap badge**

Update `HabitsStepBody` signature + body to use `TemplateWithIdentities`:

```kotlin
@Composable
private fun HabitsStepBody(
    templates: List<TemplateWithIdentities>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        items(templates, key = { it.template.id }) { row ->
            val selected = row.template.id in selectedIds
            HabitTemplateRow(
                template = row.template,
                recommendedBy = row.recommendedBy,
                selected = selected,
                onToggle = { onToggle(row.template.id) },
            )
        }
    }
}
```

Update the `HabitsStepBody` call site:
```kotlin
            OnboardingStep.HABITS -> HabitsStepBody(
                templates = uiState.habitTemplates,
                selectedIds = uiState.selectedTemplateIds,
                onToggle = viewModel::toggleHabit,
                modifier = Modifier.padding(innerPadding),
            )
```

Update `HabitTemplateRow` to accept `recommendedBy: Set<Identity>` and render overlap badge below the existing subtitle. Find the `Column(modifier = Modifier.weight(1f))` block in `HabitTemplateRow` and after the subtitle Text, add:

```kotlin
                if (recommendedBy.size > 1) {
                    Text(
                        text = "Recommended by: ${recommendedBy.joinToString(" · ") { it.name }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
```

Update the `HabitTemplateRow` signature to add the parameter:
```kotlin
@Composable
private fun HabitTemplateRow(
    template: HabitTemplate,
    recommendedBy: Set<Identity>,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    // ... existing body, with the overlap badge added inside the Column as shown above
}
```

Add imports:
```kotlin
import com.habittracker.domain.model.TemplateWithIdentities
```

- [ ] **Step 4: Build (full app)**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlin 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/OnboardingScreen.kt
rtk git commit -m "feat(onboarding): multi-select identity grid + overlap badge on habits"
```

---

### Task 15: Home identity strip — ViewModel + components + render

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityChip.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityStrip.kt`

- [ ] **Step 1: Expose userIdentities flow from HomeViewModel**

Open `HomeViewModel.kt`. Find the section where other flows are exposed (e.g. `syncState`, `pending`). Add:

```kotlin
    val userIdentities: StateFlow<List<Identity>> =
        container.getUserIdentitiesUseCase.execute(container.currentUserId())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

Add imports:
```kotlin
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
```

- [ ] **Step 2: Create IdentityChip composable**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityChip.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.Identity

@Composable
fun IdentityChip(identity: Identity) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HabitGlyph(
                icon = androidx.compose.material.icons.Icons.Default.Person,
                hue = IdentityHue.forIdentityId(identity.id),
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

@Composable
fun IdentityMorePill(extraCount: Int) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

NOTE: `HabitGlyph` currently takes `ImageVector`. The `Icons.Default.Person` placeholder is acceptable until we have per-identity icons. If `Identity.icon` is a Material icon name string, the implementer can build a small `identityImageVector(name)` helper similar to the one in `HomeScreen.habitIcon`. For 5b, the `Person` placeholder is acceptable since icon mapping per identity ships with the IdentityList screen in 5c.

- [ ] **Step 3: Create IdentityStrip composable**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityStrip.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.Identity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentityStrip(
    identities: List<Identity>,
    modifier: Modifier = Modifier,
) {
    if (identities.isEmpty()) return
    val visible = identities.take(3)
    val extra = identities.size - visible.size
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp)
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
        visible.forEach { IdentityChip(it) }
        if (extra > 0) IdentityMorePill(extra)
    }
}
```

- [ ] **Step 4: Render IdentityStrip in HomeScreen**

Open `HomeScreen.kt`. Find the `LazyColumn` where the DailyStatusCard `item { ... }` is rendered (around line 173 in current file). Insert a NEW `item { ... }` BEFORE the DailyStatusCard item:

```kotlin
                // ── Identity strip ────────────────────────────────────────────
                item {
                    val identities by viewModel.userIdentities.collectAsState()
                    IdentityStrip(identities = identities)
                }
```

Add imports:
```kotlin
import com.jktdeveloper.habitto.ui.components.IdentityStrip
```

- [ ] **Step 5: Build**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlin 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityChip.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityStrip.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt
rtk git commit -m "feat(home): identity strip — read-only chips, inert tap"
```

---

### Task 16: You hub identity card — ViewModel + component + render

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHubCard.kt`

- [ ] **Step 1: Expose userIdentities from YouHubViewModel**

In `YouHubViewModel.kt`, find where existing flows are exposed and add:

```kotlin
    val userIdentities: StateFlow<List<Identity>> =
        container.getUserIdentitiesUseCase.execute(container.currentUserId())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

Add the necessary imports (analogous to Task 15 step 1).

- [ ] **Step 2: Create IdentityHubCard composable**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHubCard.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.Identity

@Composable
fun IdentityHubCard(identities: List<Identity>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IdentityAvatarStack(identities)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Identities",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${identities.size} active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun IdentityAvatarStack(identities: List<Identity>) {
    val visible = identities.take(3)
    val extra = identities.size - visible.size
    val totalSlots = visible.size + (if (extra > 0) 1 else 0)
    val width = if (totalSlots <= 0) 0.dp else (32.dp + 16.dp * (totalSlots - 1))
    Box(modifier = Modifier.size(width = width, height = 32.dp)) {
        visible.forEachIndexed { i, identity ->
            Box(modifier = Modifier.offset(x = (16 * i).dp)) {
                HabitGlyph(
                    icon = Icons.Filled.Person,
                    hue = IdentityHue.forIdentityId(identity.id),
                    size = 32.dp,
                )
            }
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (16 * visible.size).dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(32.dp)) {
                        Text(
                            text = "+$extra",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Render IdentityHubCard in YouHubScreen**

Open `YouHubScreen.kt`. Find the `LazyColumn`. Insert as the FIRST item (before any existing `SectionHeader`):

```kotlin
        item {
            val identities by viewModel.userIdentities.collectAsState()
            if (identities.isNotEmpty()) {
                IdentityHubCard(identities = identities)
            }
        }
```

Add import:
```kotlin
import com.jktdeveloper.habitto.ui.components.IdentityHubCard
```

- [ ] **Step 4: Build**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlin 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHubCard.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubViewModel.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt
rtk git commit -m "feat(you): identity hub card with avatar stack — inert tap"
```

---

### Task 17: Schema version bump + wipe-on-mismatch

**Files:**
- Modify: SQLDelight database setup (likely `mobile/shared/src/androidMain/kotlin/.../DatabaseFactory.kt` or wherever `AndroidSqliteDriver` is constructed)

- [ ] **Step 1: Find the database factory**

```bash
rtk grep -rn "AndroidSqliteDriver\|SqlDriver\|HabitTrackerDatabase.Schema" mobile/shared/src/ | head -10
```

Identify the file that constructs the SQLDelight driver and any current `onUpgrade` / `migration` setup.

- [ ] **Step 2: Bump the schema version**

Open `HabitTrackerDatabase.sq` (the file edited in Task 1). At the very top of the file, ensure there's a version directive — SQLDelight derives the version from `Schema.version` at code-gen time, but the build version is usually set in the gradle file. Check:

```bash
rtk grep -rn "schema.version\|generateAsync\|databases {" mobile/shared/build.gradle.kts | head -5
```

Bump the version. If `mobile/shared/build.gradle.kts` has a SQLDelight `databases { create("HabitTrackerDatabase") { ... } }` block with a `version` line, increment it. Otherwise SQLDelight defaults to incrementing automatically per migration file — in that case, the existing schema represents v1 and the new schema is v2 implicitly.

- [ ] **Step 3: Add wipe-on-mismatch behavior**

In the database factory file, ensure the driver opens with destructive migration. For Android (`AndroidSqliteDriver`):

```kotlin
val driver = AndroidSqliteDriver(
    schema = HabitTrackerDatabase.Schema,
    context = context,
    name = "habittracker.db",
    callback = object : AndroidSqliteDriver.Callback(HabitTrackerDatabase.Schema) {
        override fun onUpgrade(connection: app.cash.sqldelight.driver.android.AndroidSqliteDriver.Connection, oldVersion: Int, newVersion: Int) {
            // Dev only — drop everything and let Schema.create rebuild it.
            connection.execute("DROP TABLE IF EXISTS HabitLog")
            connection.execute("DROP TABLE IF EXISTS WantLog")
            connection.execute("DROP TABLE IF EXISTS LocalHabit")
            connection.execute("DROP TABLE IF EXISTS LocalIdentity")
            connection.execute("DROP TABLE IF EXISTS LocalWantActivity")
            connection.execute("DROP TABLE IF EXISTS LocalUserIdentity")
            connection.execute("DROP TABLE IF EXISTS LocalHabitIdentity")
            HabitTrackerDatabase.Schema.create(connection.toDriver())
        }
    },
)
```

NOTE: the precise Callback API depends on SQLDelight version. The implementer must check the project's SQLDelight version (in `gradle/libs.versions.toml`) and adapt — the goal is "drop all tables and recreate schema on version mismatch". If unsure, the simpler dev-only fix is to delete the DB file on app open if a sentinel preference doesn't match the current schema version:

```kotlin
val sentinelKey = "db_schema_version"
val currentSchemaVersion = HabitTrackerDatabase.Schema.version
val storedSchemaVersion = preferences.getInt(sentinelKey, 0)
if (storedSchemaVersion < currentSchemaVersion && context.getDatabasePath("habittracker.db").exists()) {
    context.deleteDatabase("habittracker.db")
}
preferences.edit().putInt(sentinelKey, currentSchemaVersion).apply()
```

The implementer picks whichever pattern fits the project's existing code.

- [ ] **Step 4: Reset watermarks alongside the DB wipe**

When the DB is wiped, reset `SyncWatermarkStore` so the next pull fetches everything. The factory likely has access to the store — call `syncWatermarkStore.reset()` after wiping.

- [ ] **Step 5: Build**

```bash
rtk ./gradlew :mobile:androidApp:assembleDebug 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/androidMain/kotlin/...
rtk git commit -m "feat(db): wipe-on-schema-mismatch (dev-only) for phase 5b migration"
```

---

### Task 18: Manual smoke + close-out

**Files:** none (smoke + git operations).

- [ ] **Step 1: Run full build + tests**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest :mobile:androidApp:assembleDebug 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`. All tests pass.

- [ ] **Step 2: Smoke checklist (light + dark)**

Install the APK. Verify:

- Onboarding step 1: pick 2-3 identities; cards stay selected; Next disabled at 0 selected.
- Onboarding step 2: union of recommended habits across picks; overlap badge appears for habits recommended by 2+ picked identities; deselecting an identity prunes templates from step 2.
- Onboarding step 3: wants unchanged.
- Onboarding step 4: sync screen unchanged.
- Onboarding finish: completes without error.
- Home: identity strip renders between top bar and DailyStatusCard; up to 3 chips visible; "+N more" pill if user picked 4+; chips don't ripple on tap.
- You hub: identity card visible above Account section; avatar stack matches identity count; chevron is visible but inert.
- Sign out → wipe → re-onboard flow: works.
- Multi-device sync (manual, optional): if you have two devices logged into the same account, identities should appear on both after the first sync.

- [ ] **Step 3: Push branch**

```bash
rtk git push -u origin feature/phase5b-multi-identity 2>&1 | tail -5
```

- [ ] **Step 4: Create PR**

```bash
rtk gh pr create --base main --head feature/phase5b-multi-identity --title "Phase 5b: multi-identity foundation" --body "$(cat <<'EOF'
## Summary

Replace single-identity onboarding with a multi-identity model. Persist user→identity and habit→identity associations across local SQLDelight + Supabase. Surface identities on Home (strip) and You hub (card). Read-only management surfaces — full Identity screens (List/Detail/Add) ship in 5c.

## Data model
- `LocalUserIdentity`, `LocalHabitIdentity` join tables (SQLDelight + Supabase mirrors w/ RLS).
- Wipe-on-schema-mismatch local migration (dev only — no production users yet).

## Domain
- `IdentityRepository` extended with multi-identity surface.
- New use cases: `SetupUserIdentitiesUseCase`, `GetUserIdentitiesUseCase`, `LinkOnboardingHabitsToIdentitiesUseCase`.
- Replaced: `GetHabitTemplatesForIdentityUseCase` → `GetHabitTemplatesForIdentitiesUseCase` (union, deduped, with `recommendedBy` set).
- `SetupUserHabitsUseCase` now returns `Map<templateId, habitId>`.

## Sync
- `PostgrestSupabaseSyncClient` extended with `upsert/fetch user_identities/habit_identities`.
- `SyncEngine` push + pull wired for both new tables.
- Last-write-wins on conflict (existing pattern).

## UI
- Onboarding step 1: multi-select grid (≥1 required, advisory hint).
- Onboarding step 2: union habit list with `Recommended by:` badge for multi-identity templates.
- Home: identity strip (I AM + chips + "+N more" pill); inert tap.
- You hub: identity card with avatar stack + count; inert tap.

## Out of scope (5c)
- IdentityList / IdentityDetail / AddIdentityFlow screens
- Custom identity creation
- Pinning / reordering
- Per-identity progress signals, notifications, exchange-rate
- Habit form multi-select identity field (gated on Habit CRUD)

## Test plan
- [x] `:mobile:shared:testDebugUnitTest` green
- [x] `:mobile:androidApp:testDebugUnitTest` green
- [x] `assembleDebug` green
- [x] Manual smoke (light + dark): onboarding multi-select, Home strip, You hub card, sign-out wipe, sign-in pull
EOF
)"
```

---

## Self-Review

**Spec coverage check:**
- Data model (LocalUserIdentity, LocalHabitIdentity) — Task 1 ✓
- Repository extension — Task 2 ✓
- Use cases (4 new + 1 replaced + 1 extended) — Tasks 3-7 ✓
- Sync extension — Tasks 8-10 ✓
- Supabase migration — Task 11 ✓
- AppContainer wiring — Task 12 ✓
- Onboarding rewrite — Tasks 13-14 ✓
- Home identity strip — Task 15 ✓
- You hub card — Task 16 ✓
- DB migration — Task 17 ✓
- Manual smoke + PR — Task 18 ✓
- Sign-out wipe extension — covered by Task 12 (AppContainer.signOut path needs to call `clearUserIdentitiesForUser` + `clearHabitIdentitiesForUser`); this is left implicit — implementer should grep for the existing `clear*` calls in the sign-out code path and add the two new ones. **Adding explicit instruction here**: Task 12 step should include: in AppContainer's sign-out routine, after existing `habitRepo.clearForUser(...)` etc., add `identityRepository.clearUserIdentitiesForUser(...)` and `identityRepository.clearHabitIdentitiesForUser(...)`.

**Type consistency:**
- `IdentityRepository` methods used in Tasks 3, 4, 7, 10, 12 all match the interface defined in Task 2 ✓
- `TemplateWithIdentities` defined in Task 5, used in Tasks 13, 14 ✓
- `Map<String, String>` (templateId→habitId) returned by Task 6, consumed by Task 7 + 13 ✓
- `UserIdentityRow` / `HabitIdentityRow` defined in Task 2, used in Tasks 8, 9, 10 ✓

**Placeholder scan:** "Check the SQLDelight version" / "the precise Callback API depends on SQLDelight version" in Task 17 are real implementation forks — engineer must inspect codebase. Acceptable. Not a placeholder.

One gap fix: I added the sign-out cleanup explicitly to the spec coverage above. Engineer should check `AppContainer.kt` for the existing wipe routine (search for `clearForUser`) during Task 12 and extend it.
