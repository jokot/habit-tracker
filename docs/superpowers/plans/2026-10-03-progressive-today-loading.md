# Progressive Today loading: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sign-in opens Home at once. Each Today section shows a shimmer skeleton until the data it needs is local.

**Architecture:**
- `SyncEngine.pull` runs in three stages and records each finished table in `PullProgress`. The stages are core tables, then recent logs, then the full log history.
- `SyncWatermarkStore` stores `PullProgress` and exposes it as a `StateFlow`.
- A pure function maps `PullProgress` to the set of ready `TodaySection`s.
- HomeScreen renders each section from that set, with a skeleton for each section that is not ready.

**Tech Stack:** Kotlin Multiplatform 2.1.0, SQLDelight, supabase-kt 3.0.2, Ktor MockEngine (tests), kotlinx-datetime 0.6.1, Compose Material3.

**Spec:** `docs/superpowers/specs/2026-10-03-progressive-today-loading-design.md`
**Design:** https://claude.ai/artifact/EcfigqZwVUrFuqe3qPD5ix (artboards 1, 2 and 3)

## Global Constraints

- **No partial numbers.** The streak, the 7-day strip, earned, spent and balance show only when their section is ready (spec §4.3).
- **Guest users** see every section ready at once.
- **The shimmer** uses `surfaceVariant` as the base colour and `surface` as the highlight. One sweep is 1200 ms, `LinearEasing`, from left to right.
- **The shimmer is static** when the sync failed or when the system animator scale is 0.
- **Error notice copy:** "Couldn't finish loading. Pull down to try again." The button label is "Retry".
- **Stage 2 does not move any watermark.** It runs only while `recentLogs` is false and the two log tables are not both pulled.
- **Before the first commit,** revert the PR #30 test-only edit with `git checkout mobile/shared/src/commonMain/kotlin/com/habittracker/data/sync/PostgrestSupabaseSyncClient.kt` (`SYNC_PAGE_SIZE = 2L`).
- **Every commit message** ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Test commands:**
  - `./gradlew :mobile:shared:jvmTest`
  - `./gradlew :mobile:shared:compileDebugKotlinAndroid`
  - `./gradlew :mobile:androidApp:testDebugUnitTest`

## Review Focus

1. **The user signs out during the first pull.** The deferred want reconcile must not run for the old user, and it must not run for the next user on stale data. Task 5 covers this with a user-id check after the wait.
2. **Two deferred reconciles run at once,** one from startup and one from sign-in. This can insert duplicate seed wants. Task 5 cancels the earlier job before it starts a new one.
3. **An upgraded install has watermarks but no flags.** Its sections must be ready at once and must not shimmer. Task 1 covers this with a test.
4. **A table that is empty on the server** must count as pulled, or its section shimmers forever. Task 2 covers this with a test.
5. **A sync fails in stage 3.** The flags from stages 1 and 2 must stay set, and the log watermarks must not move. Task 2 covers this with a test.

---

### Task 1: PullProgress in the watermark store

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/local/SyncWatermarkStore.kt`. Add `PullProgress` next to `SyncTable`.
- Modify: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/sync/SyncEngineTest.kt`. Update `InMemoryWatermarks`.
- Test: `mobile/shared/src/jvmTest/kotlin/com/habittracker/data/local/SyncWatermarkStoreTest.kt`

**Interfaces:**
- Produces:
  - `data class PullProgress(val tables: Set<SyncTable> = emptySet(), val recentLogs: Boolean = false)`
  - `WatermarkReader.progress: StateFlow<PullProgress>`
  - `WatermarkReader.markPulled(table: SyncTable)`
  - `WatermarkReader.markRecentLogsPulled()`

- [ ] **Step 1: Write the failing test** (jvmTest, because the JVM `SyncPreferences` has a no-argument constructor)

