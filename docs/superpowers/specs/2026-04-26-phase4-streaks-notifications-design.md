# Phase 4 — Streaks + Notifications + Settings

**Date:** 2026-04-26
**Phase:** 4 (per master spec §12)
**Depends on:** Phase 1 (foundation), Phase 2 (core loop), Phase 3 (sync + auth)
**Branch:** `feature/phase4-streaks-notifications`
**Worktree:** `.worktrees/phase4-streaks-notifications`

---

## 1. Goal

Make the streak system tangible and motivating so users feel daily progress, and add the notification scaffolding that nudges them when streaks are at risk. Plus a Settings screen to host notification toggles, account actions, and About — replacing the Home overflow menu as the place to manage the app.

**Success metric:** User opens Streak History screen at least once per week; daily reminder notification has > 30% tap-through during onboarding week.

**Why this phase now:** Streaks are the spec's core retention promise (§3). Phase 3 unblocked cross-device data continuity; Phase 4 turns that data into visible progress + behavioral nudges. Notifications are necessary for the "never miss twice" rule to hold without the user opening the app.

---

## 2. Scope

### In Scope

- **Overall streak v1** — 30-day grid on Home + dedicated Streak History screen with month-by-month scroll back to first log date.
- **Streak computation** — on-device, derived from `habit_logs`, no new tables, no migration, no sync.
- **Pull-to-refresh on Home** — Material 3 `PullToRefreshBox` triggers `SyncEngine.sync(reason = MANUAL_PULL)`.
- **Settings screen** — Notifications + Account + About sections. Replaces Home overflow menu (sign out + sign in moved here).
- **Notifications (4 types):**
  - Daily reminder — fires at user-configured time (default 9:00 AM) if no habit logged yet today.
  - Streak at risk — fires at user-configured time (default 9:00 PM) if streak active and nothing logged today.
  - Streak frozen — fires when day rolls over and yesterday was missed but streak survived (one-miss tolerance).
  - Streak reset — fires when streak transitions COMPLETE → BROKEN (two consecutive misses).
- **Notification preferences** stored in DataStore (per-type enable + per-type time for the two scheduled reminders).
- **POST_NOTIFICATIONS permission** flow on Android 13+ with re-prompt path via Settings screen.
- **Point rollover cap** — implement the master spec §2.3 rule (`unspent ≤ daily_earn_cap × 2` carried per midnight, week reset Monday 00:00 local). Local TZ fix in `GetPointBalanceUseCase` (currently UTC).

### Out of Scope (Deferred)

- **Per-habit streak** — Phase 5+ per master spec §3.2.
- **Freeze earn/spend mechanics** — Phase 4 only renders FROZEN state if data has it; no UI to spend freezes. Deferred to Phase 5.
- **Want-timer end notification** — Phase 5 with timer feature.
- **Milestone notifications** (7/14/30/100-day) — backlog.
- **Habit/Want CRUD UI** — Phase 5.
- **Theme picker (light/dark/system)** — backlog. App currently follows system.
- **Data export / backup** — backlog.
- **Crashlytics / Firebase** — last phase before launch.
- **iOS widgets** — already deferred per master spec.

---

## 3. Streak System (v1)

### 3.1 Day States

Per master spec §3.1:

| State | Color Token | Meaning |
|-------|------------|---------|
| `COMPLETE` | `StreakComplete` (green) | User logged ≥ 1 active habit check-in that day. |
| `FROZEN` | `StreakFrozen` (blue) | Missed day, but isolated — streak survives. |
| `BROKEN` | `StreakBroken` (red) | Second consecutive miss — streak resets on this day. |
| `EMPTY` | `StreakEmpty` (grey) | Before user's first log (anchor). Never applied to today. |
| `TODAY_PENDING` | `StreakEmpty` + `StreakTodayOutline` ring | Today, no log yet (regardless of whether anchor exists yet — fresh user on day 1 with no logs still sees today as `TODAY_PENDING` outlined cell, all prior days `EMPTY`). |

All five colors already defined in `ui/theme/Color.kt` with WCAG AA verified contrast for both light + dark mode.

### 3.2 Compute Rules

