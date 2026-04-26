# Phase 3 — Sync + Auth Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the sync layer so local habit data survives reinstall and flows across devices, extend auth with Google OAuth + graceful email-confirmation handling, and ship a logout flow.

**Architecture:** A `SyncEngine` in the shared KMP module coordinates push + pull against a `SupabaseSyncClient` (Postgrest wrapper). Local repositories stay CRUD-only. An Android-only `SyncWorker` wraps the engine for WorkManager triggers. `SupabaseAuthRepository.signUp` returns a sealed `SignUpResult` and gains Google OAuth via an expect/actual bridge.

**Tech Stack:** Kotlin Multiplatform · SQLDelight 2.0.2 · supabase-kt 3.0.2 (auth + postgrest) · Ktor 3.0.3 · kotlinx-datetime · Compose Material 3 · AndroidX WorkManager · Android SharedPreferences / iOS NSUserDefaults (expect/actual) · WCAG-compliant Material 3 tokens.

**Reference spec:** `docs/superpowers/specs/2026-04-23-phase3-sync-auth-design.md`

---

## File Structure

### Create

- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncEngine.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncTypes.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SupabaseSyncClient.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SyncWatermarkStore.kt` *(expect)*
- `mobile/shared/src/androidMain/kotlin/com/habittracker/data/local/SyncWatermarkStore.android.kt`
- `mobile/shared/src/iosMain/kotlin/com/habittracker/data/local/SyncWatermarkStore.ios.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SignUpResult.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/remote/GoogleSignInLauncher.kt` *(expect)*
- `mobile/shared/src/androidMain/kotlin/com/habittracker/data/remote/GoogleSignInLauncher.android.kt`
- `mobile/shared/src/iosMain/kotlin/com/habittracker/data/remote/GoogleSignInLauncher.ios.kt` *(stub)*
- `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/1.sqm`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/FakeSupabaseSyncClient.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/SyncEngineTest.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/SupabaseAuthRepositoryResultTest.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/sync/SyncWorker.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/sync/SyncTriggers.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/LogoutDialog.kt`
- `supabase/migrations/20260423000000_sync_hardening.sql`

### Modify

- `gradle/libs.versions.toml` — add workmanager, supabase-oauth (if separate artifact), androidx credentials (for Google One Tap)
- `mobile/shared/build.gradle.kts` — bump SQLDelight database version, declare migration folder
- `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitLogRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitLogRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantLogRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantLogRepository.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitRepository.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitLogRepository.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeWantActivityRepository.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeWantLogRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/AuthRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SupabaseAuthRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Habit.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCase.kt`
- `mobile/androidApp/build.gradle.kts` — BuildConfig for Google client ID, workmanager + credentials deps
- `mobile/androidApp/src/androidMain/AndroidManifest.xml` — deep link intent-filter on MainActivity
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/AppContainer.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/MainActivity.kt` — trigger foreground sync on onResume
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/AuthViewModel.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/AuthScreen.kt` — Google button + ConfirmationRequired snackbar
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeViewModel.kt` — enqueue post-log, trigger manual sync, logout flow
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeScreen.kt` — sync badge + overflow menu
- `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/navigation/AppNavigation.kt` — 2 s hybrid restore

---

## Task 1: Project prerequisites

**Files:**
- Modify: `mobile/androidApp/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `mobile/androidApp/src/androidMain/AndroidManifest.xml`

> **Human prerequisites (do these before starting coding):**
> 1. Google Cloud Console → new OAuth 2.0 Client ID, type "Android". Set package name `com.habittracker.android` and the debug SHA-1 (`./gradlew signingReport` for the value).
> 2. Create a second OAuth 2.0 Client ID of type "Web application" — this is the audience for the ID token. Copy the Web Client ID.
> 3. Supabase Dashboard → Authentication → Providers → Google → enable, paste **Web Client ID** + its Client Secret. Save.
> 4. Supabase Dashboard → Authentication → URL Configuration → Additional Redirect URLs → add `com.habittracker.android://auth-callback`.
> 5. `local.properties` (gitignored) add: `google.web_client_id=YOUR_WEB_CLIENT_ID`.

- [ ] **Step 1: Add dependency versions to `gradle/libs.versions.toml`**

Append to `[versions]`:
```toml
workmanager = "2.10.0"
credentials = "1.5.0"
```

Append to `[libraries]`:
```toml
androidx-work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "workmanager" }
androidx-credentials = { module = "androidx.credentials:credentials", version.ref = "credentials" }
androidx-credentials-play-services = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "credentials" }
googleid = { module = "com.google.android.libraries.identity.googleid:googleid", version = "1.1.1" }
```

- [ ] **Step 2: Wire `GOOGLE_WEB_CLIENT_ID` BuildConfig field in `mobile/androidApp/build.gradle.kts`**

Inside `defaultConfig { ... }` add:
```kotlin
buildConfigField(
    "String",
    "GOOGLE_WEB_CLIENT_ID",
    "\"${localProps.getProperty("google.web_client_id", "")}\"",
)
```

Inside `dependencies { ... }` add:
```kotlin
implementation(libs.androidx.work.runtime)
implementation(libs.androidx.credentials)
implementation(libs.androidx.credentials.play.services)
implementation(libs.googleid)
```

- [ ] **Step 3: Add deep-link intent-filter to `mobile/androidApp/src/androidMain/AndroidManifest.xml`**

Inside the existing `<activity android:name=".MainActivity" …>` block, append another `<intent-filter>`:
```xml
<intent-filter android:autoVerify="false">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="com.habittracker.android" android:host="auth-callback" />
</intent-filter>
```

Also set `android:launchMode="singleTop"` on the MainActivity tag so the callback doesn't spawn a new instance.

- [ ] **Step 4: Build + confirm**

Run: `./gradlew :mobile:androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL. BuildConfig class has `GOOGLE_WEB_CLIENT_ID` field. WorkManager + Credentials jars in the APK dependency graph.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml mobile/androidApp/build.gradle.kts mobile/androidApp/src/androidMain/AndroidManifest.xml
git commit -m "feat: add workmanager + credentials deps, Google client ID BuildConfig, OAuth deep link"
```

---

## Task 2: Supabase remote migration

**Files:**
- Create: `supabase/migrations/20260423000000_sync_hardening.sql`

- [ ] **Step 1: Write the migration SQL**

```sql
-- Phase 3: add updated_at to mutable rows, auto-stamp via trigger
alter table if exists habits
    add column if not exists updated_at timestamptz not null default now();

alter table if exists want_activities
    add column if not exists updated_at timestamptz not null default now();

create or replace function touch_updated_at()
returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

drop trigger if exists habits_touch_updated_at on habits;
create trigger habits_touch_updated_at
    before update on habits
    for each row execute function touch_updated_at();

drop trigger if exists want_activities_touch_updated_at on want_activities;
create trigger want_activities_touch_updated_at
    before update on want_activities
    for each row execute function touch_updated_at();
```

- [ ] **Step 2: Apply the migration to the Supabase project**

Open the Supabase dashboard → SQL Editor → New query → paste the file contents → Run.

Expected output: `Success. No rows returned.`

- [ ] **Step 3: Verify columns exist**

In the SQL Editor run:
```sql
select column_name from information_schema.columns
where table_name in ('habits', 'want_activities') and column_name = 'updated_at';
```

Expected: two rows (one per table).

- [ ] **Step 4: Commit**

```bash
git add supabase/migrations/20260423000000_sync_hardening.sql
git commit -m "feat(db): add updated_at + auto-touch trigger for habits + want_activities"
```

---

## Task 3: Local schema — add `updatedAt` + SQLDelight migration

**Files:**
- Modify: `mobile/shared/build.gradle.kts`
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`
- Create: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/1.sqm`

- [ ] **Step 1: Declare schema version in `mobile/shared/build.gradle.kts`**

Find the `sqldelight { databases { create("HabitTrackerDatabase") { … } } }` block and set:
```kotlin
sqldelight {
    databases {
        create("HabitTrackerDatabase") {
            packageName.set("com.habittracker.data.local")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
```

- [ ] **Step 2: Add `updatedAt` columns to the schema**

Edit `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`:

