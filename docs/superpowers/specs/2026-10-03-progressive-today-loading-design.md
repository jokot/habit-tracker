# Progressive Today loading: design

**Date:** 2026-10-03
**Branch:** `fix/sync-pagination` (PR #30)
**Refs:** issue #20, fix-queue item 3 ("defer heavy logs pull to background after sign-in")

## 1. Problem

The user waits a long time after sign-in, and again on Today, for these reasons:

1. `AuthViewModel.completeSignIn` waits for a full sync before it opens Home. That includes the 5 s pre-reconcile sync in `seedLocalDataIfEmpty`, then a second sync of all six tables with the full log history.
2. Home has one `isLoading` flag. A full-screen spinner shows until the first local read.
3. The UI cannot tell "not pulled yet" from "empty". After a fresh sign-in, Today shows "No habits yet", a 0 streak and a 0 balance until the pull ends.
4. The pull order is habits, wants, habit logs (full history), want logs (full history), identities. The identity strip waits for the whole log history, but it does not need it.

## 2. Goal

1. Each Today section is a separate item with its own loading state: the identity strip, the stat card, today's habits and the wants.
2. Each section loads when its own data arrives. It does not wait for the other sections.
3. Sign-in goes to Home at once. It does not wait for a sync.
4. A section that is not ready shows a shimmer skeleton in the app's theme.
5. **No partial numbers.** A number is shown only after all the data it depends on is local.

## 3. Non-goals

- Other screens (Streak history, Identity hub, You, the widgets) keep their current behaviour. They read local data as it arrives.
- No change to the push half of sync.
- No parallel pulls (issue #20 item 2 stays deferred).

## 4. Design

### 4.1 Staged pull

`SyncEngine.pull` runs in three stages. Each step records its result as soon as it finishes, so the UI updates step by step. Every step after the first uses the paged fetch from PR #30.

| Stage | Steps, in order | Unblocks |
|---|---|---|
| 1. Core | user_identities, habits, habit_identities, want_activities | Identity strip, the habit list, the want list |
| 2. Recent logs | habit logs and want logs with `logged_at >= recentFrom` | Habit progress, points, balance |
| 3. History | the full watermark pull of habit_logs and want_logs | Streak, 7-day strip, want spend rate |

- **Stage 2 runs only on a first pull.** It runs when habit_logs or want_logs is not yet pulled in full. A routine sync skips it, because the watermark pull is already small.
- **`recentFrom`** is the start of the day 6 days ago, in the device time zone. It covers the 7-day strip. It is never later than the start of the week (Monday), so it also covers the weekly balance.
- **Stage 2 does not move any watermark.** Stage 3 then pulls the same rows again. The merge is `INSERT OR REPLACE`, so pulling a row twice is safe. The cost is one week of logs, pulled twice.
- **One `sync()` call runs all three stages under the mutex.** `SyncState` stays `Running` until stage 3 ends. A failure in any step ends the sync with `Error`. Steps that already finished keep their result.

### 4.2 Pull progress

The new type is in `data/local/SyncWatermarkStore.kt`, next to `SyncTable`:

```kotlin
/** What the first sync on this device has pulled so far. */
data class PullProgress(
    /** Tables whose whole history is local. */
    val tables: Set<SyncTable> = emptySet(),
    /** Habit logs and want logs of the last 7 days are local. */
    val recentLogs: Boolean = false,
)
```

Where the progress is stored:

- `SyncWatermarkStore` stores it in `SyncPreferences` as `pulled.<table>` and `pulled.recent_logs` (1 or 0).
- It exposes the value as `progress: StateFlow<PullProgress>`, which is now part of `WatermarkReader`. `markPulled(table)` and `markRecentLogsPulled()` update the store and the flow together.

Rules:

- **When a table counts as pulled:** after its step finishes, including when the server returned 0 rows. An empty table is pulled, not loading.
- **When a table counts as pulled on an upgraded install:** the first time the new version runs, it checks for any watermark above 0. If there is one, the device synced in full before, so every table and the recent logs count as pulled, empty tables too. Without this rule, users who upgrade would see a shimmer until their next sync, or for good while offline. A one-time marker (`pulled.flags_version`) stops the check from running again, so a first pull that a restart cuts short is not mistaken for a full one.
- **Recent logs are also ready** when both log tables are pulled in full.
- **What `reset()` clears:** both the flags and the watermarks. That covers sign-out, a migrate for an existing user, and a DB wipe.
- **Where the UI reads it:** `SyncEngine.pullProgress` exposes `watermarks.progress`. AppContainer passes it on.

### 4.3 Section readiness

There is a new pure function in shared `domain`, so jvmTest can test it:

```kotlin
enum class TodaySection { IDENTITIES, HABITS, POINTS, STREAK, WANTS }

fun PullProgress.readySections(isAuthenticated: Boolean): Set<TodaySection>
```

A guest has no pull, so a guest sees every section as ready at once. For a signed-in user, each section waits for these:

| Section | Ready when |
|---|---|
| IDENTITIES | `USER_IDENTITIES` is pulled |
| HABITS (habit cards and their header) | `HABITS` is pulled and recent logs are ready |
| POINTS (earned, spent, balance on the stat card) | `HABITS` is pulled and recent logs are ready |
| STREAK (streak count and 7-day strip) | `HABITS` and `HABIT_LOGS` are pulled |
| WANTS (wants header and cards) | `WANT_ACTIVITIES` and `HABIT_LOGS` are pulled, and recent logs are ready |

Why WANTS waits for the history: `LogWantUseCase` prices a want from the streak (`ExchangeRateCalculator.rateFor(streakOnDay)`). A want tapped before the history is local can spend at the wrong rate. The wants are at the bottom of Today, so the extra wait shows least there.

Why STREAK waits for the history: `observeCurrent` computes the streak from the first log ever. `observeRange` uses `firstActiveLogAt` to tell "not started" days from broken ones. Both are wrong while only recent logs are local.

### 4.4 Home state

- `HomeViewModel` combines `container.pullProgress` with `authState`, and exposes `readySections: StateFlow<Set<TodaySection>>` and `loadFailed: StateFlow<Boolean>`.
- `HomeUiState.isLoading` stays. It is true only until the first local read, which takes milliseconds.
- `HomeScreen` drops the full-screen `CircularProgressIndicator`. While `isLoading` is true, every section shows its skeleton.
- Each section renders its skeleton until it is ready, then its content. The screen does not show the "No habits yet" empty state for a section that is not ready.
- **Habit and want cards are not tappable** while their skeleton shows, so no logging is possible.

On the stat card:

- the streak half (icon, number, caption, 7-day strip) and the points row each have their own skeleton
- POINTS is often ready before STREAK, so the points row can show while the streak half still shimmers

### 4.5 Errors and offline

These rules apply when a sync ends in `Error` while some section is not ready:

- Each section that is not ready stops the shimmer animation and shows a static skeleton.
- One notice row shows above the identity strip: **"Couldn't finish loading. Pull down to try again."** It has a **Retry** text button, which calls `triggerManualSync()`.
- When a new sync starts, the notice row hides and the shimmer runs again.
- The SyncChip and the snackbar keep their current behaviour.

### 4.6 Shimmer component

`ui/components/Skeleton.kt`:

- **`Modifier.shimmer()`** draws a moving linear gradient over the shape.
  - Base colour: `surfaceVariant`. Highlight colour: `surface`.
  - The animation is infinite: 1200 ms per sweep, `LinearEasing`, moving left to right.
  - When `LocalSkeletonAnimated` is false (the sync failed), it draws a static base colour.
- **`SkeletonBlock(width, height, shape)`** is a rounded box that uses the shimmer.
- **Section skeletons** match the real layout, so content does not jump on swap:
  - `IdentityStripSkeleton`: the "I AM" label plus 3 chip-sized pills
  - `DailyStatusCard(streakLoading, pointsLoading)`: the streak half shows a 44 dp icon block, a number block, a caption line and 7 heat cells; the points row shows three value blocks
  - `HabitCardSkeleton` (shown ×3)
  - `WantCardSkeleton` (shown ×2)
- **Reduced motion:** when the system animator scale is 0, the skeleton is static.

The final visual is in the Habitto design project, on the Today page: https://claude.ai/design/p/019dd32e-8a8d-7707-b080-fc31a631b693?file=today.html

### 4.7 Sign-in and startup

**Sign-in.** `AuthViewModel.completeSignIn` becomes:

```kotlin
container.migrateLocalToAuthenticated(session.userId)   // unchanged; one small habits query
container.refreshAuthState()
container.seedLocalDataIfEmpty()                         // no sync inside any more
container.syncInBackground(SyncReason.POST_SIGN_IN)      // applicationScope.launch { sync }
_events.emit(AuthEvent.Success)
```

- `syncInBackground` uses `applicationScope`, so the sync continues after the Auth screen closes.

**`seedLocalDataIfEmpty`:**

- It drops the 5 s pre-reconcile sync.
- The want-activity reconcile must run after want_activities are pulled, or it pushes a duplicate set of seed wants.
- For a signed-in user, it launches a job in `applicationScope`. The job waits for `WANT_ACTIVITIES in pullProgress.tables`, then runs `reconcile`.
- For a guest, or when the table is already pulled, the job runs `reconcile` at once.

**Startup (`AppNavigation`).** This applies to a signed-in user on a device where `HABITS` is not yet pulled:

1. Start `syncInBackground`.
2. Wait up to 5 s for `HABITS` to be pulled.
3. Then route:
   - If `HABITS` is pulled, `IsOnboarded` decides, as now.
   - If the wait timed out, go to **Home**, not Onboarding. A signed-in user who has habits on the server must not land in Onboarding because of a slow network. Home shows the skeletons.

In every other case, startup routes as now, without a wait.

### 4.8 Remote fetch for recent logs

Two new `SupabaseSyncClient` methods:

```kotlin
suspend fun fetchHabitLogsLoggedFrom(userId: String, fromMs: Long): List<HabitLog>
suspend fun fetchWantLogsLoggedFrom(userId: String, fromMs: Long): List<WantLog>
```

- Each one is `fetchPaged` with `eq(user_id)` and `gte(logged_at, from)`, ordered by `(logged_at, id)`.
- It has the same keyset paging as PR #30.
- `FakePostgrest` learns `gte`.

## 5. Edge cases

| Case | Behaviour |
|---|---|
| Guest | Every section is ready. No skeleton. |
| New user: sign-up, server empty | Local rows are migrated. The first sync pulls empty tables and marks them pulled within one round trip each. The skeleton shows only briefly. |
| Existing user, fresh device | Sign-in opens Home at once. The identity strip, then the habit list, the points and the wants appear in order. The streak appears last. |
| Upgraded install with watermarks | The tables count as pulled at once (§4.2). No skeleton. |
| Sync fails mid-way | Finished steps stay done. The other sections show a static skeleton and the retry notice. |
| Sign-out | `reset()` clears the flags. The next account starts with skeletons. |
| User logs a habit during stage 3 | The habit card is ready (stage 2 is done). The log is written locally and pushed on the next sync. |
| Day changes during a long pull | `recentFrom` is fixed when the sync starts. Stage 3 fills anything before it. |

## 6. Tests

- **`SyncEngineTest`:**
  - stage order: core tables are marked pulled before any log fetch
  - an empty table is marked pulled
  - stage 2 runs only on a first pull, and it does not move the log watermarks
  - a failure in stage 3 keeps stages 1 and 2 marked
- **`SyncWatermarkStore` / `InMemoryWatermarks`:**
  - `reset()` clears the flags
  - a watermark above 0 counts as pulled
- **`TodaySectionTest`:** the table in §4.3, plus the guest case
- **`PostgrestSupabaseSyncClientTest`:** the two `LoggedFrom` fetches return every row from `from` on, across page seams
- **No Android ViewModel test.** The app has no HomeViewModel test setup. `HomeViewModel` only combines `pullProgress` with `authState` through `readySections`, which `TodaySectionTest` covers.
- **Manual:**
  - sign in on a fresh install of an account with long history
  - confirm Home opens at once and the sections fill in the order of §4.1
  - confirm the streak never shows a wrong value first