- **Day boundary:** local calendar day in `TimeZone.currentSystemDefault()`. `loggedAt: Instant` projected to `LocalDate`.
- **A "log" for streak purposes** = any non-deleted `HabitLog` row for that `userId` on that `LocalDate`.
- **Deleted-log handling:** current UI never soft-deletes a log (the 3-second tap-to-commit window cancels *before* DB write — no `deleted_at` rows produced today). Phase 5 may add explicit log-delete UI; the streak query already excludes `deleted_at IS NOT NULL` rows defensively.
- **Logs of deleted *habits*:** when Phase 5 lets users delete a habit (entity), its `HabitLog` rows persist. Streak compute still counts those days as `COMPLETE` — the work happened; the habit just isn't tracked going forward.
- **Anchor** = date of user's first log (`MIN(logged_at)` for `userId`). Days before anchor = `EMPTY`. No "anchor at signup" — auth is optional, streaks belong to the local data.
- **State derivation** (walk forward from anchor to today):
  - If `dayHasLog` → `COMPLETE`. Reset miss counter. Increment current streak.
  - If `!dayHasLog && previousDayWasComplete` → `FROZEN`. (One-miss tolerance.)
  - If `!dayHasLog && previousDayWasFrozen` → `BROKEN`. Reset current streak to 0.
  - If `!dayHasLog && previousDayWasBroken` → `BROKEN` (chain of misses, all red).
  - Today with no log → `TODAY_PENDING` (special-cased so today never shows as broken yet).
- **Current streak** = consecutive `COMPLETE` days ending at today (or yesterday if today is `TODAY_PENDING`). Anchored at last `BROKEN` or anchor.
- **Longest streak** = max consecutive `COMPLETE` run ever observed.

### 3.3 Performance Posture

On-device compute. No persisted derived state.

- Single-pass O(N) scan per render. N = log count (small at realistic scale).
- Home strip = last 30 days only (bounded constant work).
- StreakScreen = lazy per month — load logs for one month, compute that month's days, render. Never load all-time logs at once.
- Reactive Flow recomputes on log insert. Cost lost in noise vs UI animation budget.
- If ever slow: memoize on `(userId, log_count, max_updated_at)`. Not added Phase 4.

### 3.4 Use Case API (shared / commonMain)

```kotlin
// domain/usecase/ComputeStreakUseCase.kt
class ComputeStreakUseCase(
    private val habitLogRepository: HabitLogRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun observeRange(userId: String, range: DateRange): Flow<StreakRangeResult>
    fun observeCurrent(userId: String): Flow<StreakSummary>

    // Synchronous compute for Workers (no Flow subscription, single read).
    suspend fun computeNow(userId: String, range: DateRange): StreakRangeResult
    suspend fun computeSummaryNow(userId: String): StreakSummary
}

data class StreakRangeResult(
    val days: List<StreakDay>,           // each day in range, oldest first
    val firstLogDate: LocalDate?,        // null if no logs yet
)

data class StreakDay(
    val date: LocalDate,
    val state: StreakDayState,
)

enum class StreakDayState { COMPLETE, FROZEN, BROKEN, EMPTY, TODAY_PENDING }

data class StreakSummary(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalDaysComplete: Int,
    val firstLogDate: LocalDate?,
)
```

`observeCurrent` is a convenience derived from `observeRange(anchor..today)`. UI uses `observeRange` with a 30-day window for Home, and per-month windows for StreakScreen.

---

## 4. Point Rollover (Daily Cap)

Master spec §2.3 rule: "unspent points carry to next day, max = `daily_earn_cap × 2`. Resets every Monday 00:00."

### 4.1 What's already in code

`GetPointBalanceUseCase`:
- Filters logs to current week (Monday 00:00 UTC onwards). Monday reset = ✅ done.
- Earned per day capped at habit `dailyTarget`. Daily-target cap = ✅ done.
- **Missing:** the per-day rollover ceiling. Today, all earned points within the week accumulate without the `daily_earn_cap × 2` cap on yesterday→today carryover.

### 4.2 Definition

- **`daily_earn_cap` (per user)** = `sum of habit.dailyTarget for all active habits of user`. Derived, not stored. If user has no habits, cap = 0.
- **Rollover cap** = `daily_earn_cap × 2`. Caps the *unspent* balance carried from previous day into current day. Anything above the cap is forfeited at midnight.
- **Reset:** every Monday 00:00 local — clear carried-over balance entirely (existing weekStart filter handles this; revisit TZ below).