```kotlin
class SyncWatermarkStoreTest {
    private val store = SyncWatermarkStore(SyncPreferences())

    @Test fun `a fresh store has nothing pulled`() =
        assertEquals(PullProgress(), store.progress.value)

    @Test fun `markPulled adds the table to the flow`() {
        store.markPulled(SyncTable.HABITS)
        assertEquals(setOf(SyncTable.HABITS), store.progress.value.tables)
    }

    @Test fun `a watermark above zero counts as pulled, for upgraded installs`() {
        val prefs = SyncPreferences()
        SyncWatermarkStore(prefs).set(SyncTable.HABIT_LOGS, 5L)
        assertEquals(setOf(SyncTable.HABIT_LOGS), SyncWatermarkStore(prefs).progress.value.tables)
    }

    @Test fun `reset clears the flags and the recent-logs flag`() {
        store.markPulled(SyncTable.HABITS)
        store.markRecentLogsPulled()
        store.set(SyncTable.HABITS, 9L)
        store.reset()
        assertEquals(PullProgress(), store.progress.value)
    }
}
```

- [ ] **Step 2: Run the test.** It must fail.
  - Run: `./gradlew :mobile:shared:jvmTest --tests '*SyncWatermarkStoreTest'`
  - Expected: compile error, because `progress` is not defined.

- [ ] **Step 3: Implement**

Add to `SyncWatermarkStore.kt`, below `SyncTable`:

```kotlin
/** What the first sync on this device has pulled so far. */
data class PullProgress(
    /** Tables whose whole history is local. */
    val tables: Set<SyncTable> = emptySet(),
    /** Habit logs and want logs of the last 7 days are local. */
    val recentLogs: Boolean = false,
)
```

In `SyncWatermarkStore.kt`:

```kotlin
interface WatermarkReader {
    fun get(table: SyncTable): Long
    fun set(table: SyncTable, valueMs: Long)
    /** Which tables have finished their first pull. Survives restarts. */
    val progress: StateFlow<PullProgress>
    fun markPulled(table: SyncTable)
    fun markRecentLogsPulled()
}

class SyncWatermarkStore(private val prefs: SyncPreferences) : WatermarkReader {
    private val _progress = MutableStateFlow(load())
    override val progress: StateFlow<PullProgress> = _progress.asStateFlow()

    override fun get(table: SyncTable): Long = prefs.getLong(prefixed(table))
    override fun set(table: SyncTable, valueMs: Long) = prefs.putLong(prefixed(table), valueMs)

    override fun markPulled(table: SyncTable) {
        prefs.putLong(pulledKey(table), 1L)
        _progress.update { it.copy(tables = it.tables + table) }
    }

    override fun markRecentLogsPulled() {
        prefs.putLong(RECENT_LOGS_KEY, 1L)
        _progress.update { it.copy(recentLogs = true) }
    }

    /** Reset all watermarks and pull flags to 0 — call after wiping local data so the next pull
     *  fetches everything from the server (otherwise pull skips rows that
     *  arrived before the cached watermark and Home stays empty). */
    fun reset() {
        SyncTable.entries.forEach { table ->
            prefs.putLong(prefixed(table), 0L)
            prefs.putLong(pulledKey(table), 0L)
        }
        prefs.putLong(RECENT_LOGS_KEY, 0L)
        _progress.value = PullProgress()
    }

    /** A watermark above 0 also counts: installs from before the flags existed pulled already. */
    private fun load() = PullProgress(
        tables = SyncTable.entries.filter { prefs.getLong(pulledKey(it)) == 1L || get(it) > 0L }.toSet(),
        recentLogs = prefs.getLong(RECENT_LOGS_KEY) == 1L,
    )

    private fun prefixed(table: SyncTable) = "watermark.${table.key}"
    private fun pulledKey(table: SyncTable) = "pulled.${table.key}"

    private companion object { const val RECENT_LOGS_KEY = "pulled.recent_logs" }
}
```

Update `InMemoryWatermarks` in `SyncEngineTest.kt` with the same flow logic, but without the persistence.

- [ ] **Step 4: Run** `./gradlew :mobile:shared:jvmTest`. Expected: PASS, every test.
- [ ] **Step 5: Commit** `feat(sync): record which tables finished their first pull`

### Task 2: Staged pull in SyncEngine, and the recent-log fetch

**Files:**
- Modify: `SupabaseSyncClient.kt`, `PostgrestSupabaseSyncClient.kt`, `SyncEngine.kt`
- Modify (tests): `FakeSupabaseSyncClient.kt`, `FakePostgrest.kt` (add `gte`), `SyncEngineTest.kt`, `PostgrestSupabaseSyncClientTest.kt`

