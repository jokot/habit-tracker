# Phase 5c-2 Identity CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the four identity write-paths — AddIdentityFlow (template-only), pin/unpin, remove (soft-delete), and "Why this identity" reflection editor — backed by a single schema migration adding three columns to `user_identities`.

**Architecture:** Schema migration (`2.sqm` local + Supabase ALTER) adds `isPinned`, `whyText`, `removedAt`. Existing `IdentityRepository` extended with five new mutators. Five new use cases (`AddIdentityWithHabitsUseCase`, `PinIdentityUseCase`, `UnpinIdentityUseCase`, `RemoveIdentityUseCase`, `UpdateIdentityWhyUseCase`). New `Screen.AddIdentity` route hosting a 2-step wizard (`AddIdentityStep1Screen` + `AddIdentityStep2Screen`) driven by one `AddIdentityViewModel` (step is VM state, not a separate route). Existing `IdentityDetailScreen` gains pin button, remove button, and inline reflection editor — all wired through `IdentityDetailViewModel`. `IdentityListScreen` gains an "Add identity" CTA card. Sync rides existing `user_identities` upsert pipeline once DTO mapper is updated.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Compose Material 3, Supabase Postgrest, kotlinx-coroutines, kotlinx-datetime, kotlin.uuid, JUnit + kotlinx-coroutines-test.

**Worktree:** `.worktrees/phase5c-2-identity-crud`. **Branch:** `feature/phase5c-2-identity-crud`. **Spec:** `docs/superpowers/specs/2026-05-02-phase5c-2-identity-crud-design.md`.

---

## File Map

**Create:**
- `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/2.sqm`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/PinIdentityUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/UnpinIdentityUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/RemoveIdentityUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/UpdateIdentityWhyUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCase.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/PinIdentityUseCaseTest.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/UnpinIdentityUseCaseTest.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/RemoveIdentityUseCaseTest.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/UpdateIdentityWhyUseCaseTest.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCaseTest.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityViewModel.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityStep1Screen.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityStep2Screen.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityScreen.kt` (host that switches between Step1 and Step2 based on VM state)

**Modify:**
- `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` (LocalUserIdentity columns + queries)
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt` (UserIdentityRow + interface methods)
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt` (mapper + new methods)
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt` (test fake — new methods)
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt` (UserIdentityDto)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` (wire 5 use cases)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModel.kt` (pin/remove/why actions + state)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailScreen.kt` (pin button + remove button + reflection editor + push_pin icon on stats)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListScreen.kt` ("Add identity" CTA + push_pin badge on pinned card)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListViewModel.kt` (expose pinned identityId in state)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt` (push_pin icon on pinned chip in strip)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt` (expose pinned identityId)
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` (Screen.AddIdentity route + composable)

**Supabase (apply via dashboard SQL Editor):**
- `ALTER TABLE public.user_identities ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE;`
- `ALTER TABLE public.user_identities ADD COLUMN why_text TEXT;`
- `ALTER TABLE public.user_identities ADD COLUMN removed_at TIMESTAMPTZ;`

---

## Task 1: SQLDelight schema migration + new queries

**Files:**
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`
- Create: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/2.sqm`

- [ ] **Step 1: Update `LocalUserIdentity` table definition in `HabitTrackerDatabase.sq`**

Replace the existing `LocalUserIdentity` block (lines 42-48):

```sql
CREATE TABLE IF NOT EXISTS LocalUserIdentity (
    userId TEXT NOT NULL,
    identityId TEXT NOT NULL,
    addedAt INTEGER NOT NULL,
    syncedAt INTEGER,
    isPinned INTEGER NOT NULL DEFAULT 0,
    whyText TEXT,
    removedAt INTEGER,
    PRIMARY KEY (userId, identityId)
);
```

- [ ] **Step 2: Update existing `LocalUserIdentity` queries**

Find the `-- LocalUserIdentity queries` section and replace with:

```sql
-- LocalUserIdentity queries
upsertUserIdentity:
INSERT OR REPLACE INTO LocalUserIdentity (userId, identityId, addedAt, syncedAt, isPinned, whyText, removedAt)
VALUES (?, ?, ?, NULL, 0, NULL, NULL);

mergePulledUserIdentity:
INSERT OR REPLACE INTO LocalUserIdentity (userId, identityId, addedAt, syncedAt, isPinned, whyText, removedAt)
VALUES (?, ?, ?, ?, ?, ?, ?);

getUserIdentities:
SELECT * FROM LocalUserIdentity WHERE userId = ? AND removedAt IS NULL ORDER BY addedAt ASC;

deleteUserIdentity:
DELETE FROM LocalUserIdentity WHERE userId = ? AND identityId = ?;

deleteAllUserIdentitiesForUser:
DELETE FROM LocalUserIdentity WHERE userId = ?;

getUnsyncedUserIdentitiesForUser:
SELECT * FROM LocalUserIdentity WHERE userId = ? AND syncedAt IS NULL;

markUserIdentitySynced:
UPDATE LocalUserIdentity SET syncedAt = ? WHERE userId = ? AND identityId = ?;

setPinForIdentity:
UPDATE LocalUserIdentity SET isPinned = ?, syncedAt = NULL WHERE userId = ? AND identityId = ?;

clearPinForUser:
UPDATE LocalUserIdentity SET isPinned = 0, syncedAt = NULL WHERE userId = ? AND isPinned = 1;

updateWhyText:
UPDATE LocalUserIdentity SET whyText = ?, syncedAt = NULL WHERE userId = ? AND identityId = ?;

markUserIdentityRemoved:
UPDATE LocalUserIdentity SET removedAt = ?, isPinned = 0, syncedAt = NULL WHERE userId = ? AND identityId = ?;
```

- [ ] **Step 3: Create migration `2.sqm`**

Create `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/2.sqm`:

```sql
-- Migrate HabitTrackerDatabase from version 1 → 2
-- Adds isPinned (NOT NULL, default 0), whyText (nullable), removedAt (nullable)
-- to LocalUserIdentity for Phase 5c-2 identity CRUD.

ALTER TABLE LocalUserIdentity ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0;
ALTER TABLE LocalUserIdentity ADD COLUMN whyText TEXT;
ALTER TABLE LocalUserIdentity ADD COLUMN removedAt INTEGER;
```

- [ ] **Step 4: Build to confirm SQLDelight regenerates the database interface cleanly**

Run:
```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase5c-2-identity-crud
rtk ./gradlew :mobile:shared:generateCommonMainHabitTrackerDatabaseInterface
```

Expected: BUILD SUCCESSFUL.

This will regenerate the Kotlin types under `build/generated/sqldelight/`. The next task fixes the call sites that now have new column parameters.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq \
            mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/2.sqm
rtk git commit -m "$(cat <<'EOF'
feat(db): user_identities — isPinned, whyText, removedAt columns + queries

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: UserIdentityRow + repository interface + LocalIdentityRepository updates

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt` (already exists in commonTest — Read first)

- [ ] **Step 1: Update `UserIdentityRow` data class + interface**

Edit `IdentityRepository.kt`. Replace `UserIdentityRow` definition (existing lines 34-39):

