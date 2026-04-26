# Phase 3 — Sync + Auth Hardening Design Spec

**Date:** 2026-04-23
**Status:** Approved (ready for plan)
**Depends on:** Phase 2 (guest mode, email/password auth, reactive auth state)
**Owning surfaces:** `mobile/shared` (sync engine, supabase client, auth repo), `mobile/androidApp` (sync worker, auth UI, home sync badge, logout)

---

## 1. Goal

Close the cross-device data continuity gap surfaced at the end of Phase 2:

> Fresh reinstall + sign-in leaves Home empty because Phase 2 never pushed data to the cloud.

Phase 3 ships:

- Push local rows → Supabase and pull remote rows back, so data survives reinstall and flows across devices.
- Google OAuth sign-in alongside existing email/password (iOS + Apple Sign-In land in Phase 6).
- Graceful handling of Supabase "Confirm email" on/off.
- Logout UI with unsynced-rows warning.

## 2. Out of scope

- Realtime websocket subscriptions (Supabase Realtime).
- Background-fetch on a fixed schedule (WorkManager periodic). Phase 3 uses on-change work only.
- Conflict resolution UI (merge screens).
- Apple Sign-In.
- iOS platform work — `iosMain` actuals for new `expect` declarations are included (so the shared module still compiles for iOS) but no iOS UI wiring.
- Email-confirmation resend / polling / magic-link.

## 3. Locked design decisions

| Question | Decision | Rationale |
|---|---|---|
| Sync timing | Automatic on key triggers + manual "Sync now" button | Invisible for the common case, controllable when user needs certainty |
| Conflict rule | Append-only logs + soft-delete wins; mutable rows `updated_at` LWW | Schema already append-only; `updated_at` adds one column. Skips conflict UI. |
| Startup restore | 2 s hybrid blocking screen with fallback to background | Snappy on fast networks, graceful on slow |
| OAuth providers (Android) | Google only | Apple/iOS lands in Phase 6 |
| Offline logout | Warn + allow (Option B) | Respects user choice, avoids paternalism / stuck states |
| Email confirmation UX | Minimal snackbar, pop back to Home (Option A) | Phase 3 focuses on sync, not new onboarding surfaces |
| Sync cadence when app closed | Enqueue `WorkManager` on-change (Option C) | Unlocks widget quick-log in Phase 5 without retrofit |
| Sign-up auto-login | Auto-login when session returned; snackbar when not | Matches user expectation, no artificial friction |

## 4. Architecture

A single **`SyncEngine`** in `shared/commonMain` coordinates push + pull across all user-scoped tables. Repositories stay CRUD-only (local SQLite, no HTTP). An Android-only `SyncWorker` wraps engine calls for WorkManager. `SupabaseAuthRepository` gains Google OAuth and returns a `SignUpResult` sealed type.

```
┌─ UI layer (Compose)                                 ┐
│   HomeViewModel / AuthViewModel / SettingsViewModel │
│     subscribe SyncEngine.syncState                  │
│     call syncEngine.sync(...)                       │
├─────────────────────────────────────────────────────┤
│ SyncEngine              (shared/commonMain)         │
│   coordinates push+pull for all 4 tables            │
│   exposes syncState: StateFlow<SyncState>           │
├─────────────────────────────────────────────────────┤
│ SupabaseSyncClient      (shared/commonMain)         │
│   thin wrapper over supabase.postgrest              │
│   one method per table, DTO mapping                 │
├─────────────────────────────────────────────────────┤
│ Local repositories      (shared/commonMain)         │
│   unchanged CRUD + Flows                            │
│   + tiny helpers: getUnsyncedFor, markSynced        │
├─────────────────────────────────────────────────────┤
│ SyncWatermarkStore      (shared, expect/actual)     │
│   Android DataStore / iOS NSUserDefaults            │
│   last-pull timestamp per table                     │
├─────────────────────────────────────────────────────┤
│ SyncWorker              (androidApp/androidMain)    │
│   WorkManager wrapper. Enqueued on log / widget     │
│   write. Unique names dedupe rapid writes.          │
├─────────────────────────────────────────────────────┤
│ SupabaseAuthRepository  (shared/commonMain)         │
│   + signUp returns SignUpResult sealed type         │
│   + signInWithGoogle() (expect/actual for native    │
│     launch)                                         │
└─────────────────────────────────────────────────────┘
```

### Why a dedicated engine

- Single source of truth for sync status + errors; every ViewModel subscribes to one `syncState` flow.
- Unit-testable with fake repos + `FakeSupabaseSyncClient`, no Android framework.
- Phase 5 widget worker calls the same engine — zero re-plumbing.
- Swap Supabase backend later = change `SupabaseSyncClient` only.