### 4.3 Compute Algorithm

Replace the simple "sum filtered logs" with day-walking compute, week-bounded:

```
balance = 0
for each day from weekStart to today (in user TZ):
    earnedToday = sum over each habit:
        min(sum(quantity_i × pointsPerThreshold), habit.dailyTarget)
    spentToday = sum over want logs that day
    midnightCap = daily_earn_cap × 2

    // At midnight rollover INTO this day, prior balance is capped:
    if day != weekStart:
        balance = min(balance, midnightCap)

    balance = balance + earnedToday - spentToday
    balance = max(0, balance)  // hard floor (existing rule)

return balance
```

`balance` returned at end of loop = current point balance. No persistence.

### 4.4 Timezone Fix

Current `GetPointBalanceUseCase.currentWeekStart()` uses `TimeZone.UTC`. **Bug for non-UTC users** — Monday reset fires at wrong wall-clock moment.

Fix in Phase 4: switch to `TimeZone.currentSystemDefault()` everywhere day boundaries matter (streak compute, point compute, worker scheduling). Document the limitation: travelling across timezones may shift week boundaries by a day; acceptable for v1.

### 4.5 Edge Cases

- User just signed up (no habits) → `daily_earn_cap = 0` → cap = 0 → carryover always zero → balance only reflects today's earn − spend within week.
- User adds habits mid-week → cap grows for subsequent days. Past days unaffected.
- Logs imported via cloud restore at 11:59 PM → recomputed on next read; rollover cap applied at next midnight as normal.
- Week boundary crossing: Monday 00:00 wipes carry. Sunday's leftover points lost (matches spec §2.3 explicit rule).
- DST transition: rare but rollover compute uses date arithmetic, not duration — robust to DST.

### 4.6 No UI Change

Phase 4 keeps the existing point-balance UI. Only the compute logic changes. User-visible effect: balance may be lower than today after a high-earn / no-spend day if it crosses midnight with > cap unspent. Edge case for most users in v1.

(Optional Phase 4+: add a "Rolled over: X pts" / "Lost: Y pts" indicator. Defer — UI noise without proven need.)

### 4.7 Testing

`GetPointBalanceUseCaseTest` updates:
- Single-day earn within cap → balance = earned − spent (existing).
- Two-day stretch, day 1 earn > cap → day 2 starts with cap, not full earn.
- Two-day stretch, day 1 earn < cap → day 2 starts with day 1 actual balance.
- Spent before midnight reduces what carries.
- Spent after midnight against capped balance never goes negative.
- Week reset on Monday wipes carry.
- TZ change: weekStart must reflect local Monday, not UTC Monday.
- User with no habits: cap = 0, balance never positive.
- User adds habit mid-week: cap from new day forward.

---

## 5. UI Components

### 5.1 Home Screen — Additions

**StreakStrip** (placed below TopAppBar, above habit list):

- Header row: `Icons.Default.Whatshot` flame icon (16.dp, `primary` color) + "Streak" label, current streak number bolded ("12 days"), "View all" `TextButton` on right → navigates to StreakScreen.
- Grid: 30 colored rounded squares (8.dp radius, 16.dp size, 4.dp gap) — left-to-right oldest-to-newest. Wraps to 2 rows of 15 on small phones if needed; default single row scrollable horizontally if width insufficient.
- Tap any square → opens StreakScreen scrolled to that date's month.
- Empty state (no logs yet): grid shows all `EMPTY` greys, header reads "Log your first habit to start a streak."

**Pull-to-refresh wrapper:**
- Wrap Home `LazyColumn` in `PullToRefreshBox` (Material 3, `androidx.compose.material3.pulltorefresh`).
- `isRefreshing = syncState is SyncState.Running`.
- `onRefresh = { viewModel.manualRefresh() }` → calls `SyncEngine.sync(reason = MANUAL_PULL)`.
- Existing sync chip stays visible; the refresh indicator just adds a leading spinner during pull gesture.
- Disable on guest user (no remote to refresh from) — pull does nothing, or shows brief snackbar "Sign in to sync".