```kotlin
data class UserIdentityRow(
    val userId: String,
    val identityId: String,
    val addedAt: Instant,
    val syncedAt: Instant?,
    val isPinned: Boolean = false,
    val whyText: String? = null,
    val removedAt: Instant? = null,
)
```

Add the following abstract method declarations to the `IdentityRepository` interface (between `mergePulledUserIdentity` and the habit-identities section):

```kotlin
suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean)
suspend fun clearPinForUser(userId: String)
suspend fun updateWhyText(userId: String, identityId: String, whyText: String?)
suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant)
suspend fun setPinAtomically(userId: String, identityId: String)
```

(`setPinAtomically` is the wrapper used by the use case — clears any existing pin and sets the new one in a single transaction. Keeping the lower-level `setPinForIdentity` and `clearPinForUser` exposed for unit-testing the transaction parts independently.)

- [ ] **Step 2: Update `LocalIdentityRepository` mappers + add new methods**

Replace the body of `mergePulledUserIdentity` and `getUnsyncedUserIdentitiesFor` to round-trip the new columns:

```kotlin
override suspend fun getUnsyncedUserIdentitiesFor(userId: String): List<UserIdentityRow> =
    q.getUnsyncedUserIdentitiesForUser(userId).executeAsList().map {
        UserIdentityRow(
            userId = it.userId,
            identityId = it.identityId,
            addedAt = Instant.fromEpochMilliseconds(it.addedAt),
            syncedAt = it.syncedAt?.let(Instant::fromEpochMilliseconds),
            isPinned = it.isPinned == 1L,
            whyText = it.whyText,
            removedAt = it.removedAt?.let(Instant::fromEpochMilliseconds),
        )
    }

override suspend fun mergePulledUserIdentity(row: UserIdentityRow) {
    q.mergePulledUserIdentity(
        userId = row.userId,
        identityId = row.identityId,
        addedAt = row.addedAt.toEpochMilliseconds(),
        syncedAt = row.syncedAt?.toEpochMilliseconds(),
        isPinned = if (row.isPinned) 1L else 0L,
        whyText = row.whyText,
        removedAt = row.removedAt?.toEpochMilliseconds(),
    )
}
```

Add these new method implementations (after `mergePulledUserIdentity`):

```kotlin
override suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean) {
    q.setPinForIdentity(isPinned = if (isPinned) 1L else 0L, userId = userId, identityId = identityId)
}

override suspend fun clearPinForUser(userId: String) {
    q.clearPinForUser(userId)
}

override suspend fun updateWhyText(userId: String, identityId: String, whyText: String?) {
    q.updateWhyText(whyText = whyText, userId = userId, identityId = identityId)
}

override suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant) {
    q.markUserIdentityRemoved(removedAt = removedAt.toEpochMilliseconds(), userId = userId, identityId = identityId)
}

override suspend fun setPinAtomically(userId: String, identityId: String) {
    q.transaction {
        q.clearPinForUser(userId)
        q.setPinForIdentity(isPinned = 1L, userId = userId, identityId = identityId)
    }
}
```

- [ ] **Step 3: Read existing `FakeIdentityRepository` to learn its shape**

```bash
cat /Users/jokot/dev/habit-tracker/.worktrees/phase5c-2-identity-crud/mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt
```

(The Fake is in commonTest for unit tests. Do not invent its layout — read first, then add the five new method overrides matching the in-memory storage pattern the existing class uses.)

- [ ] **Step 4: Add new method overrides to `FakeIdentityRepository`**

The Fake stores `userIdentities` as in-memory map/list. Add a mutable map for storing pin state and the new fields. Concrete additions (paste verbatim into the Fake class body, adapting field names to whatever the existing in-memory storage uses — IF the existing Fake uses `data class FakeUserIdentity(val userId, identityId, addedAt, syncedAt)`, extend it with the new fields too):

```kotlin
override suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean) {
    val list = userIdentities.getOrPut(userId) { mutableListOf() }
    list.replaceAll { if (it.identityId == identityId) it.copy(isPinned = isPinned, syncedAt = null) else it }
}

override suspend fun clearPinForUser(userId: String) {
    val list = userIdentities[userId] ?: return
    list.replaceAll { if (it.isPinned) it.copy(isPinned = false, syncedAt = null) else it }
}

override suspend fun updateWhyText(userId: String, identityId: String, whyText: String?) {
    val list = userIdentities.getOrPut(userId) { mutableListOf() }
    list.replaceAll { if (it.identityId == identityId) it.copy(whyText = whyText, syncedAt = null) else it }
}

override suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant) {
    val list = userIdentities.getOrPut(userId) { mutableListOf() }
    list.replaceAll { if (it.identityId == identityId) it.copy(removedAt = removedAt, isPinned = false, syncedAt = null) else it }
}

override suspend fun setPinAtomically(userId: String, identityId: String) {
    clearPinForUser(userId)
    setPinForIdentity(userId, identityId, isPinned = true)
}
```

If the existing Fake's storage type lacks `isPinned`/`whyText`/`removedAt` fields, add them with sensible defaults (`isPinned = false`, `whyText = null`, `removedAt = null`).

Also update `observeUserIdentities` (or its in-memory equivalent) in the Fake to filter rows where `removedAt != null`.

- [ ] **Step 5: Build to verify everything compiles**

```bash
rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid :mobile:shared:compileTestKotlinJvm
```

Expected: BUILD SUCCESSFUL. The generated SQLDelight classes now exist with the new column types; the repository compiles against them.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt
rtk git commit -m "$(cat <<'EOF'
feat(repo): IdentityRepository pin/remove/why methods + soft-delete filter

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Sync DTO mapper update

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt`

- [ ] **Step 1: Replace `UserIdentityDto` + mapper functions**

Find the `UserIdentityDto` section (around line 264) and replace with:

```kotlin
@Serializable
private data class UserIdentityDto(
    @SerialName("user_id") val userId: String,
    @SerialName("identity_id") val identityId: String,
    @SerialName("added_at") val addedAt: String,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("why_text") val whyText: String? = null,
    @SerialName("removed_at") val removedAt: String? = null,
)

private fun UserIdentityRow.toDto() = UserIdentityDto(
    userId = userId,
    identityId = identityId,
    addedAt = addedAt.toString(),
    isPinned = isPinned,
    whyText = whyText,
    removedAt = removedAt?.toString(),
)

private fun UserIdentityDto.toDomain() = UserIdentityRow(
    userId = userId,
    identityId = identityId,
    addedAt = Instant.parse(addedAt),
    syncedAt = Instant.parse(addedAt), // server-derived; existing convention
    isPinned = isPinned,
    whyText = whyText,
    removedAt = removedAt?.let { Instant.parse(it) },
)
```

- [ ] **Step 2: Build to verify**

```bash
rtk ./gradlew :mobile:shared:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt
rtk git commit -m "$(cat <<'EOF'
feat(sync): user_identities DTO carries isPinned, whyText, removedAt

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: PinIdentityUseCase + UnpinIdentityUseCase + tests

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/PinIdentityUseCase.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/UnpinIdentityUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/PinIdentityUseCaseTest.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/UnpinIdentityUseCaseTest.kt`

- [ ] **Step 1: Write failing tests**

`PinIdentityUseCaseTest.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PinIdentityUseCaseTest {
    private val repo = FakeIdentityRepository()
    private val useCase = PinIdentityUseCase(repo)
    private val userId = "u1"

    @Test
    fun `pins target identity`() = runTest {
        repo.seedUserIdentity(userId, "athlete")
        useCase.execute(userId, "athlete")
        assertTrue(repo.isPinned(userId, "athlete"))
    }

    @Test
    fun `clears previous pin when pinning a new identity`() = runTest {
        repo.seedUserIdentity(userId, "athlete", isPinned = true)
        repo.seedUserIdentity(userId, "reader")
        useCase.execute(userId, "reader")
        assertFalse(repo.isPinned(userId, "athlete"))
        assertTrue(repo.isPinned(userId, "reader"))
    }
}
```

`UnpinIdentityUseCaseTest.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