Change the `CREATE TABLE IF NOT EXISTS LocalHabit` block to end with:
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
    updatedAt INTEGER NOT NULL
);
```

Change `CREATE TABLE IF NOT EXISTS LocalWantActivity` to:
```sql
CREATE TABLE IF NOT EXISTS LocalWantActivity (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    costPerUnit REAL NOT NULL,
    isCustom INTEGER NOT NULL DEFAULT 0,
    updatedAt INTEGER NOT NULL
);
```

Change `upsertHabit` to include `updatedAt`:
```sql
upsertHabit:
INSERT OR REPLACE INTO LocalHabit (id, userId, templateId, name, unit, thresholdPerPoint, dailyTarget, createdAt, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
```

Change `upsertWantActivity`:
```sql
upsertWantActivity:
INSERT OR REPLACE INTO LocalWantActivity (id, userId, name, unit, costPerUnit, isCustom, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?);
```

Append new sync helper queries at the end of the file:
```sql
-- Phase 3 sync helpers

getUnsyncedHabits:
SELECT * FROM LocalHabit WHERE userId = ? AND updatedAt > COALESCE(
    (SELECT value FROM SyncWatermarkDummy WHERE key = 'habits_push'), 0
);

-- Logs: filter on syncedAt IS NULL
getUnsyncedHabitLogsForUser:
SELECT * FROM HabitLog WHERE userId = ? AND syncedAt IS NULL;

getUnsyncedWantLogsForUser:
SELECT * FROM WantLog WHERE userId = ? AND syncedAt IS NULL;

getUnsyncedWantActivitiesForUser:
SELECT * FROM LocalWantActivity WHERE userId = ? AND updatedAt > COALESCE(
    (SELECT value FROM SyncWatermarkDummy WHERE key = 'want_activities_push'), 0
);

markHabitsSynced:
UPDATE LocalHabit SET updatedAt = updatedAt WHERE id IN ?;

markWantActivitiesSynced:
UPDATE LocalWantActivity SET updatedAt = updatedAt WHERE id IN ?;

getHabitsByIdForUser:
SELECT * FROM LocalHabit WHERE userId = ? AND id IN ?;

getWantActivitiesByIdForUser:
SELECT * FROM LocalWantActivity WHERE userId = ? AND id IN ?;
```

Note: `SyncWatermarkDummy` is not a real table — we use the dedicated `SyncWatermarkStore` (Task 5) for pull watermarks, and for mutable-row push we key off `synced_at IS NULL`. Replace the mutable-row push queries above with the cleaner version below:

Delete the three queries referencing `SyncWatermarkDummy` and replace with:
```sql
getUnsyncedHabitsForUser:
SELECT * FROM LocalHabit WHERE userId = ? AND syncedAt IS NULL;

getUnsyncedWantActivitiesForUser:
SELECT * FROM LocalWantActivity WHERE userId = ? AND syncedAt IS NULL;
```

For this to work, add `syncedAt INTEGER` nullable columns to both tables. Update the table definitions:
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
    syncedAt INTEGER
);

CREATE TABLE IF NOT EXISTS LocalWantActivity (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    costPerUnit REAL NOT NULL,
    isCustom INTEGER NOT NULL DEFAULT 0,
    updatedAt INTEGER NOT NULL,
    syncedAt INTEGER
);
```

Update `upsertHabit` + `upsertWantActivity` to clear `syncedAt` on save (local mutation needs push again):
```sql
upsertHabit:
INSERT OR REPLACE INTO LocalHabit (id, userId, templateId, name, unit, thresholdPerPoint, dailyTarget, createdAt, updatedAt, syncedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL);

upsertWantActivity:
INSERT OR REPLACE INTO LocalWantActivity (id, userId, name, unit, costPerUnit, isCustom, updatedAt, syncedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, NULL);
```

Append mark-synced queries:
```sql
markHabitSynced:
UPDATE LocalHabit SET syncedAt = ? WHERE id = ?;

markWantActivitySynced:
UPDATE LocalWantActivity SET syncedAt = ? WHERE id = ?;
```

Append pull-insert queries (distinct names from upsert since pull bypasses the `syncedAt = NULL` reset):
```sql
mergePulledHabit:
INSERT OR REPLACE INTO LocalHabit (id, userId, templateId, name, unit, thresholdPerPoint, dailyTarget, createdAt, updatedAt, syncedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

mergePulledWantActivity:
INSERT OR REPLACE INTO LocalWantActivity (id, userId, name, unit, costPerUnit, isCustom, updatedAt, syncedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);
```

- [ ] **Step 3: Create the migration file `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/1.sqm`**

```sql
-- Migrate HabitTrackerDatabase from version 0 → 1
-- Adds updatedAt + syncedAt columns to LocalHabit and LocalWantActivity.

ALTER TABLE LocalHabit ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE LocalHabit ADD COLUMN syncedAt INTEGER;
UPDATE LocalHabit SET updatedAt = createdAt WHERE updatedAt = 0;

ALTER TABLE LocalWantActivity ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE LocalWantActivity ADD COLUMN syncedAt INTEGER;
UPDATE LocalWantActivity SET updatedAt = strftime('%s', 'now') * 1000 WHERE updatedAt = 0;
```

- [ ] **Step 4: Generate + compile**

Run: `./gradlew :mobile:shared:generateCommonMainHabitTrackerDatabaseInterface`
Expected: BUILD SUCCESSFUL. No migration conflicts.

Run: `./gradlew :mobile:shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. Compilation will fail in impl files that call old `upsertHabit`/`upsertWantActivity` — next task fixes that.

- [ ] **Step 5: Commit**

```bash
git add mobile/shared/build.gradle.kts mobile/shared/src/commonMain/sqldelight
git commit -m "feat(db): add updatedAt + syncedAt columns + 0→1 migration"
```

---

## Task 4: Domain models gain `updatedAt` + `syncedAt`

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Habit.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt`

- [ ] **Step 1: Update `Habit.kt`**

Replace the whole file:
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
)
```

- [ ] **Step 2: Update `WantActivity.kt`**

Replace:
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
)
```

(`updatedAt` default keeps the hardcoded `SeedData` list compiling without churn; `SetupUserWantActivitiesUseCase` will stamp real value before saving — see Task 6.)

- [ ] **Step 3: Compile**

Run: `./gradlew :mobile:shared:compileDebugKotlinAndroid`
Expected: fails in `LocalHabitRepository`, `LocalWantActivityRepository`, use cases, fakes — all fixed in Task 5 + 6.

- [ ] **Step 4: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/Habit.kt mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantActivity.kt
git commit -m "feat: add updatedAt + syncedAt to Habit and WantActivity domain models"
```

---

## Task 5: Repository CRUD updates + sync helpers

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/HabitLogRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalHabitLogRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantActivityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantActivityRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantLogRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantLogRepository.kt`

- [ ] **Step 1: Extend `HabitRepository` interface**

Append to the interface:
```kotlin
suspend fun getUnsyncedFor(userId: String): List<Habit>
suspend fun markSynced(id: String, syncedAt: Instant)
suspend fun getByIdsForUser(userId: String, ids: List<String>): List<Habit>
suspend fun mergePulled(row: Habit)
```

Final file contents:
```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import kotlinx.datetime.Instant

interface HabitRepository {
    suspend fun getHabitsForUser(userId: String): List<Habit>
    fun observeHabitsForUser(userId: String): kotlinx.coroutines.flow.Flow<List<Habit>>
    suspend fun saveHabit(habit: Habit)
    suspend fun deleteHabit(habitId: String, userId: String)
    suspend fun migrateUserId(oldUserId: String, newUserId: String)
    suspend fun clearForUser(userId: String)

    suspend fun getUnsyncedFor(userId: String): List<Habit>
    suspend fun markSynced(id: String, syncedAt: Instant)
    suspend fun getByIdsForUser(userId: String, ids: List<String>): List<Habit>
    suspend fun mergePulled(row: Habit)
}
```

- [ ] **Step 2: Update `LocalHabitRepository` impl**

Replace the whole file:
```kotlin
package com.habittracker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalHabit
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class LocalHabitRepository(
    private val db: HabitTrackerDatabase,
) : HabitRepository {

    override suspend fun getHabitsForUser(userId: String): List<Habit> =
        db.habitTrackerDatabaseQueries
            .getHabitsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> =
        db.habitTrackerDatabaseQueries
            .getHabitsForUser(userId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun saveHabit(habit: Habit) {
        val now = habit.updatedAt.takeIf { it.toEpochMilliseconds() > 0L } ?: Clock.System.now()
        db.habitTrackerDatabaseQueries.upsertHabit(
            id = habit.id,
            userId = habit.userId,
            templateId = habit.templateId,
            name = habit.name,
            unit = habit.unit,
            thresholdPerPoint = habit.thresholdPerPoint,
            dailyTarget = habit.dailyTarget.toLong(),
            createdAt = habit.createdAt.toEpochMilliseconds(),
            updatedAt = now.toEpochMilliseconds(),
        )
    }

    override suspend fun deleteHabit(habitId: String, userId: String) {
        db.habitTrackerDatabaseQueries.deleteHabit(id = habitId, userId = userId)
    }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        db.habitTrackerDatabaseQueries.migrateHabitsUserId(newUserId, oldUserId)
    }

    override suspend fun clearForUser(userId: String) {
        db.habitTrackerDatabaseQueries.clearHabitsForUser(userId)
    }

    override suspend fun getUnsyncedFor(userId: String): List<Habit> =
        db.habitTrackerDatabaseQueries
            .getUnsyncedHabitsForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun markSynced(id: String, syncedAt: Instant) {
        db.habitTrackerDatabaseQueries.markHabitSynced(syncedAt.toEpochMilliseconds(), id)
    }

    override suspend fun getByIdsForUser(userId: String, ids: List<String>): List<Habit> {
        if (ids.isEmpty()) return emptyList()
        return db.habitTrackerDatabaseQueries
            .getHabitsByIdForUser(userId, ids)
            .executeAsList()
            .map { it.toDomain() }
    }

    override suspend fun mergePulled(row: Habit) {
        db.habitTrackerDatabaseQueries.mergePulledHabit(
            id = row.id,
            userId = row.userId,
            templateId = row.templateId,
            name = row.name,
            unit = row.unit,
            thresholdPerPoint = row.thresholdPerPoint,
            dailyTarget = row.dailyTarget.toLong(),
            createdAt = row.createdAt.toEpochMilliseconds(),
            updatedAt = row.updatedAt.toEpochMilliseconds(),
            syncedAt = row.syncedAt?.toEpochMilliseconds(),
        )
    }
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

- [ ] **Step 3: Extend `HabitLogRepository` + `LocalHabitLogRepository`**

`HabitLogRepository.kt` — add interface methods:
```kotlin
suspend fun getUnsyncedFor(userId: String): List<HabitLog>
suspend fun markSynced(id: String, syncedAt: Instant)
suspend fun mergePulled(row: HabitLog)
```

`LocalHabitLogRepository.kt` — append methods inside the class:
```kotlin
override suspend fun getUnsyncedFor(userId: String): List<HabitLog> =
    db.habitTrackerDatabaseQueries
        .getUnsyncedHabitLogsForUser(userId)
        .executeAsList()
        .map { it.toDomain() }

override suspend fun markSynced(id: String, syncedAt: Instant) {
    db.habitTrackerDatabaseQueries.markHabitLogSynced(syncedAt.toEpochMilliseconds(), id)
}