**Overflow menu removal:**
- Sign out + sign in actions move to Settings.
- Top app bar gets a single `Icon(Icons.Default.Settings)` button → navigates to Settings screen.
- Sync status chip remains in top bar.

### 5.2 Streak History Screen (new)

**Route:** `Screen.StreakHistory.route = "streak-history"`

**Layout:**
- `TopAppBar` with back arrow, title "Streak History", current streak as trailing text ("Current: 12 days").
- Summary card (Bento-style, 16.dp padding, 12.dp radius): three stats side-by-side — Current / Longest / Total Days. Use `headlineMedium` for numbers, `labelMedium` for labels.
- `LazyColumn` of `MonthCalendar` items. Newest month first. Scroll back loads older months on demand.
- `MonthCalendar` composable per month:
  - Month label header (e.g., "April 2026") in `titleMedium`.
  - 7-column grid (Sun-Sat or Mon-Sun based on `Locale.getDefault().firstDayOfWeek`).
  - Day cells: 32.dp square, 6.dp radius, semantic streak color, day number centered in `labelSmall` with on-color text. Today gets outline ring.
  - Days outside the user's data range render as transparent placeholders (grid alignment preserved).
- Stop condition: when month < first-log-month, render empty state card "No data before {firstLogDate}" and stop loading further.
- Loading state per month: skeleton placeholder (light grey grid) while query resolves.

**Lazy-load mechanics:**
- ViewModel holds `MutableStateFlow<List<MonthData>>` starting with current month.
- `LazyColumn` `itemsIndexed`. Last item triggers `viewModel.loadOlderMonth()` via `LaunchedEffect`.
- `loadOlderMonth()` debounced (skip if already loading).

### 5.3 Settings Screen (new)

**Route:** `Screen.Settings.route = "settings"`

**Sections (in order):**

1. **Notifications**
   - Section header "Notifications" in `titleMedium`.
   - Permission banner (Android 13+ only, when `POST_NOTIFICATIONS` denied): warning surface, copy "Notifications are blocked. Open system settings to enable.", `TextButton "Open settings"` → launches `ACTION_APP_NOTIFICATION_SETTINGS` intent. All toggles below disabled while denied.
   - Row: Daily reminder — `Switch` + `TimePickerDialog` trigger (shows current time, e.g., "9:00 AM"). Disabled until permission granted.
   - Row: Streak at risk — `Switch` + `TimePickerDialog` trigger (default 9:00 PM).
   - Row: Streak frozen alerts — `Switch` only, no time (event-driven).
   - Row: Streak reset alerts — `Switch` only.

2. **Account**
   - Signed-in state: shows email (if available from Supabase session), `TextButton "Sign out"` (destructive — uses `error` color, separated by 16.dp gap from previous row). Reuses existing `LogoutConfirmDialog` flow with unsynced count + push-before-wipe semantics.
   - Guest state: `TextButton "Sign in to sync"` → navigates to AuthScreen (existing flow).

3. **About**
   - App version (read from `BuildConfig.VERSION_NAME`).
   - Build number (`BuildConfig.VERSION_CODE`).
   - Link "Privacy policy" — opens external URL (placeholder for now, hard-coded `https://example.com/privacy`; real URL is a backlog item).

**Layout:** standard `LazyColumn` with section dividers (16.dp vertical spacing between sections, 1.dp `HorizontalDivider` with `outline` color). Each row 56.dp min-height for touch target compliance.

### 5.4 Navigation

- Top app bar Settings icon on Home → `navController.navigate(Screen.Settings.route)`.
- Settings → Sign out → confirm dialog → on success, navigates back to Home (existing flow handles auth state propagation).
- Settings → Sign in → AuthScreen (already handled).
- Home → "View all" / streak square tap → `navController.navigate(Screen.StreakHistory.route)`.

---

## 6. Notification System (Android only, androidApp/androidMain)

### 6.1 Components

```
NotificationPreferences (DataStore)
  └─ keys: dailyReminderEnabled, dailyReminderTimeMinutes (since midnight),
           streakRiskEnabled, streakRiskTimeMinutes,
           streakFrozenEnabled, streakResetEnabled

NotificationScheduler
  └─ reschedule(prefs): cancels + re-enqueues PeriodicWorkRequests per enabled type
  └─ cancelAll()
  └─ ensureChannels(): creates 4 NotificationChannels (Android 8+)

Workers (CoroutineWorker, all under sync/notifications package)
  ├─ DailyReminderWorker
  ├─ StreakRiskWorker
  ├─ StreakFrozenWorker     ← scheduled to fire at 00:30 local each day
  └─ StreakResetWorker      ← same schedule; fires at most once per day
```