class UnpinIdentityUseCaseTest {
    private val repo = FakeIdentityRepository()
    private val useCase = UnpinIdentityUseCase(repo)
    private val userId = "u1"

    @Test
    fun `unpins identity`() = runTest {
        repo.seedUserIdentity(userId, "athlete", isPinned = true)
        useCase.execute(userId, "athlete")
        assertFalse(repo.isPinned(userId, "athlete"))
    }
}
```

(`FakeIdentityRepository` needs helper methods `seedUserIdentity(userId, identityId, isPinned = false)` and `isPinned(userId, identityId): Boolean` — add both as part of this task's Fake updates.)

- [ ] **Step 2: Run tests — expect FAIL (use case classes do not exist yet)**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "com.habittracker.domain.usecase.PinIdentityUseCaseTest" --tests "com.habittracker.domain.usecase.UnpinIdentityUseCaseTest"
```

Expected: compile error — `PinIdentityUseCase`/`UnpinIdentityUseCase` not defined.

- [ ] **Step 3: Implement use cases**

`PinIdentityUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository

class PinIdentityUseCase(private val repo: IdentityRepository) {
    suspend fun execute(userId: String, identityId: String) {
        repo.setPinAtomically(userId, identityId)
    }
}
```

`UnpinIdentityUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository

class UnpinIdentityUseCase(private val repo: IdentityRepository) {
    suspend fun execute(userId: String, identityId: String) {
        repo.setPinForIdentity(userId, identityId, isPinned = false)
    }
}
```

- [ ] **Step 4: Add `seedUserIdentity` and `isPinned` helpers to `FakeIdentityRepository`**

Helpers expose internal state for tests. Add to `FakeIdentityRepository` (read its existing shape first):

```kotlin
fun seedUserIdentity(userId: String, identityId: String, isPinned: Boolean = false) {
    val list = userIdentities.getOrPut(userId) { mutableListOf() }
    list += FakeUserIdentity(
        userId = userId,
        identityId = identityId,
        addedAt = Clock.System.now(),
        syncedAt = null,
        isPinned = isPinned,
        whyText = null,
        removedAt = null,
    )
}

fun isPinned(userId: String, identityId: String): Boolean =
    userIdentities[userId]?.firstOrNull { it.identityId == identityId }?.isPinned == true
```

(Adapt to actual storage shape if the Fake uses different internals.)

- [ ] **Step 5: Run tests — expect PASS**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "com.habittracker.domain.usecase.PinIdentityUseCaseTest" --tests "com.habittracker.domain.usecase.UnpinIdentityUseCaseTest"
```

Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/PinIdentityUseCase.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/UnpinIdentityUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/PinIdentityUseCaseTest.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/UnpinIdentityUseCaseTest.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt
rtk git commit -m "$(cat <<'EOF'
feat(usecase): PinIdentityUseCase + UnpinIdentityUseCase

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: RemoveIdentityUseCase + test

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/RemoveIdentityUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/RemoveIdentityUseCaseTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoveIdentityUseCaseTest {
    private val repo = FakeIdentityRepository()
    private val useCase = RemoveIdentityUseCase(repo, clock = Clock.System)
    private val userId = "u1"

    @Test
    fun `soft-deletes identity and clears its pin`() = runTest {
        repo.seedUserIdentity(userId, "athlete", isPinned = true)
        useCase.execute(userId, "athlete")
        // Active list excludes removed
        val active = repo.observeUserIdentities(userId).first()
        assertTrue(active.none { it.id == "athlete" })
        // Pin is gone
        assertFalse(repo.isPinned(userId, "athlete"))
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "com.habittracker.domain.usecase.RemoveIdentityUseCaseTest"
```

Expected: compile error — `RemoveIdentityUseCase` undefined.

- [ ] **Step 3: Implement use case**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository
import kotlinx.datetime.Clock

class RemoveIdentityUseCase(
    private val repo: IdentityRepository,
    private val clock: Clock = Clock.System,
) {
    suspend fun execute(userId: String, identityId: String) {
        repo.markUserIdentityRemoved(userId, identityId, removedAt = clock.now())
    }
}
```

(`markUserIdentityRemoved` itself sets `isPinned = 0` in the same SQL UPDATE, so the use case stays single-call.)

- [ ] **Step 4: Run test — expect PASS**

Expected: 1 test passes.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/RemoveIdentityUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/RemoveIdentityUseCaseTest.kt
rtk git commit -m "$(cat <<'EOF'
feat(usecase): RemoveIdentityUseCase — soft delete + clear pin

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: UpdateIdentityWhyUseCase + test

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/UpdateIdentityWhyUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/UpdateIdentityWhyUseCaseTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeIdentityRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateIdentityWhyUseCaseTest {
    private val repo = FakeIdentityRepository()
    private val useCase = UpdateIdentityWhyUseCase(repo)
    private val userId = "u1"

    @Test
    fun `sets whyText to provided string`() = runTest {
        repo.seedUserIdentity(userId, "athlete")
        useCase.execute(userId, "athlete", "I want to be strong.")
        assertEquals("I want to be strong.", repo.getWhyText(userId, "athlete"))
    }

    @Test
    fun `whitespace-only string normalizes to null`() = runTest {
        repo.seedUserIdentity(userId, "athlete")
        useCase.execute(userId, "athlete", "   \n  ")
        assertNull(repo.getWhyText(userId, "athlete"))
    }

    @Test
    fun `empty string normalizes to null`() = runTest {
        repo.seedUserIdentity(userId, "athlete")
        useCase.execute(userId, "athlete", "")
        assertNull(repo.getWhyText(userId, "athlete"))
    }
}
```

(`getWhyText` is a Fake-only test helper. Add it: `fun getWhyText(userId: String, identityId: String): String? = userIdentities[userId]?.firstOrNull { it.identityId == identityId }?.whyText`.)

- [ ] **Step 2: Run test — expect FAIL**

Expected: compile error — `UpdateIdentityWhyUseCase` undefined.

- [ ] **Step 3: Implement use case**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository

class UpdateIdentityWhyUseCase(private val repo: IdentityRepository) {
    suspend fun execute(userId: String, identityId: String, whyText: String?) {
        val normalized = whyText?.trim()?.takeIf { it.isNotEmpty() }
        repo.updateWhyText(userId, identityId, normalized)
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/UpdateIdentityWhyUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/UpdateIdentityWhyUseCaseTest.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt
rtk git commit -m "$(cat <<'EOF'
feat(usecase): UpdateIdentityWhyUseCase with whitespace/empty normalization

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: AddIdentityWithHabitsUseCase + test

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCase.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCaseTest.kt`

- [ ] **Step 1: Read existing `GetHabitTemplatesForIdentitiesUseCase` to understand the template shape**

```bash
cat /Users/jokot/dev/habit-tracker/.worktrees/phase5c-2-identity-crud/mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetHabitTemplatesForIdentitiesUseCase.kt
```

Look for the `HabitTemplate` data class and how it carries `templateId, name, unit, threshold, target, icon`. The new use case consumes a `Set<String>` of templateIds plus knows how to build `Habit` rows from templates — re-use the same template helper.

- [ ] **Step 2: Read existing `LinkOnboardingHabitsToIdentitiesUseCase` to learn the linking pattern**

```bash
cat /Users/jokot/dev/habit-tracker/.worktrees/phase5c-2-identity-crud/mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LinkOnboardingHabitsToIdentitiesUseCase.kt
```

This use case ran during onboarding to link the freshly-seeded onboarding habits to identities. Same pattern — but Phase 5c-2 creates new habits inside the use case rather than receiving a ready list.

- [ ] **Step 3: Write failing test**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeIdentityRepository
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddIdentityWithHabitsUseCaseTest {
    private val habitRepo = FakeHabitRepository()
    private val identityRepo = FakeIdentityRepository()
    private val templates = GetHabitTemplatesForIdentitiesUseCase()
    private val useCase = AddIdentityWithHabitsUseCase(
        habitRepo = habitRepo,
        identityRepo = identityRepo,
        templates = templates,
        clock = Clock.System,
    )
    private val userId = "u1"

    @Test
    fun `inserts user_identity row`() = runTest {
        useCase.execute(userId, "athlete", selectedTemplateIds = emptySet())
        val active = identityRepo.observeUserIdentities(userId).first()
        assertTrue(active.any { it.id == "athlete" })
    }

    @Test
    fun `creates new habit and link for each selected template not yet owned`() = runTest {
        useCase.execute(userId, "athlete", selectedTemplateIds = setOf("run", "stretch"))
        val habits = habitRepo.getHabitsForUser(userId)
        assertEquals(2, habits.size)
        val identityHabits = identityRepo.observeHabitsForIdentity(userId, "athlete").first()
        assertEquals(2, identityHabits.size)
    }

    @Test
    fun `reuses existing habit when templateId already in user's habits`() = runTest {
        // Seed an existing run habit for this user (linked to maker, say)
        habitRepo.saveHabit(makeHabit(userId, "h-existing", templateId = "run"))
        identityRepo.linkHabitToIdentities("h-existing", setOf("maker"))

        useCase.execute(userId, "athlete", selectedTemplateIds = setOf("run"))

        val habits = habitRepo.getHabitsForUser(userId)
        assertEquals(1, habits.size, "should not create duplicate habit")
        val athleteHabits = identityRepo.observeHabitsForIdentity(userId, "athlete").first()
        assertEquals(1, athleteHabits.size)
        assertEquals("h-existing", athleteHabits.first().id)
    }

    private fun makeHabit(userId: String, id: String, templateId: String): Habit {
        val now = Clock.System.now()
        return Habit(
            id = id, userId = userId, templateId = templateId, name = "Run",
            unit = "min", thresholdPerPoint = 1.0, dailyTarget = 1,
            createdAt = now, updatedAt = now, syncedAt = null,
        )
    }
}
```

- [ ] **Step 4: Run test — expect FAIL**

Expected: compile error — `AddIdentityWithHabitsUseCase` undefined.

- [ ] **Step 5: Implement use case**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AddIdentityWithHabitsUseCase(
    private val habitRepo: HabitRepository,
    private val identityRepo: IdentityRepository,
    private val templates: GetHabitTemplatesForIdentitiesUseCase,
    private val clock: Clock = Clock.System,
) {
    /**
     * Inserts a new user_identity row and links/creates habits for each selectedTemplateId.
     * - If the user already has a habit with the given templateId, only a habit_identities link is added.
     * - Otherwise, a new habit row is created from the template (defaults applied) and linked.
     */
    suspend fun execute(
        userId: String,
        identityId: String,
        selectedTemplateIds: Set<String>,
    ) {
        // 1. Insert user_identity (idempotent INSERT OR REPLACE).
        identityRepo.setUserIdentities(userId, /* preserve existing + add new */
            existingIds(userId) + identityId)

        // 2. For each selected template, link existing habit OR create + link.
        val ownedByTemplate = habitRepo.getHabitsForUser(userId).associateBy { it.templateId }
        val templatesByIdentity = templates.execute(setOf(identityId))[identityId].orEmpty()
        val templatesById = templatesByIdentity.associateBy { it.templateId }

        for (templateId in selectedTemplateIds) {
            val existing = ownedByTemplate[templateId]
            if (existing != null) {
                identityRepo.linkHabitToIdentities(existing.id, setOf(identityId))
                continue
            }
            val tpl = templatesById[templateId] ?: continue
            val now = clock.now()
            val habit = Habit(
                id = Uuid.random().toString(),
                userId = userId,
                templateId = templateId,
                name = tpl.name,
                unit = tpl.unit,
                thresholdPerPoint = tpl.threshold,
                dailyTarget = tpl.target,
                createdAt = now,
                updatedAt = now,
                syncedAt = null,
            )
            habitRepo.saveHabit(habit)
            identityRepo.linkHabitToIdentities(habit.id, setOf(identityId))
        }
    }

    private suspend fun existingIds(userId: String): Set<String> =
        identityRepo.observeUserIdentities(userId).let { flow ->
            val first = kotlinx.coroutines.flow.first(flow)
            first.map { it.id }.toSet()
        }
}
```

(NOTE: The `setUserIdentities` method preserves existing identities — pass `existing + new`. If `setUserIdentities` is replace-style, switch to a more granular `addUserIdentity` method on the repo. Verify by reading the existing `LocalIdentityRepository.setUserIdentities` impl: it computes `toAdd = identityIds - existing` and `toRemove = existing - identityIds`, so passing `existing + new` makes it additive — only inserts the new identity. Confirmed safe.)

- [ ] **Step 6: Run test — expect PASS**

```bash
rtk ./gradlew :mobile:shared:testDebugUnitTest --tests "com.habittracker.domain.usecase.AddIdentityWithHabitsUseCaseTest"
```

Expected: 3 tests pass.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCase.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/AddIdentityWithHabitsUseCaseTest.kt
rtk git commit -m "$(cat <<'EOF'
feat(usecase): AddIdentityWithHabitsUseCase — link existing or create new

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: AppContainer wiring + Screen.AddIdentity route

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Add use cases to `AppContainer`**

Find the use case wiring section in `AppContainer.kt` (alongside `setupUserIdentitiesUseCase` etc.) and add:

```kotlin
val pinIdentityUseCase = PinIdentityUseCase(identityRepository)
val unpinIdentityUseCase = UnpinIdentityUseCase(identityRepository)
val removeIdentityUseCase = RemoveIdentityUseCase(identityRepository)
val updateIdentityWhyUseCase = UpdateIdentityWhyUseCase(identityRepository)
val addIdentityWithHabitsUseCase = AddIdentityWithHabitsUseCase(
    habitRepo = habitRepository,
    identityRepo = identityRepository,
    templates = getHabitTemplatesForIdentitiesUseCase,
)
```