override suspend fun mergePulled(row: HabitLog) {
    db.habitTrackerDatabaseQueries.insertHabitLog(
        id = row.id,
        userId = row.userId,
        habitId = row.habitId,
        quantity = row.quantity,
        loggedAt = row.loggedAt.toEpochMilliseconds(),
        deletedAt = row.deletedAt?.toEpochMilliseconds(),
        syncedAt = row.syncedAt?.toEpochMilliseconds(),
    )
}
```

`HabitTrackerDatabase.sq` already has `getUnsyncedHabitLogs` — verify the query name matches `getUnsyncedHabitLogsForUser` as declared in Task 3. If Task 3 used a different name, rename the query there to match.

- [ ] **Step 4: Extend `WantActivityRepository` + impl**

`WantActivityRepository.kt`:
```kotlin
suspend fun getUnsyncedFor(userId: String): List<WantActivity>
suspend fun markSynced(id: String, syncedAt: Instant)
suspend fun getByIdsForUser(userId: String, ids: List<String>): List<WantActivity>
suspend fun mergePulled(row: WantActivity)
```

`LocalWantActivityRepository.kt` — replace `saveWantActivity` to stamp `updatedAt` and add new methods:
```kotlin
override suspend fun saveWantActivity(activity: WantActivity, userId: String) {
    val now = activity.updatedAt.takeIf { it.toEpochMilliseconds() > 0L } ?: Clock.System.now()
    db.habitTrackerDatabaseQueries.upsertWantActivity(
        id = activity.id,
        userId = userId,
        name = activity.name,
        unit = activity.unit,
        costPerUnit = activity.costPerUnit,
        isCustom = if (activity.isCustom) 1L else 0L,
        updatedAt = now.toEpochMilliseconds(),
    )
}

override suspend fun getUnsyncedFor(userId: String): List<WantActivity> =
    db.habitTrackerDatabaseQueries
        .getUnsyncedWantActivitiesForUser(userId)
        .executeAsList()
        .map { it.toDomain() }

override suspend fun markSynced(id: String, syncedAt: Instant) {
    db.habitTrackerDatabaseQueries.markWantActivitySynced(syncedAt.toEpochMilliseconds(), id)
}

override suspend fun getByIdsForUser(userId: String, ids: List<String>): List<WantActivity> {
    if (ids.isEmpty()) return emptyList()
    return db.habitTrackerDatabaseQueries
        .getWantActivitiesByIdForUser(userId, ids)
        .executeAsList()
        .map { it.toDomain() }
}

override suspend fun mergePulled(row: WantActivity) {
    db.habitTrackerDatabaseQueries.mergePulledWantActivity(
        id = row.id,
        userId = row.createdByUserId,
        name = row.name,
        unit = row.unit,
        costPerUnit = row.costPerUnit,
        isCustom = if (row.isCustom) 1L else 0L,
        updatedAt = row.updatedAt.toEpochMilliseconds(),
        syncedAt = row.syncedAt?.toEpochMilliseconds(),
    )
}
```

Update the private `toDomain` extension to include `updatedAt`:
```kotlin
private fun LocalWantActivity.toDomain(): WantActivity = WantActivity(
    id = id,
    name = name,
    unit = unit,
    costPerUnit = costPerUnit,
    isCustom = isCustom == 1L,
    createdByUserId = userId,
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    syncedAt = syncedAt?.let { Instant.fromEpochMilliseconds(it) },
)
```

Add import: `import kotlinx.datetime.Clock` + `import kotlinx.datetime.Instant`.

- [ ] **Step 5: Extend `WantLogRepository` + impl**

`WantLogRepository.kt`:
```kotlin
suspend fun getUnsyncedFor(userId: String): List<WantLog>
suspend fun markSynced(id: String, syncedAt: Instant)
suspend fun mergePulled(row: WantLog)
```

`LocalWantLogRepository.kt` — append:
```kotlin
override suspend fun getUnsyncedFor(userId: String): List<WantLog> =
    db.habitTrackerDatabaseQueries
        .getUnsyncedWantLogsForUser(userId)
        .executeAsList()
        .map { it.toDomain() }

override suspend fun markSynced(id: String, syncedAt: Instant) {
    db.habitTrackerDatabaseQueries.markWantLogSynced(syncedAt.toEpochMilliseconds(), id)
}

override suspend fun mergePulled(row: WantLog) {
    db.habitTrackerDatabaseQueries.insertWantLog(
        id = row.id,
        userId = row.userId,
        activityId = row.activityId,
        quantity = row.quantity,
        deviceMode = row.deviceMode.toDbValue(),
        loggedAt = row.loggedAt.toEpochMilliseconds(),
        deletedAt = row.deletedAt?.toEpochMilliseconds(),
        syncedAt = row.syncedAt?.toEpochMilliseconds(),
    )
}
```

- [ ] **Step 6: Compile**

Run: `./gradlew :mobile:shared:compileDebugKotlinAndroid`
Expected: fails in fakes + use cases. Next task fixes those.

- [ ] **Step 7: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository
git commit -m "feat: repos gain getUnsyncedFor/markSynced/mergePulled + updatedAt"
```

---