**Implementation note:** Frozen + Reset notifications fire from a single coordinator worker (`DayBoundaryWorker`) at 00:30 local each day. Each evaluates yesterday's state and emits the matching notification if its toggle is on. Diagram lists 4 logical types but only **3 PeriodicWorkRequests** are scheduled: daily reminder, streak risk, day boundary.

### 6.2 Scheduling Strategy

- `enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)` with name = stable per worker type.
- Period = 1 day. `setInitialDelay` calculates seconds until next user-chosen time.
- Constraint: none (notifications must fire even on battery saver / no network).
- WorkManager auto-restores periodic work after reboot — no `BOOT_COMPLETED` receiver.
- `NotificationScheduler.reschedule()` invoked when:
  - App starts (in `MainActivity.onCreate` after `AppContainer` initializes).
  - User toggles a switch in Settings.
  - User changes a time picker.
  - Sign-in / sign-out (since logs may have changed).

### 6.3 Worker Logic

**DailyReminderWorker (`doWork`):**
1. Read `userId` from current session via `AppContainer.currentUserId()`.
2. Query `HabitLogQueries.countLogsForDate(userId, today)` (synchronous SQLDelight).
3. If count == 0, fire notification "Log your habits to keep your streak alive." Tap → opens app to Home.
4. Reschedule next day (WorkManager handles via period).

**StreakRiskWorker (`doWork`):**
1. Same userId + today log count check.
2. Additionally: `ComputeStreakUseCase.computeSummaryNow(userId)` (synchronous, one-shot). Skip if `currentStreak == 0` (nothing at risk).
3. If count == 0 && currentStreak > 0, fire "{streak}-day streak at risk. Log a habit before midnight." (No emoji in notif title; system handles app icon.)

**DayBoundaryWorker (`doWork`):**
1. `ComputeStreakUseCase.computeNow(userId, yesterday..yesterday)` to get yesterday's `StreakDay`.
2. If `yesterday.state == FROZEN` and streakFrozenEnabled → fire "Missed yesterday. Don't miss today, or your streak resets."
3. If `yesterday.state == BROKEN` and streakResetEnabled → fire "Streak reset. Start fresh today."
4. Idempotency: store last-fired-date in DataStore; skip if already fired for that yesterday.

### 6.4 Channels

| Channel ID | Name | Importance |
|-----------|------|-----------|
| `daily_reminder` | "Daily reminder" | DEFAULT |
| `streak_risk` | "Streak alerts" | HIGH (reaches user past quiet hours via sound) |
| `streak_status` | "Streak status" | LOW (frozen / reset informational) |

### 6.5 Permission Flow

- Android 13+ requires runtime `POST_NOTIFICATIONS`.
- **Defaults at first launch:** all 4 notification preferences enabled in DataStore with default times (9:00 AM / 9:00 PM). No actual `WorkManager` jobs scheduled until permission granted.
- First time user opens Settings AND permission not yet asked → trigger system permission dialog via `ActivityResultContract`.
- On permission granted → `NotificationScheduler.reschedule(prefs)` enqueues all enabled jobs.
- If denied (or denied permanently): Settings shows persistent banner with "Open settings" CTA → `Settings.ACTION_APP_NOTIFICATION_SETTINGS`. Toggles disabled (visual + no-op).
- Permission state re-checked on `Settings` resume (user may have changed it via system).

### 6.6 Manifest Additions

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />  <!-- WorkManager already declares; explicit for clarity -->
```

No deep links beyond what Phase 3 already declared. Notification taps use existing app launch intent.

---

## 7. Data Flow

### 7.1 Home Streak Strip

```
HabitLogQueries.observeLogsBetween(userId, today-29, today)
  → ComputeStreakUseCase.observeRange(userId, last30Days)
  → HomeViewModel.streakRange: StateFlow<StreakRangeResult>
  → StreakStrip composable (recomposes on emit)