**Interfaces:**
- Consumes: Task 1 `WatermarkReader.progress`, `markPulled` and `markRecentLogsPulled`.
- Produces:
  - `SupabaseSyncClient.fetchHabitLogsLoggedFrom(userId: String, fromMs: Long): List<HabitLog>`
  - `SupabaseSyncClient.fetchWantLogsLoggedFrom(userId: String, fromMs: Long): List<WantLog>`
  - `SyncEngine.pullProgress: StateFlow<PullProgress>`
  - new `SyncEngine` constructor parameters: `clock: Clock = Clock.System` and `timeZone: TimeZone = TimeZone.currentSystemDefault()`

- [ ] **Step 1: Write the failing tests**

In `SyncEngineTest`:
- **Core tables before any log fetch.** `FakeSupabaseSyncClient` records each fetch name in `val fetches = mutableListOf<String>()`. After a first sync, assert this order:

  ```
  user_identities, habits, habit_identities, want_activities,
  habit_logs_recent, want_logs_recent, habit_logs, want_logs
  ```
- **An empty server marks every table pulled.** It also sets `recentLogs`.
- **The second sync skips stage 2.** The `fetches` of the second sync do not contain `*_recent`.
- **Stage 2 does not move the log watermarks.** Make `fetchHabitLogsSince` throw. Then assert:
  - `watermarks.get(HABIT_LOGS) == 0`
  - `HABITS in progress.tables`
  - `recentLogs == true`
  - `HABIT_LOGS !in progress.tables`
  - `syncState is Error`

  To make one named fetch throw, add `var throwOn: String? = null` to the fake.

In `PostgrestSupabaseSyncClientTest`, add one test per log table. It seeds 40 rows across the `from` instant, with three rows to each timestamp. It asserts that `fetchHabitLogsLoggedFrom(USER, SINCE_MS)` returns every row of USER with `logged_at >= SINCE`, across the 4-row cap. Repeat for want logs.

- [ ] **Step 2: Run** `./gradlew :mobile:shared:jvmTest`. Expected: compile errors for the new methods.

- [ ] **Step 3: Implement**

`PostgrestSupabaseSyncClient`:

```kotlin
override suspend fun fetchHabitLogsLoggedFrom(userId: String, fromMs: Long): List<HabitLog> =
    fetchPaged<HabitLogDto>(
        "habit_logs",
        orderBy = listOf("logged_at", "id"),
        keyOf = { listOf(it.loggedAt.asCursorTime(), it.id) },
    ) {
        eq("user_id", userId)
        gte("logged_at", Instant.fromEpochMilliseconds(fromMs).toString())
    }.map { it.toDomain() }
// fetchWantLogsLoggedFrom: same shape, table "want_logs", WantLogDto.
```

`FakePostgrest.condition` gains `"gte" -> compareField(field, value) >= 0`.

`SyncEngine.pull`:

```kotlin
private suspend fun pull(userId: String): Int {
    // Stage 1, small tables: each one unblocks a Today section as soon as it lands.
    var pulled = pullUserIdentities(userId) + pullHabits(userId) +
        pullHabitIdentities(userId) + pullWantActivities(userId)
    // Stage 2, first pull only: the last 7 days of logs, for today's progress and the
    // week's points. Stage 3 pulls them again, so this moves no watermark.
    if (!recentLogsLocal()) pullRecentLogs(userId)
    // Stage 3: full history, for the streak.
    pulled += pullHabitLogs(userId) + pullWantLogs(userId)
    return pulled
}

private fun recentLogsLocal(): Boolean = watermarks.progress.value.let {
    it.recentLogs || (SyncTable.HABIT_LOGS in it.tables && SyncTable.WANT_LOGS in it.tables)
}

private suspend fun pullRecentLogs(userId: String) {
    val today = clock.now().toLocalDateTime(timeZone).date
    // 6 days back covers the 7-day strip, and it is never later than Monday.
    val fromMs = today.minus(6, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
    supabase.fetchHabitLogsLoggedFrom(userId, fromMs).forEach { habitLogRepo.mergePulled(it) }
    supabase.fetchWantLogsLoggedFrom(userId, fromMs).forEach { wantLogRepo.mergePulled(it) }
    watermarks.markRecentLogsPulled()
}
```