## Task 6: Use cases + fakes catch-up

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserHabitsUseCase.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/SetupUserWantActivitiesUseCase.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitRepository.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeHabitLogRepository.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeWantActivityRepository.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeWantLogRepository.kt`

- [ ] **Step 1: Update `SetupUserHabitsUseCase` — stamp `updatedAt`**

Inside the `templates.forEach { template -> habitRepository.saveHabit(Habit(…)) }` block change the `Habit(…)` construction to include `updatedAt = now`:
```kotlin
habitRepository.saveHabit(
    Habit(
        id = Uuid.random().toString(),
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
```

- [ ] **Step 2: Update `SetupUserWantActivitiesUseCase` — stamp activities**

Inside the `activities.forEach { activity -> … }` block change to:
```kotlin
val now = Clock.System.now()
activities.forEach { activity ->
    wantActivityRepository.saveWantActivity(activity.copy(updatedAt = now), userId)
}
```

- [ ] **Step 3: Update `FakeHabitRepository`**

Replace the class body to match the expanded interface:
```kotlin
class FakeHabitRepository : HabitRepository {
    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: List<Habit> get() = _habits.value

    override suspend fun getHabitsForUser(userId: String): List<Habit> =
        _habits.value.filter { it.userId == userId }

    override fun observeHabitsForUser(userId: String): Flow<List<Habit>> =
        _habits.map { list -> list.filter { it.userId == userId } }

    override suspend fun saveHabit(habit: Habit) {
        _habits.value = _habits.value.filterNot { it.id == habit.id } + habit
    }

    override suspend fun deleteHabit(habitId: String, userId: String) {
        _habits.value = _habits.value.filterNot { it.id == habitId && it.userId == userId }
    }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        _habits.value = _habits.value.map {
            if (it.userId == oldUserId) it.copy(userId = newUserId) else it
        }
    }

    override suspend fun clearForUser(userId: String) {
        _habits.value = _habits.value.filterNot { it.userId == userId }
    }

    override suspend fun getUnsyncedFor(userId: String): List<Habit> =
        _habits.value.filter { it.userId == userId && it.syncedAt == null }

    override suspend fun markSynced(id: String, syncedAt: Instant) {
        _habits.value = _habits.value.map {
            if (it.id == id) it.copy(syncedAt = syncedAt) else it
        }
    }

    override suspend fun getByIdsForUser(userId: String, ids: List<String>): List<Habit> =
        _habits.value.filter { it.userId == userId && it.id in ids }

    override suspend fun mergePulled(row: Habit) {
        _habits.value = _habits.value.filterNot { it.id == row.id } + row
    }
}
```

Imports to add: `kotlinx.datetime.Instant`.

- [ ] **Step 4: Update `FakeHabitLogRepository`**

Append inside the class:
```kotlin
override suspend fun getUnsyncedFor(userId: String): List<HabitLog> =
    _logs.value.filter { it.userId == userId && it.syncedAt == null }

override suspend fun markSynced(id: String, syncedAt: Instant) {
    _logs.value = _logs.value.map {
        if (it.id == id) it.copy(syncedAt = syncedAt) else it
    }
}

override suspend fun mergePulled(row: HabitLog) {
    _logs.value = _logs.value.filterNot { it.id == row.id } + row
}
```

- [ ] **Step 5: Update `FakeWantActivityRepository`**

Append inside the class:
```kotlin
override suspend fun getUnsyncedFor(userId: String): List<WantActivity> =
    _activities.value.filter { (it.createdByUserId == userId || it.createdByUserId == null) && it.syncedAt == null }

override suspend fun markSynced(id: String, syncedAt: Instant) {
    _activities.value = _activities.value.map {
        if (it.id == id) it.copy(syncedAt = syncedAt) else it
    }
}

override suspend fun getByIdsForUser(userId: String, ids: List<String>): List<WantActivity> =
    _activities.value.filter {
        (it.createdByUserId == userId || it.createdByUserId == null) && it.id in ids
    }

override suspend fun mergePulled(row: WantActivity) {
    _activities.value = _activities.value.filterNot { it.id == row.id } + row
}
```

(If the fake stores activities as a `MutableStateFlow<List<WantActivity>>`, reference that. If it wraps an `AbstractMutableList`, add the same semantics going through the backing list.)

- [ ] **Step 6: Update `FakeWantLogRepository`**

Append:
```kotlin
override suspend fun getUnsyncedFor(userId: String): List<WantLog> =
    _logs.value.filter { it.userId == userId && it.syncedAt == null }

override suspend fun markSynced(id: String, syncedAt: Instant) {
    _logs.value = _logs.value.map {
        if (it.id == id) it.copy(syncedAt = syncedAt) else it
    }
}

override suspend fun mergePulled(row: WantLog) {
    _logs.value = _logs.value.filterNot { it.id == row.id } + row
}
```

- [ ] **Step 7: Compile + run existing tests**

```bash
./gradlew :mobile:shared:compileDebugKotlinAndroid
./gradlew :mobile:shared:allTests
```

Expected: all Phase 2 tests still pass (33 tests). Any test failing because a `Habit` now needs `updatedAt` gets the missing field backfilled with `Clock.System.now()` or `Instant.fromEpochMilliseconds(0L)`.

- [ ] **Step 8: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository
git commit -m "feat(test): fakes implement sync helpers; use cases stamp updatedAt"
```

---

## Task 7: `SyncWatermarkStore` + `SyncPreferences` (expect/actual)

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SyncPreferences.kt` *(expect)*
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SyncWatermarkStore.kt` *(plain class)*
- Create: `mobile/shared/src/androidMain/kotlin/com/habittracker/data/local/SyncPreferences.android.kt`
- Create: `mobile/shared/src/iosMain/kotlin/com/habittracker/data/local/SyncPreferences.ios.kt`

Splitting persistence (platform) from orchestration (common) keeps the engine testable — tests inject an in-memory `SyncPreferences` impl without the expect/actual plumbing.

- [ ] **Step 1: Write `SyncPreferences` expect**

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SyncPreferences.kt`:
```kotlin
package com.habittracker.data.local

/** Platform key/value persistence for small primitives. */
expect class SyncPreferences {
    fun getLong(key: String): Long
    fun putLong(key: String, value: Long)
}
```

- [ ] **Step 2: Write `SyncWatermarkStore` (common)**

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SyncWatermarkStore.kt`:
```kotlin
package com.habittracker.data.local

enum class SyncTable(val key: String) {
    HABITS("habits"),
    HABIT_LOGS("habit_logs"),
    WANT_ACTIVITIES("want_activities"),
    WANT_LOGS("want_logs"),
}

/** Persists the most recent pulled server timestamp per sync table. */
class SyncWatermarkStore(private val prefs: SyncPreferences) {
    fun get(table: SyncTable): Long = prefs.getLong(prefixed(table))
    fun set(table: SyncTable, valueMs: Long) = prefs.putLong(prefixed(table), valueMs)

    private fun prefixed(table: SyncTable) = "watermark.${table.key}"
}
```

- [ ] **Step 3: Android actual**

`mobile/shared/src/androidMain/kotlin/com/habittracker/data/local/SyncPreferences.android.kt`:
```kotlin
package com.habittracker.data.local

import android.content.Context

actual class SyncPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE,
    )

    actual fun getLong(key: String): Long = prefs.getLong(key, 0L)

    actual fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "habit_tracker_sync_prefs"
    }
}
```

- [ ] **Step 4: iOS actual**

`mobile/shared/src/iosMain/kotlin/com/habittracker/data/local/SyncPreferences.ios.kt`:
```kotlin
package com.habittracker.data.local

import platform.Foundation.NSUserDefaults

actual class SyncPreferences {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getLong(key: String): Long = defaults.integerForKey(key)
    actual fun putLong(key: String, value: Long) {
        defaults.setInteger(value, key)
    }
}
```

- [ ] **Step 5: Compile on both targets**

```bash
./gradlew :mobile:shared:compileDebugKotlinAndroid
./gradlew :mobile:shared:compileKotlinIosSimulatorArm64
```

Expected: BUILD SUCCESSFUL (both).

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/local mobile/shared/src/androidMain/kotlin/com/habittracker/data/local mobile/shared/src/iosMain/kotlin/com/habittracker/data/local
git commit -m "feat(sync): SyncPreferences expect/actual + SyncWatermarkStore wrapper"
```

---

## Task 8: `SyncTypes.kt`

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncTypes.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.habittracker.data.sync

import kotlinx.datetime.Instant

/** Why the sync is running — useful for telemetry / debug. */
enum class SyncReason {
    APP_FOREGROUND,
    POST_LOG,
    MANUAL,
    POST_SIGN_IN,
    WIDGET_WRITE,
}

/** Latest visible sync state for ViewModels to render. */
sealed class SyncState {
    object Idle : SyncState()
    data class Running(val since: Instant, val reason: SyncReason) : SyncState()
    data class Synced(val at: Instant, val pushed: Int, val pulled: Int) : SyncState()
    data class Error(val message: String, val since: Instant) : SyncState()
}

/** Outcome of a single sync attempt. */
data class SyncOutcome(val pushed: Int, val pulled: Int)
```

- [ ] **Step 2: Compile**

Run: `./gradlew :mobile:shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncTypes.kt
git commit -m "feat(sync): SyncReason, SyncState, SyncOutcome domain types"
```

---

## Task 9: `SupabaseSyncClient` interface + Postgrest impl

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SupabaseSyncClient.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt`

- [ ] **Step 1: Interface**

```kotlin
package com.habittracker.data.sync

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog

interface SupabaseSyncClient {
    suspend fun upsertHabit(row: Habit)
    suspend fun upsertWantActivity(row: WantActivity, ownerUserId: String)
    suspend fun upsertHabitLog(row: HabitLog)
    suspend fun upsertWantLog(row: WantLog)

    suspend fun fetchHabitsSince(userId: String, sinceMs: Long): List<Habit>
    suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity>
    suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog>
    suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog>
}
```

- [ ] **Step 2: Postgrest-backed implementation**

```kotlin
package com.habittracker.data.sync

import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

class PostgrestSupabaseSyncClient(
    private val supabase: SupabaseClient,
) : SupabaseSyncClient {

    override suspend fun upsertHabit(row: Habit) {
        supabase.postgrest.from("habits").upsert(row.toDto())
    }

    override suspend fun upsertWantActivity(row: WantActivity, ownerUserId: String) {
        supabase.postgrest.from("want_activities").upsert(row.toDto(ownerUserId))
    }

    override suspend fun upsertHabitLog(row: HabitLog) {
        supabase.postgrest.from("habit_logs").upsert(row.toDto())
    }

    override suspend fun upsertWantLog(row: WantLog) {
        supabase.postgrest.from("want_logs").upsert(row.toDto())
    }

    override suspend fun fetchHabitsSince(userId: String, sinceMs: Long): List<Habit> =
        supabase.postgrest.from("habits")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("updated_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("updated_at", Order.ASCENDING)
            }
            .decodeList<HabitDto>()
            .map { it.toDomain() }

    override suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity> =
        supabase.postgrest.from("want_activities")
            .select {
                filter {
                    or { eq("created_by_user_id", userId); isNull("created_by_user_id") }
                    gt("updated_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("updated_at", Order.ASCENDING)
            }
            .decodeList<WantActivityDto>()
            .map { it.toDomain() }

    override suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog> =
        supabase.postgrest.from("habit_logs")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("synced_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("synced_at", Order.ASCENDING)
            }
            .decodeList<HabitLogDto>()
            .map { it.toDomain() }

    override suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog> =
        supabase.postgrest.from("want_logs")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("synced_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("synced_at", Order.ASCENDING)
            }
            .decodeList<WantLogDto>()
            .map { it.toDomain() }
}

// ---- DTOs ---------------------------------------------------------------

@Serializable
private data class HabitDto(
    val id: String,
    val user_id: String,
    val template_id: String,
    val name: String,
    val unit: String,
    val threshold_per_point: Double,
    val daily_target: Int,
    val created_at: String,
    val updated_at: String,
)

private fun Habit.toDto() = HabitDto(
    id = id,
    user_id = userId,
    template_id = templateId,
    name = name,
    unit = unit,
    threshold_per_point = thresholdPerPoint,
    daily_target = dailyTarget,
    created_at = createdAt.toString(),
    updated_at = updatedAt.toString(),
)

private fun HabitDto.toDomain(): Habit = Habit(
    id = id,
    userId = user_id,
    templateId = template_id,
    name = name,
    unit = unit,
    thresholdPerPoint = threshold_per_point,
    dailyTarget = daily_target,
    createdAt = Instant.parse(created_at),
    updatedAt = Instant.parse(updated_at),
    syncedAt = Instant.parse(updated_at), // server updated_at doubles as local syncedAt
)

@Serializable
private data class WantActivityDto(
    val id: String,
    val created_by_user_id: String?,
    val name: String,
    val unit: String,
    val cost_per_unit: Double,
    val is_custom: Boolean,
    val updated_at: String,
)

private fun WantActivity.toDto(ownerUserId: String) = WantActivityDto(
    id = id,
    created_by_user_id = if (isCustom) ownerUserId else null,
    name = name,
    unit = unit,
    cost_per_unit = costPerUnit,
    is_custom = isCustom,
    updated_at = updatedAt.toString(),
)

private fun WantActivityDto.toDomain() = WantActivity(
    id = id,
    name = name,
    unit = unit,
    costPerUnit = cost_per_unit,
    isCustom = is_custom,
    createdByUserId = created_by_user_id,
    updatedAt = Instant.parse(updated_at),
    syncedAt = Instant.parse(updated_at),
)

@Serializable
private data class HabitLogDto(
    val id: String,
    val user_id: String,
    val habit_id: String,
    val quantity: Double,
    val logged_at: String,
    val deleted_at: String?,
    val synced_at: String?,
)

private fun HabitLog.toDto() = HabitLogDto(
    id = id,
    user_id = userId,
    habit_id = habitId,
    quantity = quantity,
    logged_at = loggedAt.toString(),
    deleted_at = deletedAt?.toString(),
    synced_at = syncedAt?.toString(),
)

private fun HabitLogDto.toDomain() = HabitLog(
    id = id,
    userId = user_id,
    habitId = habit_id,
    quantity = quantity,
    loggedAt = Instant.parse(logged_at),
    deletedAt = deleted_at?.let { Instant.parse(it) },
    syncedAt = synced_at?.let { Instant.parse(it) },
)

@Serializable
private data class WantLogDto(
    val id: String,
    val user_id: String,
    val activity_id: String,
    val quantity: Double,
    val device_mode: String,
    val logged_at: String,
    val deleted_at: String?,
    val synced_at: String?,
)

private fun WantLog.toDto() = WantLogDto(
    id = id,
    user_id = userId,
    activity_id = activityId,
    quantity = quantity,
    device_mode = when (deviceMode) {
        DeviceMode.THIS_DEVICE -> "this_device"
        DeviceMode.OTHER -> "other"
    },
    logged_at = loggedAt.toString(),
    deleted_at = deletedAt?.toString(),
    synced_at = syncedAt?.toString(),
)

private fun WantLogDto.toDomain() = WantLog(
    id = id,
    userId = user_id,
    activityId = activity_id,
    quantity = quantity,
    deviceMode = when (device_mode) {
        "this_device" -> DeviceMode.THIS_DEVICE
        else -> DeviceMode.OTHER
    },
    loggedAt = Instant.parse(logged_at),
    deletedAt = deleted_at?.let { Instant.parse(it) },
    syncedAt = synced_at?.let { Instant.parse(it) },
)
```

Add `kotlinx-serialization-core` dep if not already present (Ktor pulls it transitively; verify at compile time).

- [ ] **Step 2b: Shared build.gradle.kts — add kotlinx-serialization plugin if missing**

If the build fails because `@Serializable` is unresolved, add to `mobile/shared/build.gradle.kts` plugins block:
```kotlin
alias(libs.plugins.kotlin.serialization)
```

And add to `gradle/libs.versions.toml`:
```toml
[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 3: Compile**

Run: `./gradlew :mobile:shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync gradle/libs.versions.toml mobile/shared/build.gradle.kts
git commit -m "feat(sync): SupabaseSyncClient interface + Postgrest impl with DTOs"
```

---

## Task 10: `FakeSupabaseSyncClient` for tests

**Files:**
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/FakeSupabaseSyncClient.kt`

- [ ] **Step 1: Write the fake**

```kotlin
package com.habittracker.data.sync

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog

class FakeSupabaseSyncClient : SupabaseSyncClient {
    val habits = mutableListOf<Habit>()
    val wantActivities = mutableListOf<WantActivity>()
    val habitLogs = mutableListOf<HabitLog>()
    val wantLogs = mutableListOf<WantLog>()

    var shouldThrowOnNext: Throwable? = null

    private fun failIfNeeded() {
        shouldThrowOnNext?.let { err ->
            shouldThrowOnNext = null
            throw err
        }
    }

    override suspend fun upsertHabit(row: Habit) {
        failIfNeeded()
        habits.removeAll { it.id == row.id }
        habits.add(row)
    }

    override suspend fun upsertWantActivity(row: WantActivity, ownerUserId: String) {
        failIfNeeded()
        wantActivities.removeAll { it.id == row.id }
        wantActivities.add(row.copy(createdByUserId = if (row.isCustom) ownerUserId else null))
    }

    override suspend fun upsertHabitLog(row: HabitLog) {
        failIfNeeded()
        habitLogs.removeAll { it.id == row.id }
        habitLogs.add(row)
    }

    override suspend fun upsertWantLog(row: WantLog) {
        failIfNeeded()
        wantLogs.removeAll { it.id == row.id }
        wantLogs.add(row)
    }

    override suspend fun fetchHabitsSince(userId: String, sinceMs: Long): List<Habit> {
        failIfNeeded()
        return habits.filter { it.userId == userId && it.updatedAt.toEpochMilliseconds() > sinceMs }
    }

    override suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity> {
        failIfNeeded()
        return wantActivities.filter {
            (it.createdByUserId == userId || it.createdByUserId == null) &&
                it.updatedAt.toEpochMilliseconds() > sinceMs
        }
    }

    override suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog> {
        failIfNeeded()
        return habitLogs.filter {
            it.userId == userId && (it.syncedAt?.toEpochMilliseconds() ?: 0L) > sinceMs
        }
    }

    override suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog> {
        failIfNeeded()
        return wantLogs.filter {
            it.userId == userId && (it.syncedAt?.toEpochMilliseconds() ?: 0L) > sinceMs
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/FakeSupabaseSyncClient.kt
git commit -m "test(sync): FakeSupabaseSyncClient with error injection"
```

---

## Task 11: `SyncEngine` — implementation + tests (TDD)

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncEngine.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/SyncEngineTest.kt`

- [ ] **Step 1: Write failing test `SyncEngineTest`**

`mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/SyncEngineTest.kt`:
```kotlin
package com.habittracker.data.sync

import com.habittracker.data.local.SyncTable
import com.habittracker.data.local.SyncWatermarkStore
import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeWantActivityRepository
import com.habittracker.data.repository.FakeWantLogRepository
import com.habittracker.domain.UserIdentityProvider
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncEngineTest {

    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()
    private val wantActivityRepo = FakeWantActivityRepository()
    private val wantLogRepo = FakeWantLogRepository()
    private val supabase = FakeSupabaseSyncClient()
    private val watermarks = InMemoryWatermarkStore()
    private val auth = FakeAuthIdentity("user-1", authenticated = true)

    private val engine = SyncEngine(
        habitRepo, habitLogRepo, wantActivityRepo, wantLogRepo,
        supabase, watermarks, auth,
    )

    @Test
    fun `push marks rows synced`() = runTest {
        habitRepo.saveHabit(makeHabit("h1"))
        val result = engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(1, result.pushed)
        assertNotNull(habitRepo.habits.first().syncedAt)
    }

    @Test
    fun `pull inserts new remote rows`() = runTest {
        val remote = makeHabit("h1").copy(userId = "user-1", updatedAt = tPlus(10))
        supabase.habits.add(remote)
        val result = engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(1, result.pulled)
        assertEquals("h1", habitRepo.habits.first().id)
    }

    @Test
    fun `LWW overwrites local when remote updatedAt is newer`() = runTest {
        val local = makeHabit("h1").copy(name = "Old", updatedAt = tPlus(1), syncedAt = tPlus(1))
        habitRepo.saveHabit(local)
        val remote = local.copy(name = "New", updatedAt = tPlus(5))
        supabase.habits.add(remote)
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals("New", habitRepo.habits.first().name)
    }

    @Test
    fun `pull no-ops when local updatedAt is newer`() = runTest {
        val local = makeHabit("h1").copy(name = "Local", updatedAt = tPlus(10))
        habitRepo.saveHabit(local)
        val remote = local.copy(name = "Stale", updatedAt = tPlus(5))
        supabase.habits.add(remote)
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals("Local", habitRepo.habits.first().name)
    }

    @Test
    fun `soft delete merge applies remote tombstone`() = runTest {
        val log = HabitLog("l1", "user-1", "h1", 3.0, tPlus(1))
        habitLogRepo.insertLog(log.id, log.userId, log.habitId, log.quantity, log.loggedAt)
        val remoteDeleted = log.copy(deletedAt = tPlus(5), syncedAt = tPlus(5))
        supabase.habitLogs.add(remoteDeleted)
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(false, habitLogRepo.logs.first { it.id == "l1" }.isActive)
    }

    @Test
    fun `idempotent — back-to-back sync calls push 0 on second run`() = runTest {
        habitRepo.saveHabit(makeHabit("h1"))
        engine.sync(SyncReason.MANUAL).getOrThrow()
        val second = engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(0, second.pushed)
    }

    @Test
    fun `unauthenticated sync returns zero without touching network`() = runTest {
        val offlineAuth = FakeAuthIdentity("user-1", authenticated = false)
        val engine = SyncEngine(
            habitRepo, habitLogRepo, wantActivityRepo, wantLogRepo,
            supabase, watermarks, offlineAuth,
        )
        habitRepo.saveHabit(makeHabit("h1"))
        val result = engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(0, result.pushed)
        assertEquals(0, result.pulled)
    }

    @Test
    fun `push failure surfaces Error state`() = runTest {
        habitRepo.saveHabit(makeHabit("h1"))
        supabase.shouldThrowOnNext = RuntimeException("boom")
        val result = engine.sync(SyncReason.MANUAL)
        assertTrue(result.isFailure)
        assertTrue(engine.syncState.value is SyncState.Error)
    }

    @Test
    fun `watermark advances to max server timestamp pulled`() = runTest {
        supabase.habits.add(makeHabit("h1").copy(updatedAt = tPlus(10)))
        supabase.habits.add(makeHabit("h2").copy(updatedAt = tPlus(20)))
        engine.sync(SyncReason.MANUAL).getOrThrow()
        assertEquals(tPlus(20).toEpochMilliseconds(), watermarks.get(SyncTable.HABITS))
    }

    // ---- helpers ----

    private val t0: Instant = Clock.System.now()
    private fun tPlus(ms: Int) = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() + ms * 1000L)

    private fun makeHabit(id: String) = Habit(
        id = id,
        userId = "user-1",
        templateId = "tpl",
        name = "Read",
        unit = "pages",
        thresholdPerPoint = 3.0,
        dailyTarget = 3,
        createdAt = t0,
        updatedAt = t0,
        syncedAt = null,
    )
}

/** Minimal in-memory watermark store for tests — SyncWatermarkStore is expect-only in common. */
class InMemoryWatermarkStore {
    private val store = mutableMapOf<SyncTable, Long>()
    fun get(table: SyncTable): Long = store[table] ?: 0L
    fun set(table: SyncTable, valueMs: Long) { store[table] = valueMs }
}

class FakeAuthIdentity(
    private val uid: String,
    private val authenticated: Boolean,
) {
    fun currentUserId(): String = uid
    fun isAuthenticated(): Boolean = authenticated
}
```

**Note:** `SyncEngine` takes an identity provider that exposes `currentUserId()` + `isAuthenticated()`. To keep the engine KMP-clean, accept a small SAM interface rather than the androidApp-only `UserIdentityProvider`. See next step.

- [ ] **Step 2: Define the `SyncIdentity` interface + `SyncEngine` skeleton**

`mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/SyncEngine.kt`:
```kotlin
package com.habittracker.data.sync

import com.habittracker.data.local.SyncTable
import com.habittracker.data.local.SyncWatermarkStore
import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Minimal identity surface SyncEngine needs. Implementations bridge from AppContainer. */
interface SyncIdentity {
    fun currentUserId(): String
    fun isAuthenticated(): Boolean
}

class SyncEngine(
    private val habitRepo: HabitRepository,
    private val habitLogRepo: HabitLogRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val wantLogRepo: WantLogRepository,
    private val supabase: SupabaseSyncClient,
    private val watermarks: SyncWatermarkStore,
    private val identity: SyncIdentity,
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _state.asStateFlow()

    private val mutex = Mutex()

    suspend fun sync(reason: SyncReason): Result<SyncOutcome> = mutex.withLock {
        if (!identity.isAuthenticated()) {
            return Result.success(SyncOutcome(0, 0))
        }
        val userId = identity.currentUserId()
        val start = Clock.System.now()
        _state.value = SyncState.Running(start, reason)
        runCatching {
            val pushed = push(userId)
            val pulled = pull(userId)
            val now = Clock.System.now()
            _state.value = SyncState.Synced(now, pushed, pulled)
            SyncOutcome(pushed, pulled)
        }.onFailure { e ->
            _state.value = SyncState.Error(e.message ?: "Sync failed", Clock.System.now())
        }
    }

    private suspend fun push(userId: String): Int {
        var count = 0
        val now = Clock.System.now()
        habitRepo.getUnsyncedFor(userId).forEach { row ->
            supabase.upsertHabit(row)
            habitRepo.markSynced(row.id, now)
            count++
        }
        wantActivityRepo.getUnsyncedFor(userId).forEach { row ->
            supabase.upsertWantActivity(row, userId)
            wantActivityRepo.markSynced(row.id, now)
            count++
        }
        habitLogRepo.getUnsyncedFor(userId).forEach { row ->
            val stamped = row.copy(syncedAt = now)
            supabase.upsertHabitLog(stamped)
            habitLogRepo.markSynced(row.id, now)
            count++
        }
        wantLogRepo.getUnsyncedFor(userId).forEach { row ->
            val stamped = row.copy(syncedAt = now)
            supabase.upsertWantLog(stamped)
            wantLogRepo.markSynced(row.id, now)
            count++
        }
        return count
    }

    private suspend fun pull(userId: String): Int {
        var count = 0
        count += pullHabits(userId)
        count += pullWantActivities(userId)
        count += pullHabitLogs(userId)
        count += pullWantLogs(userId)
        return count
    }

    private suspend fun pullHabits(userId: String): Int {
        val last = watermarks.get(SyncTable.HABITS)
        val remote = supabase.fetchHabitsSince(userId, last)
        if (remote.isEmpty()) return 0
        val ids = remote.map { it.id }
        val locals = habitRepo.getByIdsForUser(userId, ids).associateBy { it.id }
        remote.forEach { row ->
            val local = locals[row.id]
            if (local == null || row.updatedAt > local.updatedAt) {
                habitRepo.mergePulled(row.copy(syncedAt = row.updatedAt))
            }
        }
        watermarks.set(SyncTable.HABITS, remote.maxOf { it.updatedAt.toEpochMilliseconds() })
        return remote.size
    }

    private suspend fun pullWantActivities(userId: String): Int {
        val last = watermarks.get(SyncTable.WANT_ACTIVITIES)
        val remote = supabase.fetchWantActivitiesSince(userId, last)
        if (remote.isEmpty()) return 0
        val ids = remote.map { it.id }
        val locals = wantActivityRepo.getByIdsForUser(userId, ids).associateBy { it.id }
        remote.forEach { row ->
            val local = locals[row.id]
            if (local == null || row.updatedAt > local.updatedAt) {
                wantActivityRepo.mergePulled(row.copy(syncedAt = row.updatedAt))
            }
        }
        watermarks.set(SyncTable.WANT_ACTIVITIES, remote.maxOf { it.updatedAt.toEpochMilliseconds() })
        return remote.size
    }

    private suspend fun pullHabitLogs(userId: String): Int {
        val last = watermarks.get(SyncTable.HABIT_LOGS)
        val remote = supabase.fetchHabitLogsSince(userId, last)
        if (remote.isEmpty()) return 0
        remote.forEach { row -> habitLogRepo.mergePulled(row) }
        val maxTs = remote.mapNotNull { it.syncedAt?.toEpochMilliseconds() }.maxOrNull() ?: last
        watermarks.set(SyncTable.HABIT_LOGS, maxTs)
        return remote.size
    }

    private suspend fun pullWantLogs(userId: String): Int {
        val last = watermarks.get(SyncTable.WANT_LOGS)
        val remote = supabase.fetchWantLogsSince(userId, last)
        if (remote.isEmpty()) return 0
        remote.forEach { row -> wantLogRepo.mergePulled(row) }
        val maxTs = remote.mapNotNull { it.syncedAt?.toEpochMilliseconds() }.maxOrNull() ?: last
        watermarks.set(SyncTable.WANT_LOGS, maxTs)
        return remote.size
    }
}
```

- [ ] **Step 3: Adjust test helpers to match production types**

Replace the `InMemoryWatermarkStore` helper in the test with a test-only preferences impl that plugs into the real `SyncWatermarkStore` from Task 7:

```kotlin
private class InMemorySyncPreferences : com.habittracker.data.local.SyncPreferences {
    private val map = mutableMapOf<String, Long>()
    override fun getLong(key: String): Long = map[key] ?: 0L
    override fun putLong(key: String, value: Long) { map[key] = value }
}

// In the test class:
private val watermarks = com.habittracker.data.local.SyncWatermarkStore(InMemorySyncPreferences())
```

Note: because `SyncPreferences` is an `expect class` without a common body, commonTest can't `: SyncPreferences` directly. Workaround is to introduce a `SyncPreferencesBacking` interface inside commonMain that the expect class implements, or — simpler — make the expect class non-`final` by default and override its methods. SQLDelight/kotlinx projects handle this with a separate interface. For this project, use a small in-package wrapper:

```kotlin
// Add to commonMain SyncPreferences.kt ABOVE the expect class:
interface SyncPreferencesBacking {
    fun getLong(key: String): Long
    fun putLong(key: String, value: Long)
}

expect class SyncPreferences : SyncPreferencesBacking
```

Android/iOS actuals need to implement the interface methods explicitly (rename their method overrides to `override fun`). Then the test uses `SyncPreferencesBacking` directly:

```kotlin
private class InMemorySyncPreferences : SyncPreferencesBacking {
    private val map = mutableMapOf<String, Long>()
    override fun getLong(key: String): Long = map[key] ?: 0L
    override fun putLong(key: String, value: Long) { map[key] = value }
}
```

And update `SyncWatermarkStore` to take `SyncPreferencesBacking` instead of `SyncPreferences` — zero impact on production wiring (SyncPreferences implements the interface).

If this causes churn, keep it even simpler: bypass `SyncWatermarkStore` in tests and inject an in-memory `Map<SyncTable, Long>` wrapper implementing a tiny new `WatermarkReader` interface. Use whichever feels lighter at implementation time.

`FakeAuthIdentity` already implements `SyncIdentity` as specified in Step 1's test code block — no change needed.

- [ ] **Step 4: Run the tests — verify they pass**

```bash
./gradlew :mobile:shared:allTests
```

Expected: new `SyncEngineTest` class's 9 tests pass, existing tests still pass. Total ~42 tests.

- [ ] **Step 5: Commit**

```bash
git add mobile/shared/src
git commit -m "feat(sync): SyncEngine implementation + 9 unit tests covering push/pull/LWW/soft-delete"
```

---

## Task 12: `SignUpResult` sealed type + AuthRepository changes + tests

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SignUpResult.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/AuthRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SupabaseAuthRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/FakeAuthRepository.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/SupabaseAuthRepositoryResultTest.kt`

- [ ] **Step 1: Create `SignUpResult.kt`**

```kotlin
package com.habittracker.data.repository

sealed class SignUpResult {
    data class SignedIn(val session: UserSession) : SignUpResult()
    data class ConfirmationRequired(val email: String) : SignUpResult()
}
```

- [ ] **Step 2: Update `AuthRepository` signature**

Replace `signUp` method:
```kotlin
suspend fun signUp(email: String, password: String): Result<SignUpResult>
```

Keep `signIn`, `signOut`, `currentUserId`, `isLoggedIn` unchanged.

- [ ] **Step 3: Update `SupabaseAuthRepository.signUp`**

```kotlin
override suspend fun signUp(email: String, password: String): Result<SignUpResult> = runCatching {
    client.auth.signUpWith(Email) {
        this.email = email
        this.password = password
    }
    val session = client.auth.currentSessionOrNull()
    if (session != null) {
        val user = session.user!!
        SignUpResult.SignedIn(UserSession(userId = user.id, email = user.email ?: email))
    } else {
        SignUpResult.ConfirmationRequired(email)
    }
}
```

- [ ] **Step 4: Update `FakeAuthRepository.signUp`**

```kotlin
override suspend fun signUp(email: String, password: String): Result<SignUpResult> {
    if (shouldFail) return Result.failure(RuntimeException("fake auth failure"))
    val session = UserSession(userId = "fake-${email.hashCode()}", email = email)
    currentSession = session
    return Result.success(SignUpResult.SignedIn(session))
}
```

Add a `var confirmationRequiredOnNext = false` toggle so tests can exercise the `ConfirmationRequired` branch:
```kotlin
var confirmationRequiredOnNext = false

override suspend fun signUp(email: String, password: String): Result<SignUpResult> {
    if (shouldFail) return Result.failure(RuntimeException("fake auth failure"))
    if (confirmationRequiredOnNext) {
        confirmationRequiredOnNext = false
        return Result.success(SignUpResult.ConfirmationRequired(email))
    }
    val session = UserSession(userId = "fake-${email.hashCode()}", email = email)
    currentSession = session
    return Result.success(SignUpResult.SignedIn(session))
}
```

- [ ] **Step 5: Write failing test `SupabaseAuthRepositoryResultTest`**

Since we can't easily mock supabase-kt's `SupabaseClient`, test the branching logic via `FakeAuthRepository`:
```kotlin
package com.habittracker.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupabaseAuthRepositoryResultTest {

    @Test
    fun `signUp returns SignedIn when session is active`() = runTest {
        val repo = FakeAuthRepository()
        val result = repo.signUp("a@b.com", "password").getOrThrow()
        assertTrue(result is SignUpResult.SignedIn)
        assertEquals("a@b.com", (result as SignUpResult.SignedIn).session.email)
    }

    @Test
    fun `signUp returns ConfirmationRequired when no session`() = runTest {
        val repo = FakeAuthRepository()
        repo.confirmationRequiredOnNext = true
        val result = repo.signUp("a@b.com", "password").getOrThrow()
        assertTrue(result is SignUpResult.ConfirmationRequired)
        assertEquals("a@b.com", (result as SignUpResult.ConfirmationRequired).email)
    }
}
```

- [ ] **Step 6: Run test → confirm PASS**

```bash
./gradlew :mobile:shared:allTests
```

Expected: both new tests pass.

- [ ] **Step 7: Commit**

```bash
git add mobile/shared/src
git commit -m "feat(auth): SignUpResult sealed type — SignedIn | ConfirmationRequired"
```

---

## Task 13: Google OAuth — expect/actual bridge + Android impl

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/remote/GoogleSignInLauncher.kt`
- Create: `mobile/shared/src/androidMain/kotlin/com/habittracker/data/remote/GoogleSignInLauncher.android.kt`
- Create: `mobile/shared/src/iosMain/kotlin/com/habittracker/data/remote/GoogleSignInLauncher.ios.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/AuthRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SupabaseAuthRepository.kt`

- [ ] **Step 1: Define the expect class**

`GoogleSignInLauncher.kt`:
```kotlin
package com.habittracker.data.remote

/** Platform bridge for Google OAuth. Android impl uses Credentials Manager; iOS is stub for Phase 6. */
expect class GoogleSignInLauncher {
    /** Launch the native Google sign-in flow. Returns an ID token on success. */
    suspend fun requestIdToken(): Result<String>
}
```

- [ ] **Step 2: Android actual**

`GoogleSignInLauncher.android.kt`:
```kotlin
package com.habittracker.data.remote

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

actual class GoogleSignInLauncher(
    private val context: Context,
    private val webClientId: String,
) {
    actual suspend fun requestIdToken(): Result<String> = runCatching {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialException) {
            throw IllegalStateException("Google sign-in cancelled or failed: ${e.message}", e)
        }

        val credential = response.credential
        if (credential is androidx.credentials.CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val idCred = GoogleIdTokenCredential.createFrom(credential.data)
            idCred.idToken
        } else {
            error("Unexpected credential type: ${credential.type}")
        }
    }
}
```

- [ ] **Step 3: iOS stub actual**

`GoogleSignInLauncher.ios.kt`:
```kotlin
package com.habittracker.data.remote

actual class GoogleSignInLauncher {
    actual suspend fun requestIdToken(): Result<String> =
        Result.failure(UnsupportedOperationException("Google sign-in on iOS lands in Phase 6"))
}
```

- [ ] **Step 4: Extend `AuthRepository` with `signInWithGoogle`**

```kotlin
suspend fun signInWithGoogle(idToken: String): Result<UserSession>
```

`SupabaseAuthRepository`:
```kotlin
override suspend fun signInWithGoogle(idToken: String): Result<UserSession> = runCatching {
    client.auth.signInWith(io.github.jan.supabase.auth.providers.IDToken) {
        provider = io.github.jan.supabase.auth.providers.Google
        this.idToken = idToken
    }
    val user = client.auth.currentSessionOrNull()?.user
        ?: error("Google sign-in returned no session")
    UserSession(userId = user.id, email = user.email ?: "")
}
```

`FakeAuthRepository`:
```kotlin
override suspend fun signInWithGoogle(idToken: String): Result<UserSession> {
    val session = UserSession(userId = "google-fake-$idToken", email = "google@fake.test")
    currentSession = session
    return Result.success(session)
}
```

- [ ] **Step 5: Compile on both targets**

```bash
./gradlew :mobile:shared:compileDebugKotlinAndroid
./gradlew :mobile:shared:compileKotlinIosSimulatorArm64
```

Expected: both green.

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src
git commit -m "feat(auth): GoogleSignInLauncher expect/actual + signInWithGoogle bridge"
```

---

## Task 14: `AppContainer` wires SyncEngine + AuthViewModel branches on SignUpResult

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/AppContainer.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/AuthViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/auth/AuthScreen.kt`

- [ ] **Step 1: Add SyncEngine + SyncPreferences + GoogleSignInLauncher to `AppContainer`**

Inside AppContainer, after existing repo declarations:
```kotlin
private val syncPreferences = SyncPreferences(context)
private val watermarks = SyncWatermarkStore(syncPreferences)
private val supabaseSyncClient: SupabaseSyncClient =
    PostgrestSupabaseSyncClient(supabase)

private val syncIdentity = object : SyncIdentity {
    override fun currentUserId(): String = this@AppContainer.currentUserId()
    override fun isAuthenticated(): Boolean = this@AppContainer.isAuthenticated()
}

val syncEngine = SyncEngine(
    habitRepository, habitLogRepository,
    wantActivityRepository, wantLogRepository,
    supabaseSyncClient, watermarks, syncIdentity,
)

val googleSignInLauncher = GoogleSignInLauncher(
    context = context.applicationContext,
    webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
)
```

Imports as needed.

- [ ] **Step 2: AuthViewModel branches on `SignUpResult`**

Replace the `onSuccess` block inside `submit()`:
```kotlin
result.onSuccess { outcome ->
    when (outcome) {
        is SignUpResult.SignedIn -> {
            container.migrateLocalToAuthenticated(outcome.session.userId)
            container.refreshAuthState()
            container.seedLocalDataIfEmpty()
            container.syncEngine.sync(SyncReason.POST_SIGN_IN)
            _events.emit(AuthEvent.Success)
        }
        is SignUpResult.ConfirmationRequired -> {
            _events.emit(AuthEvent.ConfirmationEmailSent(outcome.email))
        }
    }
}.onFailure { e ->
    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Auth failed")
}
```

Note: `signUp` now returns `Result<SignUpResult>`, not `Result<UserSession>`. For the `signIn` path, keep the existing `Result<UserSession>` handling:
```kotlin
val result: Result<Any> = if (state.isSignUp) {
    container.authRepository.signUp(state.email.trim(), state.password)
} else {
    container.authRepository.signIn(state.email.trim(), state.password)
}
```

Cleaner: handle each branch separately instead of `Result<Any>`:
```kotlin
if (state.isSignUp) {
    container.authRepository.signUp(state.email.trim(), state.password)
        .onSuccess { outcome -> handleSignUpOutcome(outcome) }
        .onFailure { e -> reportError(e) }
} else {
    container.authRepository.signIn(state.email.trim(), state.password)
        .onSuccess { session -> completeLogin(session) }
        .onFailure { e -> reportError(e) }
}

private suspend fun completeLogin(session: UserSession) {
    container.migrateLocalToAuthenticated(session.userId)
    container.refreshAuthState()
    container.seedLocalDataIfEmpty()
    container.syncEngine.sync(SyncReason.POST_SIGN_IN)
    _events.emit(AuthEvent.Success)
}

private suspend fun handleSignUpOutcome(outcome: SignUpResult) {
    when (outcome) {
        is SignUpResult.SignedIn -> completeLogin(outcome.session)
        is SignUpResult.ConfirmationRequired ->
            _events.emit(AuthEvent.ConfirmationEmailSent(outcome.email))
    }
}

private fun reportError(e: Throwable) {
    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Auth failed")
}
```

Add the new event variant to the sealed `AuthEvent`:
```kotlin
sealed interface AuthEvent {
    object Success : AuthEvent
    data class ConfirmationEmailSent(val email: String) : AuthEvent
}
```

Add a `signInWithGoogle(idToken: String)` VM method:
```kotlin
fun signInWithGoogle(idToken: String) {
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        container.authRepository.signInWithGoogle(idToken)
            .onSuccess { session -> completeLogin(session) }
            .onFailure { e -> reportError(e) }
    }
}
```

- [ ] **Step 3: AuthScreen adds Google button + handles ConfirmationEmailSent event**

Add a new `LaunchedEffect(Unit)` that collects events alongside the existing `Success` handler. Or extend the existing:
```kotlin
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            AuthEvent.Success -> onSuccess()
            is AuthEvent.ConfirmationEmailSent -> {
                snackbarHostState.showSnackbar(
                    "Check your email (${event.email}) to confirm your account."
                )
                onBack()
            }
        }
    }
}
```

(`snackbarHostState` already exists on Home via the HomeScreen pattern — lift it to AuthScreen similarly: `val snackbarHostState = remember { SnackbarHostState() }` inside AuthScreen, add `snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }` to the Scaffold.)

Add the Google button above the email field:
```kotlin
GoogleSignInButton(
    enabled = !uiState.isLoading,
    onClick = {
        scope.launch {
            launcher.requestIdToken()
                .onSuccess { token -> viewModel.signInWithGoogle(token) }
                .onFailure { e -> snackbarHostState.showSnackbar(e.message ?: "Google sign-in failed") }
        }
    },
)
Spacer(Modifier.height(Spacing.lg))
OrDivider("or")
Spacer(Modifier.height(Spacing.lg))
```

Inject `GoogleSignInLauncher` into AuthScreen from AppNavigation via a parameter on the composable — or read it off AppContainer. Simplest: add `launcher: GoogleSignInLauncher` to the AuthScreen signature; AppNavigation passes `container.googleSignInLauncher`.

Button composable:
```kotlin
@Composable
private fun GoogleSignInButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        // Use a Text label; if a brand mark is desired later, add an icon here.
        Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun OrDivider(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            "  $label  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
```

- [ ] **Step 4: AppNavigation wires launcher into AuthScreen**

Inside the `composable(Screen.Auth.route) { … }` block, pass the launcher:
```kotlin
AuthScreen(
    viewModel = vm,
    launcher = container.googleSignInLauncher,
    onSuccess = { navController.popBackStack() },
    onBack = { navController.popBackStack() },
)
```

- [ ] **Step 5: Build**

```bash
./gradlew :mobile:androidApp:assembleDebug
./gradlew :mobile:shared:allTests
```

Expected: BUILD SUCCESSFUL. All tests still pass.

- [ ] **Step 6: Commit**

```bash
git add mobile/androidApp/src
git commit -m "feat(auth): AuthViewModel branches SignedIn/ConfirmationRequired + Google button"
```

---

## Task 15: `SyncWorker` + Android triggers

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/sync/SyncWorker.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/sync/SyncTriggers.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/MainActivity.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeViewModel.kt`

- [ ] **Step 1: Write `SyncWorker`**

```kotlin
package com.habittracker.android.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habittracker.android.HabitTrackerApplication
import com.habittracker.data.sync.SyncReason

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HabitTrackerApplication
        val engine = app.container.syncEngine
        val reasonName = inputData.getString(KEY_REASON) ?: SyncReason.POST_LOG.name
        val reason = runCatching { SyncReason.valueOf(reasonName) }.getOrDefault(SyncReason.POST_LOG)
        return engine.sync(reason).fold(
            onSuccess = { Result.success() },
            onFailure = { err ->
                // Transient vs terminal
                if (err is IllegalStateException) Result.failure() else Result.retry()
            },
        )
    }

    companion object {
        const val KEY_REASON = "sync.reason"
    }
}
```

- [ ] **Step 2: Write `SyncTriggers` helper**

```kotlin
package com.habittracker.android.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.habittracker.data.sync.SyncReason
import java.util.concurrent.TimeUnit

object SyncTriggers {

    fun enqueue(context: Context, reason: SyncReason) {
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(Data.Builder().putString(SyncWorker.KEY_REASON, reason.name).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(uniqueName(reason), ExistingWorkPolicy.KEEP, workRequest)
    }

    private fun uniqueName(reason: SyncReason): String = when (reason) {
        SyncReason.POST_LOG -> "sync-post-log"
        SyncReason.APP_FOREGROUND -> "sync-foreground"
        SyncReason.WIDGET_WRITE -> "sync-widget-write"
        SyncReason.MANUAL -> "sync-manual"
        SyncReason.POST_SIGN_IN -> "sync-post-sign-in"
    }
}
```

- [ ] **Step 3: Trigger foreground sync from `MainActivity.onResume`**

Add a debounced trigger:
```kotlin
private var lastForegroundSyncAt = 0L

override fun onResume() {
    super.onResume()
    val now = System.currentTimeMillis()
    if (now - lastForegroundSyncAt > 5_000L) {
        lastForegroundSyncAt = now
        SyncTriggers.enqueue(this, SyncReason.APP_FOREGROUND)
    }
}
```

- [ ] **Step 4: Enqueue post-log sync from HomeViewModel**

Inside `commitPendingHabit` and `commitPendingWant` (successful branches), after the snackbar emit add:
```kotlin
SyncTriggers.enqueue(container.appContext, SyncReason.POST_LOG)
```

This requires AppContainer to expose an `appContext: Context`. Add:
```kotlin
val appContext: Context = context.applicationContext
```

as a public property on AppContainer.

- [ ] **Step 5: Build + run app on emulator**

```bash
./gradlew :mobile:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. Run the app, log a habit, inspect `adb logcat | grep SyncWorker` — confirm worker fires.

- [ ] **Step 6: Commit**

```bash
git add mobile/androidApp/src
git commit -m "feat(sync): SyncWorker + SyncTriggers + MainActivity onResume debounce + post-log enqueue"
```

---

## Task 16: Startup restore (2 s hybrid)

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Add 2 s hybrid pull in `AppNavigation.LaunchedEffect`**

Replace the existing startup block:
```kotlin
LaunchedEffect(Unit) {
    container.seedLocalDataIfEmpty()
    val userId = container.currentUserId()

    if (container.isAuthenticated() &&
        container.habitRepository.getHabitsForUser(userId).isEmpty()
    ) {
        // Fresh device with existing session — try a 2 s cloud restore before routing.
        withTimeoutOrNull(2_000L) {
            container.syncEngine.sync(SyncReason.POST_SIGN_IN)
        }
    }

    startDestination = if (container.isOnboardedUseCase.execute(userId)) {
        Screen.Home.route
    } else {
        Screen.Onboarding.route
    }
}
```

Imports: `kotlinx.coroutines.withTimeoutOrNull`, `com.habittracker.data.sync.SyncReason`.

- [ ] **Step 2: Build**

```bash
./gradlew :mobile:androidApp:assembleDebug
```

- [ ] **Step 3: Commit**

```bash
git add mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/navigation/AppNavigation.kt
git commit -m "feat(sync): 2s hybrid pull at startup for authenticated users with empty local"
```

---

## Task 17: Home sync badge + overflow menu (Sync now, Sign out)

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeScreen.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/HomeViewModel.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/habittracker/android/ui/home/LogoutDialog.kt`

- [ ] **Step 1: Expose syncState flow from HomeViewModel**

Add to HomeViewModel:
```kotlin
val syncState: StateFlow<SyncState> = container.syncEngine.syncState

fun triggerManualSync() {
    SyncTriggers.enqueue(container.appContext, SyncReason.MANUAL)
}

fun beginSignOut() {
    _showLogoutDialog.value = true
}

fun confirmSignOut(forceWhenUnsynced: Boolean) {
    val userId = container.currentUserId()
    viewModelScope.launch {
        val unsyncedHabitLogs = container.habitLogRepository.getUnsyncedFor(userId).size
        val unsyncedWantLogs = container.wantLogRepository.getUnsyncedFor(userId).size
        val unsynced = unsyncedHabitLogs + unsyncedWantLogs
        if (unsynced > 0 && !forceWhenUnsynced) {
            _logoutUnsyncedCount.value = unsynced
            return@launch
        }
        // Attempt one push, continue regardless
        runCatching { container.syncEngine.sync(SyncReason.MANUAL) }
        container.clearAuthenticatedUserData(userId)
        container.authRepository.signOut()
        container.refreshAuthState()
        _showLogoutDialog.value = false
        _events.tryEmit(HomeEvent.Message("Signed out"))
    }
}

fun dismissLogoutDialog() {
    _showLogoutDialog.value = false
    _logoutUnsyncedCount.value = 0
}

private val _showLogoutDialog = MutableStateFlow(false)
val showLogoutDialog: StateFlow<Boolean> = _showLogoutDialog.asStateFlow()

private val _logoutUnsyncedCount = MutableStateFlow(0)
val logoutUnsyncedCount: StateFlow<Int> = _logoutUnsyncedCount.asStateFlow()
```

Imports: `com.habittracker.android.sync.SyncTriggers`, `com.habittracker.data.sync.SyncReason`, `com.habittracker.data.sync.SyncState`.

- [ ] **Step 2: Create `LogoutDialog.kt`**

```kotlin
package com.habittracker.android.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun LogoutDialog(
    unsyncedCount: Int,
    onConfirm: (forceWhenUnsynced: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasUnsynced = unsyncedCount > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasUnsynced) "Sign out with unsynced data?" else "Sign out?") },
        text = {
            Text(
                if (hasUnsynced) {
                    "$unsyncedCount logs haven't synced. They'll be lost on this device if you sign out now."
                } else {
                    "Local data on this device will be cleared. Cloud data stays."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hasUnsynced) }) {
                Text(if (hasUnsynced) "Sign out anyway" else "Sign out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
```

- [ ] **Step 3: Home TopAppBar — overflow menu + sync status chip**

Replace the TopAppBar's `actions = { … }` block in HomeScreen:
```kotlin
actions = {
    val state by viewModel.syncState.collectAsState()
    when (state) {
        is SyncState.Running -> SyncRunningChip()
        is SyncState.Error -> SyncErrorChip(
            message = (state as SyncState.Error).message,
            onRetry = viewModel::triggerManualSync,
        )
        else -> {}
    }
    if (uiState.isAuthenticated) SyncedPill() else TextButton(onClick = onSignIn) { Text("Sign in") }
    Box {
        var menuOpen by remember { mutableStateOf(false) }
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Sync now") },
                onClick = { menuOpen = false; viewModel.triggerManualSync() },
            )
            if (uiState.isAuthenticated) {
                DropdownMenuItem(
                    text = { Text("Sign out") },
                    onClick = { menuOpen = false; viewModel.beginSignOut() },
                )
            }
        }
    }
    Spacer(Modifier.width(Spacing.sm))
},
```

Add small helper composables:
```kotlin
@Composable
private fun SyncRunningChip() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(horizontal = Spacing.xs)) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(Spacing.xs))
            Text("Syncing", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun SyncErrorChip(message: String, onRetry: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.padding(horizontal = Spacing.xs).clickable(onClick = onRetry)) {
        Text("Sync failed — tap to retry", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))
    }
}
```

Imports needed: `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.filled.MoreVert`, `androidx.compose.material3.DropdownMenu`, `androidx.compose.material3.DropdownMenuItem`, `androidx.compose.runtime.mutableStateOf`, `setValue`.

- [ ] **Step 4: Show logout dialog inside HomeScreen**

At the top level of `HomeScreen` body, after reading `uiState`:
```kotlin
val showDialog by viewModel.showLogoutDialog.collectAsState()
val unsyncedCount by viewModel.logoutUnsyncedCount.collectAsState()

if (showDialog) {
    LogoutDialog(
        unsyncedCount = unsyncedCount,
        onConfirm = { force -> viewModel.confirmSignOut(force) },
        onDismiss = viewModel::dismissLogoutDialog,
    )
}
```

- [ ] **Step 5: Build + sanity test**

```bash
./gradlew :mobile:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add mobile/androidApp/src
git commit -m "feat(ui): Home sync badge + overflow menu (Sync now, Sign out) + confirm dialog"
```

---

## Task 18: Manual verification + plan close-out

**Files:** none (manual test pass)

- [ ] **Step 1: Fresh install happy path**

```
adb shell pm clear com.habittracker.android
```

Launch → onboard guest → log a habit + a want → Home shows "+N pts" and spent counts → tap Sign in → sign up with fresh email → land back on Home → "Synced" pill visible → balance + progress identical.

- [ ] **Step 2: Reinstall / second-device restore**

Uninstall app. Reinstall. Launch → lands at 2 s spinner then Home (no onboarding) → data from step 1 is present.

- [ ] **Step 3: Airplane mode log + reconnect**

Toggle airplane mode → log a habit → snackbar shows "+N pts" → overflow "Sync now" → error chip. Disable airplane mode → tap chip (or reopen app) → "Syncing" chip → "Synced" pill.

- [ ] **Step 4: Sign-out with unsynced data**

Airplane mode → log 3 habits → overflow "Sign out" → dialog shows "3 logs haven't synced." → tap "Sign out anyway" → Home reverts to guest. Logs on the device are wiped per spec.

- [ ] **Step 5: Supabase Confirm email ON**

In Supabase dashboard → Authentication → Providers → Email → toggle **Confirm email ON**. Sign out in app. Sign up with new fresh email → snackbar "Check your email …" and AuthScreen closes. Click confirmation link in the email. Sign in with the same creds on the AuthScreen → migrate runs → Home populated.

Toggle Confirm email back OFF before more local dev.

- [ ] **Step 6: Google sign-in**

From Home → Sign in → "Continue with Google" → pick an account → returns to Home with "Synced" pill. Verify in Supabase dashboard → Authentication → Users that the Google account appears.

- [ ] **Step 7: Cross-device sync**

Install on a second emulator (or the same emulator with the same signed-in account after clear data + fresh sign-in). Log a habit on device A → open app on device B → within ~30 s (foreground sync) or after manual Sync → new log appears.

- [ ] **Step 8: Close out**

If all pass:
```bash
git log --oneline main..HEAD
git push
gh pr create --base main --head feature/phase3-sync-auth --title "Phase 3: sync + auth hardening" --body "..."
```

---