```

ViewModel additions:
```kotlin
val streakSummary: StateFlow<StreakSummary>
val streakStrip: StateFlow<StreakRangeResult>
fun manualRefresh()  // calls SyncEngine.sync(MANUAL_PULL)
```

### 7.2 Streak History Screen

```
StreakViewModel.loadMonth(yearMonth: YearMonth)
  → HabitLogQueries.observeLogsBetween(userId, monthStart, monthEnd)
  → ComputeStreakUseCase.observeRange(userId, monthRange)
  → MonthData added to monthsLoaded: StateFlow<List<MonthData>>
```

ViewModel:
```kotlin
class StreakHistoryViewModel(useCase: ComputeStreakUseCase, userIdProvider: () -> String) {
    val summary: StateFlow<StreakSummary>
    val monthsLoaded: StateFlow<List<MonthData>>
    val firstLogDate: StateFlow<LocalDate?>
    fun loadOlderMonth()
}

data class MonthData(
    val month: YearMonth,
    val days: List<StreakDay>,
    val isLoading: Boolean,
)
```

### 7.3 Settings Screen

```
NotificationPreferences (DataStore Flow<Prefs>)
  → SettingsViewModel.prefs: StateFlow<NotificationPrefs>
  → SettingsScreen (toggles + time pickers)

User toggles switch
  → SettingsViewModel.setDailyReminderEnabled(true)
  → NotificationPreferences.update { ... }
  → NotificationScheduler.reschedule(updatedPrefs)
  → WorkManager.enqueueUniquePeriodicWork(UPDATE)
```

### 7.4 Pull-to-Refresh

```
PullToRefreshBox.onRefresh
  → HomeViewModel.manualRefresh()
  → SyncEngine.sync(reason = MANUAL_PULL)
  → SyncState transitions: Idle → Running → Idle | Error
  → isRefreshing = sync.state is Running
  → indicator clears automatically when state leaves Running