Add the corresponding imports.

- [ ] **Step 2: Add route to `Screen` sealed class**

In `AppNavigation.kt`, find the `sealed class Screen` declaration and add:

```kotlin
object AddIdentity : Screen("add_identity")
```

- [ ] **Step 3: Add NavHost composable for the new route (placeholder body for now)**

Inside the `NavHost(...) { ... }` block, add:

```kotlin
composable(Screen.AddIdentity.route) {
    val vm = viewModel { AddIdentityViewModel(container) }
    AddIdentityScreen(
        viewModel = vm,
        onClose = { navController.popBackStack() },
        onCommitSuccess = {
            navController.navigate(Screen.IdentityList.route) {
                popUpTo(Screen.IdentityList.route) { inclusive = false }
            }
        },
    )
}
```

(`AddIdentityViewModel` and `AddIdentityScreen` will be implemented in subsequent tasks. Compilation will fail until those exist — accept the failure for this task and move on. The next tasks fix the chain.)

- [ ] **Step 4: Build to verify the use cases wired in `AppContainer` compile**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD FAIL with "Unresolved reference: AddIdentityViewModel" / "AddIdentityScreen". This is expected; mark task complete after Tasks 9-11 land. To unblock build temporarily comment out the `composable(Screen.AddIdentity.route) { ... }` block — but DO NOT commit the commented-out version. Restore it before commit.