Each `pullX` calls `watermarks.markPulled(<its table>)` on every success path, including the `remote.isEmpty()` early return. Write it as a guard, for example `if (remote.isEmpty()) return 0.also { watermarks.markPulled(SyncTable.HABITS) }`, or as a small `markedPulled(table) { … }` helper. Add `val pullProgress: StateFlow<PullProgress> = watermarks.progress`.

- [ ] **Step 4: Run** `./gradlew :mobile:shared:jvmTest`. Expected: PASS, every test.
- [ ] **Step 5: Commit** `feat(sync): pull core tables, then recent logs, then history`

### Task 3: Today readiness

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/TodaySection.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/model/TodaySectionTest.kt`

**Interfaces:**
- Produces:
  - `enum class TodaySection { IDENTITIES, HABITS, POINTS, STREAK, WANTS }`
  - `fun PullProgress.readySections(isAuthenticated: Boolean): Set<TodaySection>`

- [ ] **Step 1: Write the failing test.** The test covers:
  - a guest with an empty progress gets all five sections
  - a signed-in user with an empty progress gets none
  - `{USER_IDENTITIES}` gives `{IDENTITIES}`
  - `{HABITS}` with recentLogs gives `{HABITS, POINTS}`
  - `{HABITS, HABIT_LOGS}` without recentLogs gives `{STREAK}`
  - `{HABITS, WANT_ACTIVITIES, HABIT_LOGS, WANT_LOGS}` without the recentLogs flag gives `{HABITS, POINTS, STREAK, WANTS}`, because both log tables count as recent
  - `{WANT_ACTIVITIES}` with recentLogs gives no WANTS, because WANTS waits for the history
- [ ] **Step 2: Run the test.** Expected: FAIL to compile.
- [ ] **Step 3: Implement**

```kotlin
/** The parts of Today that load on their own. */
enum class TodaySection { IDENTITIES, HABITS, POINTS, STREAK, WANTS }

/**
 * Sections whose data is all local. A section never shows a number from part of
 * its data: the streak counts from the first log ever, so it waits for the whole
 * history, and so do wants, whose spend rate comes from the streak.
 */
fun PullProgress.readySections(isAuthenticated: Boolean): Set<TodaySection> {
    if (!isAuthenticated) return TodaySection.entries.toSet()
    val recent = recentLogs || (SyncTable.HABIT_LOGS in tables && SyncTable.WANT_LOGS in tables)
    val history = SyncTable.HABIT_LOGS in tables
    return buildSet {
        if (SyncTable.USER_IDENTITIES in tables) add(TodaySection.IDENTITIES)
        if (SyncTable.HABITS in tables && recent) { add(TodaySection.HABITS); add(TodaySection.POINTS) }
        if (SyncTable.HABITS in tables && history) add(TodaySection.STREAK)
        if (SyncTable.WANT_ACTIVITIES in tables && recent && history) add(TodaySection.WANTS)
    }
}
```

- [ ] **Step 4: Run** `./gradlew :mobile:shared:jvmTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(home): map pull progress to the Today sections that can show`

### Task 4: Skeleton components

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/Skeleton.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/streak/StreakStrip.kt` (`DailyStatusCard`)

**Interfaces:**
- Produces:
  - `val LocalSkeletonAnimated: ProvidableCompositionLocal<Boolean>` (default true)
  - `Modifier.shimmer()`
  - `SkeletonBlock(width: Dp, height: Dp, shape: Shape, modifier)`
  - `IdentityStripSkeleton()`, `HabitCardSkeleton()`, `WantCardSkeleton()`, `SectionSubtitleSkeleton(width: Dp)`
  - `DailyStatusCard(..., streakLoading: Boolean = false, pointsLoading: Boolean = false)`

- [ ] **Step 1: Implement `Skeleton.kt`**