### Alternatives considered

- **Per-repo sync methods.** Entangles local CRUD with HTTP; ViewModels would have to orchestrate 4 repos.
- **Supabase Realtime.** Overkill for a daily-cadence habit tracker; higher quota + battery cost + harder offline story.
- **WorkManager periodic.** Wastes battery with fixed intervals; on-change work is sharper.

## 5. Components

### 5.1 `SyncEngine` (shared/commonMain)

```kotlin
class SyncEngine(
    private val habitRepo: HabitRepository,
    private val habitLogRepo: HabitLogRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val wantLogRepo: WantLogRepository,
    private val supabase: SupabaseSyncClient,
    private val watermarks: SyncWatermarkStore,
    private val authProvider: UserIdentityProvider,
)

enum class SyncReason { APP_FOREGROUND, POST_LOG, MANUAL, POST_SIGN_IN, WIDGET_WRITE }

sealed class SyncState {
    object Idle : SyncState()
    data class Running(val since: Instant, val reason: SyncReason) : SyncState()
    data class Synced(val at: Instant, val pushed: Int, val pulled: Int) : SyncState()
    data class Error(val message: String, val since: Instant) : SyncState()
}

data class SyncOutcome(val pushed: Int, val pulled: Int)

suspend fun sync(reason: SyncReason): Result<SyncOutcome>
val syncState: StateFlow<SyncState>
```

- `sync` is idempotent and safe to call concurrently — internal mutex serializes runs.
- Skips immediately if `!authProvider.isAuthenticated()`.
- First push, then pull, then watermark advance.

### 5.2 `SupabaseSyncClient` (shared/commonMain)

Interface:

```kotlin
interface SupabaseSyncClient {
    suspend fun upsertHabit(row: Habit)
    suspend fun upsertWantActivity(row: WantActivity)
    suspend fun upsertHabitLog(row: HabitLog)
    suspend fun upsertWantLog(row: WantLog)
    suspend fun fetchHabitsSince(userId: String, sinceMs: Long): List<Habit>
    suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity>
    suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog>
    suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog>
}
```

The Postgrest-backed implementation lives alongside, fake version in `commonTest`. Domain models round-trip via explicit DTOs inside the client — the engine sees domain types only.

### 5.3 `SyncWatermarkStore` (shared, `expect class`)

```kotlin
expect class SyncWatermarkStore {
    fun get(table: String): Long       // ms, 0 if never pulled
    fun set(table: String, value: Long)
}
```

- `androidMain` actual uses SharedPreferences under `habit_tracker_sync`.
- `iosMain` actual uses `NSUserDefaults` under key `habit_tracker.sync.<table>`.

### 5.4 `SyncWorker` (androidApp/androidMain)

```kotlin
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = ...
}
```

- Enqueued via `WorkManager.getInstance(ctx).enqueueUniqueWork("sync-post-log", KEEP, req)` after every log commit. Same pattern for `sync-widget-write`, `sync-foreground`.
- `KEEP` policy dedupes rapid writes; fresh work enqueued once prior is finished or cancelled.
- `doWork()` returns `Result.retry()` on transient errors (network/5xx) → WorkManager exponential backoff (initial 30 s, max 10 min, up to `WorkRequest.MAX_BACKOFF_MILLIS`). Returns `Result.failure()` on permanent errors (401 after refresh fail) so backoff doesn't spin forever; next user trigger enqueues fresh work.

### 5.5 Repository additions

Each user-scoped repo gains two tiny methods (still CRUD on local SQLite):

```kotlin
suspend fun getUnsyncedFor(userId: String): List<T>    // WHERE syncedAt IS NULL
suspend fun markSynced(ids: List<String>, syncedAt: Instant)
```

For mutable rows (`Habit`, `WantActivity`) a single extra helper drives LWW merge:

```kotlin
suspend fun getByIdsForUser(userId: String, ids: List<String>): List<T>
```

No new domain methods for logs — insert/softDelete already exist; merge just calls `insertLog` for new rows and `softDelete` for remote tombstones.

### 5.6 `SupabaseAuthRepository` additions

```kotlin
sealed class SignUpResult {
    data class SignedIn(val session: UserSession) : SignUpResult()
    data class ConfirmationRequired(val email: String) : SignUpResult()
}

suspend fun signUp(email: String, password: String): Result<SignUpResult>
suspend fun signInWithGoogle(activity: Activity): Result<UserSession>  // androidMain only
```

Detection is passive: after `client.auth.signUpWith(Email) { ... }`, read `client.auth.currentSessionOrNull()`. Non-null → `SignedIn`; null → `ConfirmationRequired`. No project-settings API call.