(Alternative: skip Step 3 entirely until Task 11. Plan choice: ship Steps 1+2 in this task's commit, defer Step 3 to Task 11.)

REVISED — Steps 3+4 deferred to Task 11. This task commits Steps 1+2 only.

- [ ] **Step 5: Commit (Steps 1+2 only)**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "$(cat <<'EOF'
feat(container): wire identity CRUD use cases + Screen.AddIdentity route

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: AddIdentityViewModel

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityViewModel.kt`

- [ ] **Step 1: Implement the ViewModel**

```kotlin
package com.jktdeveloper.habitto.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.AddIdentityWithHabitsUseCase
import com.habittracker.domain.usecase.GetHabitTemplatesForIdentitiesUseCase
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.data.repository.HabitRepository
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

data class HabitChoice(
    val templateId: String,
    val name: String,
    val icon: String,
    val threshold: Double,
    val target: Int,
    val unit: String,
    val checked: Boolean,
    val alreadyTracking: Boolean,
)

data class AddIdentityUiState(
    val step: Int = 1,
    val candidates: List<Identity> = emptyList(),
    val selectedIdentity: Identity? = null,
    val recommendedHabits: List<HabitChoice> = emptyList(),
    val isCommitting: Boolean = false,
    val error: String? = null,
)

class AddIdentityViewModel(
    private val identityRepo: IdentityRepository,
    private val habitRepo: HabitRepository,
    private val templates: GetHabitTemplatesForIdentitiesUseCase,
    private val addUseCase: AddIdentityWithHabitsUseCase,
    private val userIdProvider: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow(AddIdentityUiState())
    val state: StateFlow<AddIdentityUiState> = _state.asStateFlow()

    private val _commitSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commitSuccess: SharedFlow<Unit> = _commitSuccess.asSharedFlow()

    constructor(container: AppContainer) : this(
        identityRepo = container.identityRepository,
        habitRepo = container.habitRepository,
        templates = container.getHabitTemplatesForIdentitiesUseCase,
        addUseCase = container.addIdentityWithHabitsUseCase,
        userIdProvider = { container.currentUserId() },
    )

    init { loadCandidates() }

    private fun loadCandidates() {
        viewModelScope.launch {
            val userId = userIdProvider()
            val all = identityRepo.getAllIdentities()
            val active = identityRepo.observeUserIdentities(userId).first().map { it.id }.toSet()
            val candidates = all.filter { it.id !in active }
            _state.update { it.copy(candidates = candidates, selectedIdentity = candidates.firstOrNull()) }
        }
    }

    fun selectIdentity(identity: Identity) {
        _state.update { it.copy(selectedIdentity = identity) }
    }

    fun advanceToStep2() {
        val selected = _state.value.selectedIdentity ?: return
        viewModelScope.launch {
            val userId = userIdProvider()
            val ownedTemplateIds = habitRepo.getHabitsForUser(userId).map { it.templateId }.toSet()
            val tpls = templates.execute(setOf(selected.id))[selected.id].orEmpty()
            val choices = tpls.map { tpl ->
                HabitChoice(
                    templateId = tpl.templateId,
                    name = tpl.name,
                    icon = tpl.icon,
                    threshold = tpl.threshold,
                    target = tpl.target,
                    unit = tpl.unit,
                    checked = true,
                    alreadyTracking = tpl.templateId in ownedTemplateIds,
                )
            }
            _state.update { it.copy(step = 2, recommendedHabits = choices) }
        }
    }

    fun goBackToStep1() {
        _state.update { it.copy(step = 1) }
    }

    fun toggleHabit(templateId: String) {
        _state.update { current ->
            current.copy(
                recommendedHabits = current.recommendedHabits.map {
                    if (it.templateId == templateId) it.copy(checked = !it.checked) else it
                }
            )
        }
    }

    fun commit() {
        val selected = _state.value.selectedIdentity ?: return
        val selectedTemplateIds = _state.value.recommendedHabits.filter { it.checked }.map { it.templateId }.toSet()
        _state.update { it.copy(isCommitting = true, error = null) }
        viewModelScope.launch {
            val userId = userIdProvider()
            runCatching { addUseCase.execute(userId, selected.id, selectedTemplateIds) }
                .onSuccess {
                    _state.update { it.copy(isCommitting = false) }
                    _commitSuccess.tryEmit(Unit)
                }
                .onFailure { e ->
                    _state.update { it.copy(isCommitting = false, error = "Couldn't save — try again. (${e.message})") }
                }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD FAIL with "Unresolved reference: AddIdentityScreen" (Task 11). The ViewModel itself compiles; the failure is from the NavHost block waiting for Task 11.

If the failure is in the ViewModel itself (signature, imports, unresolved use case), fix and rebuild.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityViewModel.kt
rtk git commit -m "$(cat <<'EOF'
feat(ui): AddIdentityViewModel — 2-step state, commit-success event

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: AddIdentityStep1Screen + AddIdentityStep2Screen + AddIdentityScreen host

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityScreen.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityStep1Screen.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityStep2Screen.kt`

This task is UI-heavy. Follow the design in `/tmp/habitto-design/habitto/project/screens.jsx` lines 2162-2356 for visual fidelity. Re-use existing `IdentityAvatar` (locate via `find` if unfamiliar) and existing identity-hue helper. Re-use `HabitGlyph` for habit icon rendering.

- [ ] **Step 1: Create the host `AddIdentityScreen.kt`**

```kotlin
package com.jktdeveloper.habitto.ui.identity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun AddIdentityScreen(
    viewModel: AddIdentityViewModel,
    onClose: () -> Unit,
    onCommitSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.commitSuccess.collect { onCommitSuccess() }
    }
    when (state.step) {
        1 -> AddIdentityStep1Screen(
            state = state,
            onClose = onClose,
            onSelect = viewModel::selectIdentity,
            onContinue = viewModel::advanceToStep2,
        )
        2 -> AddIdentityStep2Screen(
            state = state,
            onBack = viewModel::goBackToStep1,
            onToggle = viewModel::toggleHabit,
            onCommit = viewModel::commit,
        )
    }
}
```

- [ ] **Step 2: Create `AddIdentityStep1Screen.kt`**

Render per design (screens.jsx:2162-2258). Key elements:

- Top app bar: close icon (left), title "Add identity"
- Heading "Who else are you becoming?" (titleLarge, bold) + sub "Pick from suggestions or define your own." (bodyMedium, on-surface-variant)
- Search bar — non-functional `OutlinedTextField` placeholder (or static `Surface` row mimicking the search look) with prefix icon + ghost text "Search identities…"
- 2-column `LazyVerticalGrid` (or chunked `Column`) of identity tiles:
  - Each tile: hue-tinted bg when selected (light variant of identity hue), accent border when selected, default `surface` bg + `outlineVariant` border otherwise
  - Avatar 36dp + identity name + identity description
  - Selected tile shows a small filled-circle check badge top-right
  - Tap → `onSelect(identity)`
- "Custom" dashed tile as the last grid item:
  - Icon + "Custom" + "Define your own."
  - Tap → show toast "Coming soon" via `LocalContext` + `Toast.makeText(...).show()` (no nav, no state change)
- Sticky bottom CTA: filled button "Continue with {selectedIdentity.name.lowercase()}" with arrow_forward icon → `onContinue()`

(Full Compose code is verbose; reference the design's JSX as the visual spec and produce idiomatic Compose Material 3. Aim for visual parity with the canvas mockup.)

- [ ] **Step 3: Create `AddIdentityStep2Screen.kt`**

Render per design (screens.jsx:2271-2356). Key elements:

- Top app bar: back arrow → `onBack()`, title "Add {identityName}"
- Header row: identity avatar (44dp) + "What does a {name} do?" (titleMedium, weight 700) + "Pick the habits you want to track." (bodyMedium)
- Vertical list of habit cards from `state.recommendedHabits`:
  - Each card has: square check toggle (22dp, hue-tinted when checked), `HabitGlyph(icon, hue, 40dp)`, name, "{target} × {threshold} {unit}"
  - When `alreadyTracking == true`, show a pill "Already tracking · will associate" inside the card
  - Tap card → `onToggle(templateId)`
- Below cards: "+ Define a custom habit" text button — tap shows toast "Coming soon"
- Sticky bottom bar:
  - Left: "{N} habits selected" + sub "{M} already tracking" (where M = count of `checked && alreadyTracking`)
  - Right: filled button "Commit to {identityName.lowercase()}" → `onCommit()` (disabled while `state.isCommitting`)
- If `state.error != null`, show a `Snackbar` (or transient text) with the error message

- [ ] **Step 4: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL (now that AddIdentityScreen exists, the NavHost block from Task 8 Step 5 is satisfied — but Task 11 will re-add it).

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityScreen.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityStep1Screen.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/AddIdentityStep2Screen.kt
rtk git commit -m "$(cat <<'EOF'
feat(ui): AddIdentityFlow — Step1 picker + Step2 recommended habits

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: AppNavigation — wire AddIdentity composable

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Add `composable(Screen.AddIdentity.route) { ... }` block to NavHost**

Inside the `NavHost(...) { ... }`, add the block:

```kotlin
composable(Screen.AddIdentity.route) {
    val vm = viewModel { AddIdentityViewModel(container) }
    AddIdentityScreen(
        viewModel = vm,
        onClose = { navController.popBackStack() },
        onCommitSuccess = {
            navController.navigate(Screen.IdentityList.route) {
                popUpTo(Screen.IdentityList.route) { inclusive = false }
            }
        },
    )
}
```

Add imports: `com.jktdeveloper.habitto.ui.identity.AddIdentityViewModel`, `com.jktdeveloper.habitto.ui.identity.AddIdentityScreen`.

- [ ] **Step 2: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "$(cat <<'EOF'
feat(nav): mount AddIdentityScreen at Screen.AddIdentity

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: IdentityListScreen "Add identity" CTA + push_pin badge

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListViewModel.kt`

- [ ] **Step 1: Surface pinned identityId in `IdentityListViewModel` state**

The VM observes `userIdentitiesWithStats`. Add the pinned identityId to the state by reading the current row's `isPinned` flag from the DB. Two approaches:

A. Add a `getPinnedIdentityIdForUser(userId): String?` method to `IdentityRepository` and expose `pinnedIdentityId: String?` on the VM state.

B. Cleaner: extend `IdentityWithStats` (existing model in `IdentityStats.kt`) to carry `isPinned: Boolean`. Updated upstream from `LocalIdentityRepository.observeUserIdentities` returning the raw `LocalUserIdentity` row's flag.

Approach **A** is smaller-scope and touches less code. Implement A:

- Add to `IdentityRepository` interface: `suspend fun getPinnedIdentityIdForUser(userId: String): String?`
- Add `getPinnedIdentityIdForUser(userId)` SQL query to `HabitTrackerDatabase.sq`:
  ```sql
  getPinnedIdentityIdForUser:
  SELECT identityId FROM LocalUserIdentity WHERE userId = ? AND isPinned = 1 AND removedAt IS NULL LIMIT 1;
  ```
- Implement in `LocalIdentityRepository`:
  ```kotlin
  override suspend fun getPinnedIdentityIdForUser(userId: String): String? =
      q.getPinnedIdentityIdForUser(userId).executeAsOneOrNull()
  ```
- Implement in `FakeIdentityRepository`:
  ```kotlin
  override suspend fun getPinnedIdentityIdForUser(userId: String): String? =
      userIdentities[userId]?.firstOrNull { it.isPinned && it.removedAt == null }?.identityId
  ```
- In `IdentityListViewModel`, expose `pinnedIdentityId: String?` in state. Refresh on every `refresh()` call AND after pin/unpin actions (covered by IdentityDetail flows; also re-derive on resume since IdentityList re-fetches via the existing resume callback).

- [ ] **Step 2: Render "Add identity" CTA card**

In `IdentityListScreen.kt`, after the `LazyColumn { items(identities) { ... } }` block (or as the last item), add a dashed-border CTA card per design (screens.jsx:1924-1948):

```kotlin
item {
    OutlinedCard(
        onClick = onAddIdentityClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
        // dashed look: use a custom modifier or accept solid + describe-as-dashed
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(20.dp, 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Add identity", style = MaterialTheme.typography.titleSmall)
                Text("Pick from suggestions or define your own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```

Add `onAddIdentityClick: () -> Unit` parameter to `IdentityListScreen` Composable signature. Pass it through from `AppNavigation.kt`'s composable block:

```kotlin
composable(Screen.IdentityList.route) {
    val vm = viewModel { IdentityListViewModel(container) }
    IdentityListScreen(
        viewModel = vm,
        onIdentityClick = { id -> navController.navigate(Screen.IdentityDetail.route(id)) },
        onAddIdentityClick = { navController.navigate(Screen.AddIdentity.route) },
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 3: Render `push_pin` badge on pinned identity card**

In the `IdentityCard` composable (or wherever the row content is rendered), check if `identity.id == state.pinnedIdentityId`. If yes, show a small `push_pin` filled icon next to the identity name and tint the card's border with the identity's hue (per design line 1857-1875). Use `Icons.Filled.PushPin` or its outlined variant.

- [ ] **Step 4: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListScreen.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListViewModel.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt \
            mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq
rtk git commit -m "$(cat <<'EOF'
feat(ui): IdentityList Add CTA + pinned card badge

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: IdentityDetailViewModel — pin/remove/why actions + state

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModel.kt`

- [ ] **Step 1: Extend the ViewModel**

Modify the existing `IdentityDetailViewModel` to:

1. Inject the four new use cases via the constructor (`PinIdentityUseCase`, `UnpinIdentityUseCase`, `RemoveIdentityUseCase`, `UpdateIdentityWhyUseCase`) — populate via the existing `AppContainer` constructor. Add corresponding container properties.
2. Extend `IdentityDetailState.Loaded` with `isPinned: Boolean, whyText: String?, isEditingWhy: Boolean = false, pendingWhyDraft: String? = null`.
3. Add a `MutableSharedFlow<Unit> removeSuccess` event.
4. Add methods:

```kotlin
fun togglePin() {
    val loaded = _state.value as? IdentityDetailState.Loaded ?: return
    viewModelScope.launch {
        val userId = userIdProvider()
        if (loaded.isPinned) {
            unpinUseCase.execute(userId, identityId)
        } else {
            pinUseCase.execute(userId, identityId)
        }
        refresh()
    }
}

fun removeIdentity() {
    viewModelScope.launch {
        val userId = userIdProvider()
        runCatching { removeUseCase.execute(userId, identityId) }
            .onSuccess { _removeSuccess.tryEmit(Unit) }
    }
}

fun startEditingWhy() {
    val loaded = _state.value as? IdentityDetailState.Loaded ?: return
    _state.value = loaded.copy(isEditingWhy = true, pendingWhyDraft = loaded.whyText.orEmpty())
}

fun updateWhyDraft(text: String) {
    val loaded = _state.value as? IdentityDetailState.Loaded ?: return
    _state.value = loaded.copy(pendingWhyDraft = text)
}

fun saveWhyText() {
    val loaded = _state.value as? IdentityDetailState.Loaded ?: return
    viewModelScope.launch {
        val userId = userIdProvider()
        updateWhyUseCase.execute(userId, identityId, loaded.pendingWhyDraft)
        refresh()
    }
}

fun cancelEditingWhy() {
    val loaded = _state.value as? IdentityDetailState.Loaded ?: return
    _state.value = loaded.copy(isEditingWhy = false, pendingWhyDraft = null)
}
```

5. Update the `refresh()` body to also fetch `isPinned` (via `identityRepo.getPinnedIdentityIdForUser(userId) == identityId`) and read the row's `whyText`. Add a new `getUserIdentityRowFor(userId, identityId): UserIdentityRow?` method to the repo if needed, OR re-use `getUnsyncedUserIdentitiesFor` + filter (less clean).

Cleaner: add `getUserIdentityRow(userId, identityId): UserIdentityRow?` to repo:
- SQL: `getUserIdentityRow: SELECT * FROM LocalUserIdentity WHERE userId = ? AND identityId = ? LIMIT 1;`
- Impl: trivial.

Then in `refresh()`:
```kotlin
val row = identityRepo.getUserIdentityRow(userId, identityId)
val whyText = row?.whyText
val isPinned = row?.isPinned ?: false
```

- [ ] **Step 2: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModel.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/IdentityRepository.kt \
            mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalIdentityRepository.kt \
            mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeIdentityRepository.kt \
            mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq
rtk git commit -m "$(cat <<'EOF'
feat(ui): IdentityDetailViewModel — pin/remove/why actions + state

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: IdentityDetailScreen — pin button + remove button + reflection editor

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Add reflection editor section**

Per design (screens.jsx:2123-2139), add a "Why this identity" card section to the screen, between the linked-habits list and the manage-actions row:

```kotlin
@Composable
private fun WhyCard(
    whyText: String?,
    isEditing: Boolean,
    pendingDraft: String?,
    onStartEditing: () -> Unit,
    onUpdateDraft: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = 8.dp)) {
        Text("Why this identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (isEditing) {
            OutlinedTextField(
                value = pendingDraft.orEmpty(),
                onValueChange = onUpdateDraft,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3, maxLines = 6,
                placeholder = { Text("Why does this identity matter to you?") },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onSave) { Text("Save") }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = whyText?.let { "\"$it\"" } ?: "Tap edit to add a reflection.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    modifier = Modifier.padding(16.dp),
                )
            }
            TextButton(onClick = onStartEditing, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
```

- [ ] **Step 2: Add pin and remove buttons**

Per design (screens.jsx:2143-2151), add a Manage Actions section at the bottom of the scrollable content:

```kotlin
@Composable
private fun ManageActions(
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onTogglePin,
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            Icon(
                if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isPinned) "Unpin from Home" else "Pin to Home")
        }
        TextButton(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Outlined.RemoveCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Remove identity")
        }
        Text(
            "Removing keeps your habits — they stay associated with the identities they support.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

- [ ] **Step 3: Wire `removeSuccess` event in the screen + nav**

In `IdentityDetailScreen` body, observe the `removeSuccess` SharedFlow:

```kotlin
LaunchedEffect(Unit) {
    viewModel.removeSuccess.collect { onRemoveSuccess() }
}
```

Add `onRemoveSuccess: () -> Unit` parameter to the screen Composable. Pass through from `AppNavigation.kt`:

```kotlin
composable(Screen.IdentityDetail.route, ...) {
    // ... existing body ...
    IdentityDetailScreen(
        // ... existing args ...
        onRemoveSuccess = { navController.popBackStack() },
    )
}
```

- [ ] **Step 4: Render WhyCard + ManageActions in the scaffold**

Inside the existing IdentityDetailScreen content (after the linked-habits list, before any existing footer), call `WhyCard(...)` and `ManageActions(...)` passing the relevant state + VM action lambdas.

- [ ] **Step 5: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailScreen.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
rtk git commit -m "$(cat <<'EOF'
feat(ui): IdentityDetail pin/remove buttons + reflection editor

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Home strip — push_pin icon on pinned chip

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt` (or whatever file renders the identity strip)

- [ ] **Step 1: Surface `pinnedIdentityId` on `HomeUiState`**

Add `pinnedIdentityId: String? = null` to `HomeUiState`. Populate inside `observeHomeUiState` by calling `container.identityRepository.getPinnedIdentityIdForUser(userId)` once per emission of the day-flow + auth-flow (i.e. inside the `combine` block, fetch synchronously alongside other data).

Cleaner: add a separate Flow for the pin observation and merge into the state. Simpler: since `observeUserIdentities` already emits, extend the combine to read `getPinnedIdentityIdForUser` (which is suspend, not flow) — call it inside the lambda and pass to UI state.

- [ ] **Step 2: Render `push_pin` icon on the pinned chip**

In the identity strip Composable (likely `IdentityStrip.kt` or inline in `HomeScreen.kt`), find the per-identity chip render and add a `push_pin` (filled, 11dp) inline with the identity name when `identity.id == state.pinnedIdentityId`. Tint with the identity's hue.

- [ ] **Step 3: Build to verify**

```bash
rtk ./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt \
            mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt
rtk git commit -m "$(cat <<'EOF'
feat(home): push_pin icon on pinned identity chip in strip

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: Manual smoke + Supabase migration + PR

**Files:** none (validation + git ops).

- [ ] **Step 1: Apply Supabase migration**

In Supabase dashboard → SQL Editor, run:

```sql
ALTER TABLE public.user_identities ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.user_identities ADD COLUMN why_text TEXT;
ALTER TABLE public.user_identities ADD COLUMN removed_at TIMESTAMPTZ;
```

Verify via:
```sql
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'user_identities';
```

Expect 7 rows.

- [ ] **Step 2: Run full test suite**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase5c-2-identity-crud
rtk ./gradlew :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest
```

Expected: all tests pass (existing + 14 new ones across 5 use case test files).

- [ ] **Step 3: Build + install debug APK**

```bash
rtk ./gradlew :mobile:androidApp:installDebug
```

- [ ] **Step 4: Smoke checklist**

- [ ] App launches, existing identities present (data migrated from version 1 → 2 cleanly).
- [ ] Open IdentityList → "Add identity" CTA card visible at bottom of active identities.
- [ ] Tap CTA → AddIdentityFlow step 1. Candidates = unused templates. Pick one (not the pre-selected) → continue button label updates.
- [ ] Tap "Custom" tile → toast "Coming soon", no nav.
- [ ] Tap Continue → step 2 with all recommended habits checked by default.
- [ ] If a recommended habit's templateId already exists in user's habits → "Already tracking · will associate" pill rendered.
- [ ] Tap "+ Define a custom habit" → toast "Coming soon", no nav.
- [ ] Toggle a couple of habits off → bottom counter updates.
- [ ] Tap Commit → return to IdentityList. New identity present.
- [ ] Open IdentityDetail of new identity → linked habits include the ones selected. Existing habits with matching templateId are also linked (no duplicates in the user's habit list — verify Home).
- [ ] Tap "Pin to Home" on IdentityDetail → button label flips to "Unpin from Home", icon fills.
- [ ] Back to Home → pinned chip in strip shows `push_pin` icon.
- [ ] Back to IdentityList → pinned card shows hue-tinted border + `push_pin` icon.
- [ ] Pin a different identity → previous unpins automatically (single-pin invariant).
- [ ] "Why this identity" section shows placeholder "Tap edit to add a reflection." Tap Edit → text field appears. Type a reflection. Tap Save → display flips back, italic quoted text rendered.
- [ ] Tap Edit again → field pre-populated with current reflection. Clear all text. Tap Save → placeholder reappears (whitespace/empty normalizes to NULL).
- [ ] Tap Cancel during edit → field discards, no save.
- [ ] Tap "Remove identity" → IdentityDetail closes, IdentityList no longer shows the identity. Home strip removes the chip. If removed identity was pinned, no other becomes pinned automatically.
- [ ] Linked-only habits remain in the user's habit list on Home (orphan stays active per Q4 C1).
- [ ] Multi-device: sign in same account on second emulator. Trigger sync (pull-refresh). New identity, pin state, reflection, and removal all appear.

- [ ] **Step 5: Push branch**

```bash
rtk git push -u origin feature/phase5c-2-identity-crud
```

- [ ] **Step 6: Create PR**

```bash
rtk gh pr create --base main --head feature/phase5c-2-identity-crud \
    --title "Phase 5c-2: identity CRUD (add/pin/remove/reflection)" \
    --body "$(cat <<'BODY'
## Summary

Ships the identity write-paths.

- **AddIdentityFlow** — 2-step wizard (template picker → recommended habits). Custom identity + custom habit deferred to followup phases (tiles render, taps show "Coming soon" toast).
- **Pin / unpin** — single featured identity, server-synced. Pinned chip on Home strip + IdentityList card.
- **Remove identity** — soft-delete via `removed_at`. No confirm dialog (single tap). Orphan habits remain active per design decision.
- **"Why this identity"** — inline reflection editor, server-synced.

## Schema

Single migration adds three columns to `user_identities`:
- `is_pinned BOOLEAN NOT NULL DEFAULT FALSE`
- `why_text TEXT`
- `removed_at TIMESTAMPTZ`

Local migration: `2.sqm`. Supabase migration applied via dashboard.

## Out of scope

- Custom identity (user-defined name/icon/hue) — followup phase
- Custom habit creation inside step 2 — Habit CRUD phase
- Past-identities collapsed section on IdentityList — future
- Streak engine changes — orphan habits remain required for daily-complete rule

## Test plan
- [x] `:mobile:shared:testDebugUnitTest` green (incl. 14 new use case tests)
- [x] `:mobile:androidApp:testDebugUnitTest` green
- [x] Manual: AddIdentityFlow end-to-end
- [x] Manual: pin/unpin reflects on Home + IdentityList
- [x] Manual: remove identity propagates everywhere
- [x] Manual: reflection editor save/cancel/whitespace-normalize
- [x] Manual: multi-device sync

🤖 Generated with [Claude Code](https://claude.com/claude-code)
BODY
)"
```

---

## Self-Review

**Spec coverage check:**
- Schema migration (3 new columns, both local + Supabase) → Task 1, Task 16 step 1
- UserIdentityRow data class extension → Task 2
- IdentityRepository interface methods (5 new) → Task 2
- LocalIdentityRepository impls + transaction wrapper → Task 2
- Sync DTO mapper → Task 3
- AddIdentityWithHabitsUseCase + tests → Task 7
- PinIdentityUseCase + UnpinIdentityUseCase + tests → Task 4
- RemoveIdentityUseCase + test → Task 5
- UpdateIdentityWhyUseCase + test (incl whitespace normalization) → Task 6
- AppContainer wiring → Task 8
- Screen.AddIdentity route → Task 8 + Task 11
- AddIdentityViewModel → Task 9
- AddIdentityStep1Screen + AddIdentityStep2Screen → Task 10
- IdentityList "Add identity" CTA → Task 12
- IdentityList pin badge → Task 12
- IdentityDetailViewModel extensions (pin/remove/why) → Task 13
- IdentityDetailScreen pin/remove buttons + reflection editor → Task 14
- Home strip pin icon → Task 15
- Manual smoke + Supabase migration → Task 16

**Placeholder scan:** none.

**Type consistency:**
- Use cases all expose `suspend fun execute(...)`. Consistent.
- `setPinAtomically` / `setPinForIdentity` / `clearPinForUser` names consistent across interface, LocalIdentityRepository, FakeIdentityRepository, and use case call sites.
- `markUserIdentityRemoved` name + signature `(userId, identityId, removedAt: Instant)` consistent.
- `updateWhyText` name + signature `(userId, identityId, whyText: String?)` consistent.
- DTO field names match SQL column names (`is_pinned`, `why_text`, `removed_at`) and Kotlin field names (`isPinned`, `whyText`, `removedAt`).
- `HabitChoice` data class defined in Task 9 and used in Task 10 — same shape.
- `Screen.AddIdentity` route string `"add_identity"` consistent across Task 8 declaration and Task 11 mounting.