```

---

## 8. Error Handling

- **Streak compute throws:** ViewModel catches, emits empty `StreakSummary` + empty days list, logs via `println` + Android `Log.e`. UI renders empty state, never crashes.
- **Future-dated log (clock skew):** filtered out at query level (`logged_at <= now() + 1h` tolerance). Logged warning.
- **Empty logs:** `firstLogDate = null`, all days `EMPTY`, current streak 0.
- **Timezone shift mid-streak (DST or travel):** compute always uses current device TZ. A user crossing time zones may see one-day streak adjustment — acceptable for v1.
- **`loadOlderMonth` query fails:** that month item shows inline error + retry button. Other months unaffected.
- **Pull-to-refresh while offline:** existing `categorize()` returns "No network"; refresh indicator clears, snackbar shows. No additional handling.
- **Notification scheduling fails:** Settings shows toast "Couldn't update reminder", reverts toggle UI to previous state. DataStore not written.
- **Worker fires but DB unavailable:** worker logs error, returns `Result.retry()`. WorkManager retries with backoff.
- **Permission denied mid-session:** Settings re-checks permission on resume, updates banner state.
- **StreakResetWorker missed fire (device off at 00:30):** WorkManager catches up on next eligible time. Notification may be late by hours; acceptable.
- **Sign out clears local DB:** streak resets to 0 (no logs left). Acceptable, matches existing Phase 3 behavior.

---

## 9. Testing

### 9.1 Shared (commonTest)

`ComputeStreakUseCaseTest`:
- empty logs → all `EMPTY`, currentStreak 0, firstLogDate null
- single log today → currentStreak 1, day = `COMPLETE`, longest 1
- 5 consecutive days incl. today → currentStreak 5
- gap of 1 day mid-streak → that day = `FROZEN`, streak continues
- gap of 2 consecutive days → 1st = `FROZEN`, 2nd = `BROKEN`, streak resets
- broken then resumed → new streak counted from resume day
- today no log + yesterday complete → today = `TODAY_PENDING`, currentStreak unchanged from yesterday
- today no log + yesterday frozen → today = `TODAY_PENDING` (don't pre-emptively break)
- log far in future → ignored
- multiple logs same day → counted once
- cross-month / cross-year boundary → correct
- DST forward / DST back → correct (test in fixed TZ with synthetic instants)
- longestStreak ≥ currentStreak invariant
- deleted habit's old logs still count (historic completeness preserved)

### 9.2 Android (androidTest / unit)

- `HomeViewModelTest` — streakStrip emits on log insert; manualRefresh triggers SyncEngine
- `StreakHistoryViewModelTest` — initial load = current month; loadOlderMonth prepends; stops at firstLogDate
- `SettingsViewModelTest` — toggle persists to DataStore; reschedule called on change
- `NotificationPreferencesTest` — DataStore round-trip
- `NotificationSchedulerTest` — toggle on enqueues unique work; toggle off cancels; time change replaces (verify via WorkManager test harness)
- `DailyReminderWorkerTest` — fires when no log today; skips when logged today
- `StreakRiskWorkerTest` — fires when streak ≥ 1 + no log today; skips when streak == 0
- `DayBoundaryWorkerTest` — fires frozen/reset notifs based on yesterday's state

### 9.3 Manual Smoke (verification checklist in plan)

- 30-day strip renders correct colors (manually create logs across 30 days mixing complete/missed)
- Strip empty state shows on fresh install before first log
- Streak number on Home matches grid + Streak History summary
- Pull-to-refresh on Home triggers sync (chip → Running → Idle)
- Pull-to-refresh while offline shows error chip + snackbar, indicator clears
- Streak History scrolls back, lazy-loads months, stops at first log
- (Day cell tap interaction deferred — see §12)
- Settings → toggle daily reminder off → cancel WorkManager (verify via `adb shell dumpsys jobscheduler` or `adb shell cmd jobscheduler get-tracked`)
- Settings → set reminder time to 1 minute from now → notif fires at that time
- Notif tap opens app to Home
- Sign out moved to Settings; Home overflow menu gone (replaced by Settings icon)
- Permission banner shows when POST_NOTIFICATIONS denied; "Open settings" deep link works
- Day boundary: simulate by changing device date forward → DayBoundaryWorker fires correct notif

---

## 10. Design System (Phase 4 surfaces)

Aligns with master spec §9 (Material 3, dark + light, no gradients, 2/4/8 spacing, WCAG AA).

### 10.1 Streak Strip (Home)

| Element | Spec |
|---------|------|
| Outer card | `surfaceVariant` background, 12.dp radius, 16.dp padding |
| Header label | `titleSmall`, weight 600, with `Icons.Default.Whatshot` icon (16.dp) |
| Streak number | `headlineSmall`, weight 700, `primary` color |
| "View all" button | `TextButton`, label `labelLarge`, `primary` color |
| Day cell | 16.dp × 16.dp, 4.dp radius, 4.dp gap |
| Today outline | 1.5.dp `StreakTodayOutline` ring |
| Cell tap target | Expand via `Modifier.clickable` with 8.dp `padding` (effective 32.dp tap area) |

### 10.2 Streak History Screen

| Element | Spec |
|---------|------|
| Top app bar | `CenterAlignedTopAppBar`, back arrow, title "Streak History" |
| Summary card | `surface` bg, 16.dp radius, 16.dp padding, 1.dp `outline` border |
| Stat number | `headlineMedium`, weight 700 |
| Stat label | `labelMedium`, `onSurfaceVariant` |
| Month header | `titleMedium`, weight 600, 16.dp top + 8.dp bottom spacing |
| Day cell | 32.dp × 32.dp, 6.dp radius, 4.dp gap |
| Day number | `labelSmall`, weight 500, on-color text (white on dark cells, `onSurface` on grey) |
| Empty/before-anchor cell | Transparent placeholder (preserves 7-col grid) |

### 10.3 Settings Screen

| Element | Spec |
|---------|------|
| Top app bar | `CenterAlignedTopAppBar`, back arrow, title "Settings" |
| Section header | `titleMedium`, weight 600, 16.dp horizontal + 24.dp top + 8.dp bottom |
| Settings row | min 56.dp height, 16.dp horizontal padding, vertical center alignment |
| Row title | `bodyLarge` |
| Row supporting text | `bodyMedium`, `onSurfaceVariant` |
| Switch | Material 3 `Switch` |
| Time picker trigger | `TextButton` showing formatted time (e.g., "9:00 AM"), `primary` color |
| Section divider | 1.dp `HorizontalDivider`, `outline` color, full-width |
| Sign out row | Title in `error` color, separated by 16.dp gap |
| Permission banner | `errorContainer` bg, 12.dp radius, 12.dp padding, icon + text + button |

### 10.4 Notifications

| Element | Spec |
|---------|------|
| Notif title | App name "Habitto" |
| Notif icon | App launcher monochrome icon (already exists) |
| Notif color (Android < 12) | `primary` token (`#2E7D32` light / `#81C784` dark — system picks based on theme) |
| Action | Tap opens Home (existing `MainActivity` launch intent) |