```kotlin
/** False while the sync that would fill the skeletons has failed: they stop moving. */
val LocalSkeletonAnimated = staticCompositionLocalOf { true }

/** Placeholder fill with a light band sweeping left to right, once every 1200 ms. */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    if (!LocalSkeletonAnimated.current || animationsDisabled()) return@composed background(base)
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer",
    )
    drawBehind {
        drawRect(base)
        val band = size.width
        val x = -band + progress * (size.width + band)
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, highlight, Color.Transparent), startX = x, endX = x + band))
    }
}

@Composable
private fun animationsDisabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
fun SkeletonBlock(width: Dp, height: Dp, shape: Shape = RoundedCornerShape(6.dp), modifier: Modifier = Modifier) =
    Box(modifier.size(width, height).clip(shape).shimmer())
```

The section skeletons copy the real paddings and sizes:
- `IdentityStripSkeleton`: the "I AM" label, then pills of 84, 72 and 92 by 30 dp. It has 16 dp side padding and 12 dp bottom padding.
- `HabitCardSkeleton`: a 16 dp-radius `Surface` with a 1 dp `outlineVariant` border and 12/14 dp padding. It holds a 44 dp glyph block, a 14 dp title line, a 10 dp subtitle line and a full-width 4 dp bar.
- `WantCardSkeleton`: the same card, without the bar.

The line widths come from a `width` parameter, so three stacked cards do not look identical. Each section skeleton sets `Modifier.semantics { contentDescription = "Loading" }`.

- [ ] **Step 2: Add the loading branches to `DailyStatusCard`**
  - When `streakLoading` is true:
    - the 44 dp icon box, the number and the caption are replaced by `SkeletonBlock(44.dp, 44.dp, RoundedCornerShape(12.dp))`, a `SkeletonBlock(96.dp, 30.dp)` and a `SkeletonBlock(168.dp, 12.dp)`
    - each of the 7 heat cells becomes `SkeletonBlock(32.dp, 32.dp, RoundedCornerShape(8.dp))` under its weekday label
    - day taps are off
  - When `pointsLoading` is true:
    - each value text becomes `SkeletonBlock(36.dp, 22.dp)` above its label
    - the balance tap is off
- [ ] **Step 3: Compile** with `./gradlew :mobile:androidApp:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `feat(ui): shimmer skeletons for the Today sections`

### Task 5: Non-blocking sign-in and startup

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/auth/AuthViewModel.kt:129-135`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt:99-127`

**Interfaces:**
- Consumes: Task 2 `SyncEngine.pullProgress`.
- Produces:
  - `AppContainer.pullProgress: StateFlow<PullProgress>`
  - `AppContainer.syncInBackground(reason: SyncReason)`

- [ ] **Step 1: Change `AppContainer`**

```kotlin
val pullProgress: StateFlow<PullProgress> = syncEngine.pullProgress

/** Starts a sync that outlives the caller's screen. Results arrive through the local flows. */
fun syncInBackground(reason: SyncReason) {
    applicationScope.launch { syncEngine.sync(reason) }
}

private var reconcileJob: Job? = null
```

`seedLocalDataIfEmpty()` keeps the identity seed and drops the `withTimeoutOrNull(5_000) { sync }` block. Its reconcile becomes:

```kotlin
// Reconcile must see the user's pulled wants, or it pushes a second seed set.
// A signed-in user waits for want_activities, in the background.
val userId = currentUserId()
reconcileJob?.cancel()
reconcileJob = applicationScope.launch {
    if (isAuthenticated()) pullProgress.first { SyncTable.WANT_ACTIVITIES in it.tables }
    if (currentUserId() != userId) return@launch // signed out meanwhile
    runCatching { setupUserWantActivitiesUseCase.reconcile(userId) }
        .onFailure { e -> android.util.Log.w("AppContainer", "Want-activity reconcile failed", e) }
}
```

- [ ] **Step 2: Change `AuthViewModel.completeSignIn`.** Replace `container.syncEngine.sync(SyncReason.POST_SIGN_IN)` with `container.syncInBackground(SyncReason.POST_SIGN_IN)`.

- [ ] **Step 3: Change `AppNavigation` startup.** Replace the "fresh device" block with:

```kotlin
val userId = container.currentUserId()
val habitsPulled = { SyncTable.HABITS in container.pullProgress.value.tables }
startDestination = if (container.isAuthenticated() && !habitsPulled()) {
    container.syncInBackground(SyncReason.POST_SIGN_IN)
    val pulled = withTimeoutOrNull(5_000L) {
        container.pullProgress.first { SyncTable.HABITS in it.tables }
    } != null
    // Timed out: Home with skeletons. Onboarding would be wrong for a user whose habits are still on the way.
    if (!pulled || container.isOnboardedUseCase.execute(userId)) Screen.Home.route else Screen.Onboarding.route
} else {
    if (container.isOnboardedUseCase.execute(userId)) Screen.Home.route else Screen.Onboarding.route
}
```

- [ ] **Step 4: Run the checks**
  - `./gradlew :mobile:androidApp:compileDebugKotlin :mobile:androidApp:testDebugUnitTest`
  - Expected: PASS.
- [ ] **Step 5: Commit** `feat(auth): open Home at once after sign-in and sync in the background`

### Task 6: Home renders each section from its readiness

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt:192-389`