Google OAuth uses `Auth.signInWith(Google)` which launches a Custom Tabs / native flow; result is observed through `auth.sessionStatus`. The `Activity` dep is Android-only, so the method goes in an androidApp-level helper or behind an `expect` bridge to keep the repo interface shared.

## 6. Schema additions

### 6.1 Local (SQLDelight)

**LocalHabit** — add `updatedAt INTEGER NOT NULL`. Migration backfills `updatedAt = createdAt`. `saveHabit` stamps `updatedAt = now()` on upsert.

**LocalWantActivity** — same `updatedAt` column + backfill.

**HabitLog / WantLog / LocalIdentity** — no changes. Append-only or user-agnostic.

New SQLDelight `migrations/1.sqm` bumps schema version and performs the backfill inside a transaction.

### 6.2 Remote (Supabase)

New Postgres migration under `supabase/migrations/<ts>_sync_hardening.sql`:

```sql
alter table habits        add column if not exists updated_at timestamptz not null default now();
alter table want_activities add column if not exists updated_at timestamptz not null default now();

-- Auto-stamp on UPDATE for both tables
create or replace function touch_updated_at() returns trigger as $$
begin new.updated_at = now(); return new; end;
$$ language plpgsql;

drop trigger if exists habits_touch on habits;
create trigger habits_touch before update on habits
  for each row execute function touch_updated_at();

drop trigger if exists want_activities_touch on want_activities;
create trigger want_activities_touch before update on want_activities
  for each row execute function touch_updated_at();
```

No schema change for logs — `synced_at` + `deleted_at` already present.

## 7. Data flow

### 7.1 Push

```
for each table in [habits, want_activities, habit_logs, want_logs]:
    rows = repo.getUnsyncedFor(currentUserId)          // synced_at IS NULL
    for row in rows:
        supabase.upsert(row)                           // idempotent by id
    repo.markSynced(rows.map { it.id }, now)
```

Fail fast on first HTTP error → engine returns `Error`, rows stay unsynced, next trigger retries. Postgrest `upsert` is idempotent so re-pushing already-synced rows is safe.

### 7.2 Pull

```
for each table in [habits, want_activities, habit_logs, want_logs]:
    lastMs = watermarks.get(table)
    remote = supabase.fetchSince(table, currentUserId, lastMs)
    for row in remote:
        applyMerge(row)
    if remote.isNotEmpty():
        watermarks.set(table, max(lastMs, remote.maxOf { it.serverUpdatedAtMs }))
```

Server-side `updated_at` (for mutable rows) and `synced_at` (for logs) provide monotonic watermark progression.

### 7.3 Merge rules

**Append-only logs (`HabitLog`, `WantLog`):**

- row not in local → `insertLog(...)` with remote `id`.
- row in local, remote `deletedAt` non-null, local `deletedAt` null → `softDelete(id)`.
- row in local, both `deletedAt` non-null → keep max(`local.deletedAt`, `remote.deletedAt`). Implementation: skip (SQLDelight soft-delete is idempotent; push will reconcile the max on next round).
- row in local, only local deleted → no-op on pull; push will update server.

**Mutable rows (`Habit`, `WantActivity`):**

- row not in local → upsert.
- row in local, `remote.updatedAt > local.updatedAt` → overwrite local.
- row in local, `remote.updatedAt ≤ local.updatedAt` → no-op; next push will carry local edits up.

### 7.4 Trigger map

| Trigger | What runs | Blocking UI? |
|---|---|---|
| Sign-in success | `migrateLocalToAuthenticated(authUid)` → `refreshAuthState()` → `sync(POST_SIGN_IN)` under 2 s hybrid | 2 s then background |
| App foreground | `sync(APP_FOREGROUND)` debounced 5 s from last resume | no |
| Log commit | Enqueue `SyncWorker` with unique name `sync-post-log` → `sync(POST_LOG)` | no |
| Manual "Sync" button | `sync(MANUAL)` | no; surfaces Running/Error in UI |
| Widget write (Phase 5) | Same worker, unique name `sync-widget-write` | no |
| Sign-out | Attempt `sync(MANUAL)` → `clearAuthenticatedUserData(authUid)` → `supabase.auth.signOut()` → `refreshAuthState()`. Offline with unsynced: warn dialog before wipe. | confirm dialog |

### 7.5 Startup restore

```
AppNavigation.LaunchedEffect(Unit):
    container.seedLocalDataIfEmpty()
    userId = container.currentUserId()
    if container.isAuthenticated() and habitRepo.getHabitsForUser(userId).isEmpty():
        withTimeoutOrNull(2_000) { syncEngine.sync(POST_SIGN_IN) }
        // route resolves from whatever arrived by now; remainder streams in
    startDestination =
        if (isOnboardedUseCase.execute(userId)) Screen.Home.route
        else Screen.Onboarding.route
```