### 10.5 Pull-to-Refresh

Standard Material 3 `PullToRefreshBox` indicator. No customization.

---

## 11. Dependencies

```toml
# gradle/libs.versions.toml additions
[versions]
datastore = "1.1.1"
material3-pulltorefresh = "1.3.1"  # bundled with Compose BOM if using one — verify

[libraries]
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

`androidx.compose.material3:material3` already pulled in; `PullToRefreshBox` ships with Material 3 1.3+. Verify version during plan.

WorkManager already added in Phase 3 — no change.

---

## 12. Migration / Compatibility

- **No DB migration.** Streaks computed from existing `HabitLog` rows.
- **No remote schema change.** No Supabase migration.
- **No breaking changes** to existing screens — Home gains one new section + Settings icon, overflow menu items move (sign out + sign in to Settings).
- **First launch after upgrade:** notification prefs default to enabled in DataStore with 9 AM / 9 PM times. Actual scheduling waits for `POST_NOTIFICATIONS` grant. User can disable individually in Settings. No backfill needed.

---

## 13. Open Decisions (resolved)

- ✅ **On-device streak compute, no persistence.** Performance non-issue at realistic scale.
- ✅ **Streak anchored at first log date**, not signup. Auth optional per master spec.
- ✅ **Sign out moves to Settings**, removes Home overflow menu, replaced by Settings icon.
- ✅ **Pull-to-refresh added to Phase 4** (was floating).
- ✅ **Freeze earn/spend deferred to Phase 5.** Phase 4 only renders FROZEN state.
- ✅ **Crashlytics deferred to last phase pre-launch.**
- ✅ **3 workers, not 4.** Frozen + Reset coordinated via single DayBoundaryWorker.
- ✅ **Notification colors:** use `primary` token; system handles dark/light.
- ✅ **Day-of-week start:** locale-driven (`Locale.getDefault().firstDayOfWeek`).
- ✅ **Streak History day cell tap:** read-only Phase 4 (no log-from-history). Future phase may add "log retroactively" affordance — out of scope here.
- ✅ **Privacy policy URL:** placeholder `https://example.com/privacy` until real URL exists. Tracked as a backlog item; not blocking Phase 4.
- ✅ **`daily_earn_cap` definition:** sum of all active habits' `dailyTarget`. Derived per user, no new field. Cap = `dailyEarnCap × 2`.
- ✅ **Week start TZ:** switch from UTC to local TZ (`TimeZone.currentSystemDefault()`). Existing tests in `GetPointBalanceUseCaseTest` need updating.
- ✅ **Rollover UI feedback:** no Phase 4 indicator. May add later if users complain about silent point loss.

---

## 14. Phase 4 Definition of Done

- [ ] Home renders 30-day streak strip with correct colors + current streak number
- [ ] Streak History screen accessible from Home, scrolls to first log date, lazy-loads months
- [ ] Settings screen accessible from Home, replaces overflow menu
- [ ] Sign in / sign out flows operate from Settings
- [ ] All 4 notification types configurable + fire correctly per their conditions
- [ ] Notification time pickers persist in DataStore + survive app restart
- [ ] Pull-to-refresh on Home triggers sync; indicator clears on completion
- [ ] Point rollover cap enforced (verified via two-day high-earn / no-spend test)
- [ ] Week reset on Monday 00:00 local TZ wipes carryover
- [ ] All shared `commonTest` streak + rollover tests pass
- [ ] Android unit tests pass for ViewModels + Workers
- [ ] Manual smoke checklist passes on emulator
- [ ] WCAG AA verified for new surfaces in light + dark mode
- [ ] PR opened against `main` with manual verification screenshots