**Interfaces:**
- Consumes:
  - Task 3 `readySections`
  - Task 4 skeletons and `LocalSkeletonAnimated`
  - Task 5 `container.pullProgress`
- Produces:
  - `HomeViewModel.readySections: StateFlow<Set<TodaySection>>`
  - `HomeViewModel.loadFailed: StateFlow<Boolean>`

- [ ] **Step 1: Change `HomeViewModel`**

```kotlin
val readySections: StateFlow<Set<TodaySection>> =
    combine(container.authState, container.pullProgress) { auth, progress ->
        progress.readySections(auth.isAuthenticated)
    }.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        container.pullProgress.value.readySections(container.isAuthenticated()),
    )

/** The last sync failed while some section still has no data. */
val loadFailed: StateFlow<Boolean> =
    combine(syncState, readySections) { state, ready ->
        state is SyncState.Error && ready.size < TodaySection.entries.size
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
```

- [ ] **Step 2: Change `HomeScreen`**
  - Delete the full-screen `CircularProgressIndicator` branch. Compute `val ready = if (uiState.isLoading) emptySet() else readySections`.
  - Wrap the `LazyColumn` in `CompositionLocalProvider(LocalSkeletonAnimated provides !loadFailed)`.
  - Above the identity strip, when `loadFailed`, add a `LoadFailedNotice(onRetry = viewModel::triggerManualSync)` item:
    - a `WarnContainer` / `OnWarnContainer` row with 14 dp radius and 16 dp side margin
    - a wifi-off icon
    - the text "Couldn't finish loading. Pull down to try again."
    - a `TextButton("Retry")`
  - The **identity strip** shows `IdentityStripSkeleton()` when `IDENTITIES !in ready`.
  - The **stat card** gets `streakLoading = STREAK !in ready` and `pointsLoading = POINTS !in ready`.
  - The **habits header** shows the "x of y done" text only when HABITS is ready. Otherwise it shows `SectionSubtitleSkeleton(88.dp)`.
  - The **habit items:**
    - when HABITS is not ready: `items(3) { HabitCardSkeleton(...) }`, with the same 16 dp side padding and 8 dp top padding
    - when it is ready: the current empty state or cards
  - The **wants:**
    - when WANTS is not ready: the "Wants" title, `SectionSubtitleSkeleton(176.dp)` and `items(2) { WantCardSkeleton(...) }`
    - when it is ready: the current block, which still shows only when `wantActivities` is not empty
- [ ] **Step 3: Run the checks**
  - `./gradlew :mobile:shared:jvmTest :mobile:shared:compileDebugKotlinAndroid :mobile:androidApp:testDebugUnitTest`
  - Expected: PASS.
- [ ] **Step 4: Commit** `feat(home): load each Today section on its own, with skeletons`

### Task 7: Docs and PR

- [ ] **Step 1: Update the PR #30 body.**
  - Add a "Progressive Today loading" section with links to the spec, the plan and the design.
  - Keep `Refs #20`. This work delivers issue #20 item 3.
- [ ] **Step 2: Push** with `GH_TOKEN=$(gh auth token --user jokot) git push`.