## 8. Error handling

| Condition | Behaviour |
|---|---|
| Network timeout / DNS / 5xx | `sync()` returns `Error`, background syncs stay silent; manual sync shows error snackbar. Rows remain unsynced for next trigger. |
| 401 expired session | supabase-kt auto-refreshes; on refresh failure → `Error`, no destructive action, requires next sign-in trigger. |
| Partial push failure | Already-pushed rows stay marked; remainder retried next trigger. Logs are independent, safe to resume. |
| Schema mismatch (extra/missing column) | DTO layer uses explicit column list per table → forward-compat. New unknown fields ignored. |
| Sign-out offline with unsynced | Dialog B: warn count, allow "Sign out anyway". Engine attempts one push, proceeds with wipe on failure. |
| Startup pull timeout (>2 s) | Route on local state; background pull continues. No error shown unless sync explicitly fails. |
| Startup pull fails (offline) | Onboarding if local empty; Home with error chip if local has rows. User can retry sync from Home. |

## 9. Testing

### 9.1 Unit (commonTest)

- `SyncEngineTest` with `FakeSupabaseSyncClient` + existing repo fakes:
  - push marks rows `synced`.
  - pull inserts new remote rows.
  - LWW: remote `updatedAt > local.updatedAt` overwrites local.
  - soft-delete merge: remote tombstone applies; local tombstone preserved on pull.
  - idempotent: back-to-back `sync()` → second is no-op.
  - unauthenticated: `sync()` returns without touching network.
  - session-refresh failure surfaces `Error`, leaves local data intact.
  - watermark advances to max server ts from pulled rows.
- `SupabaseAuthRepositoryTest`:
  - `signUp` returns `SignedIn` when `currentSessionOrNull()` non-null after sign-up call.
  - `signUp` returns `ConfirmationRequired(email)` when null.
- `SyncWatermarkStoreTest` (androidUnitTest + iosTest): round-trip ms value per table key.

### 9.2 Integration (androidTest)

- `SyncWorkerTest`: enqueue → runs → calls engine (verified via injected fake engine). Single success + failure case; deeper coverage sits in unit tests.

### 9.3 Manual verification checklist

- Fresh install + guest onboarding + logs → sign up → Home still shows data + "Synced" pill.
- Phone A logs → Phone B app foreground within 30 s → new rows visible.
- Airplane mode → log → reconnect → pushed on next foreground.
- Sign-out offline with unsynced → warn dialog, confirm, local wipes.
- Supabase Confirm email ON → sign-up → "Check your email" snackbar; confirmation link → sign-in → migrate.
- Google OAuth first sign-in → Home populated from cloud (new device).
- Reinstall → sign-in → 2 s restore spinner → Home with previous data.
- Manual Sync button during airplane mode → error snackbar; after reconnect → success.

## 10. UI surface changes

- **AuthScreen:** "Continue with Google" primary button above email fields. Snackbar for `ConfirmationRequired`. No new screen for confirmation.
- **HomeScreen TopAppBar:** sync status indicator when `Running`, error chip when `Error`, overflow menu item "Sync now" and "Sign out".
- **Logout confirmation dialog:** shown inline from Home. Lists unsynced count when non-zero.

No full new screens. All additions layered onto existing Phase 2 surfaces. Theme tokens untouched.

## 11. Deployment prerequisites

Before the Phase 3 build can sign in with Google on a device:

- **Google Cloud Console:** create an OAuth 2.0 Client ID of type "Android" tied to the app's package name + SHA-1 signing certificate. Copy the Web Client ID (used as `id_token` audience).
- **Supabase dashboard → Authentication → Providers → Google:** enable provider, paste the Web Client ID + Client Secret, add `com.habittracker.android://auth-callback` to Additional Redirect URLs.
- **Android Manifest:** add the intent-filter for the redirect URI scheme on a new `AuthCallbackActivity` (or reuse MainActivity with deep link).
- **`local.properties`:** add `google.web_client_id=...` so `BuildConfig.GOOGLE_WEB_CLIENT_ID` can be injected and consumed by the OAuth launch.

None of these change at runtime — pure project setup. Document in the plan's first task.

## 12. Open follow-ups to Phase 4+

- Notifications (Phase 4) alongside streaks.
- Apple Sign-In + iOS UI wiring (Phase 6).
- Email confirmation resend / polling / magic-link (Phase 6 polish, if ever).
- Conflict-detection UI if real-world data shows unexpected merge losses.

---
