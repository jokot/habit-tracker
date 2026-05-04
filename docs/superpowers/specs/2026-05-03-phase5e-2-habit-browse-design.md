# Phase 5e-2 — Habit Browse (Read-Only)

> **Branch:** `feature/phase5e-2-habit-browse`. **Worktree:** `.worktrees/phase5e-2-habit-browse`.

## Goal

Ship read-only Habit list + Habit detail screens. Builds new `ComputePerHabitStreakUseCase` that mirrors the user-level streak engine semantics scoped to one habit. No mutation UI — that's 5e-3.

## Non-goals

- Habit form (add / edit) — 5e-3
- Habit delete — 5e-3 (the SQL `markHabitDeleted` query already lives, no UI yet)
- Drag-reorder habits in the list — undesigned, separate phase
- Habit list grouped by identity — flat list ships in 5e-2, grouping deferred
- Tap-from-Today habit cards → HabitDetail — would conflict with existing tap-to-log; defer to a polish phase
- App bar `edit` + `more_vert` icons on HabitDetail — out of scope (mutation entry points belong in 5e-3)

## Architecture

New domain use case `ComputePerHabitStreakUseCase` returns `PerHabitStreakResult(totalLogs, pointsEarned, currentStreak, longestStreak, firstLogDate, last30Days)` for one habit, reusing the `effectiveFrom`/`effectiveTo` filter from 5e-1. Per-habit streak rule (Q2 decision A): a day is COMPLETE for habit X iff at least one non-deleted log exists for X on that day AND X was active that day. FROZEN/BROKEN/TODAY_PENDING follow the same single-grace-day semantics as the user-level engine.

UI layer adds 4 new files (HabitListViewModel + HabitListScreen + HabitDetailViewModel + HabitDetailScreen) and 2 new nav routes (`Screen.HabitList`, `Screen.HabitDetail(habitId)`). YouHub gains a single "Habits" row that opens HabitList. HabitList row tap → HabitDetail. No FAB. No drag handles. No app bar mutation icons.

## Schema / data model

No schema changes. 5e-1 already added `effective_from` / `effective_to` to `habits` and `habit_identities`. This phase consumes them, doesn't extend them.

## Components

### `ComputePerHabitStreakUseCase`

**Files (create):**
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/PerHabitStreakResult.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputePerHabitStreakUseCase.kt`

**Result model:**

```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.LocalDate

data class PerHabitStreakResult(
    val habitId: String,
    val totalLogs: Int,
    val pointsEarned: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val firstLogDate: LocalDate?,
    val last30Days: List<PerHabitDayState>,
) {
    companion object {
        fun emptyFor(habitId: String, today: LocalDate): PerHabitStreakResult =
            PerHabitStreakResult(
                habitId = habitId,
                totalLogs = 0,
                pointsEarned = 0,
                currentStreak = 0,
                longestStreak = 0,
                firstLogDate = null,
                last30Days = (0 until 30).map { offset ->
                    PerHabitDayState(today.minus(29 - offset, kotlinx.datetime.DateTimeUnit.DAY), com.habittracker.domain.model.StreakDayState.EMPTY)
                },
            )
    }
}

data class PerHabitDayState(
    val date: LocalDate,
    val state: com.habittracker.domain.model.StreakDayState,
)
```

`StreakDayState` already exists (from 5b: COMPLETE / FROZEN / BROKEN / TODAY_PENDING / EMPTY / FUTURE).

**Use case:**

```kotlin
package com.habittracker.domain.usecase

class ComputePerHabitStreakUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val habitRepo: HabitRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun observe(userId: String, habitId: String): Flow<PerHabitStreakResult> =
        habitLogRepo.observeAllActiveLogsForUser(userId).map { allLogs ->
            val habit = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
                ?: return@map PerHabitStreakResult.emptyFor(habitId, todayLocal())
            compute(habit, allLogs.filter { it.habitId == habitId })
        }

    suspend fun computeNow(userId: String, habitId: String): PerHabitStreakResult {
        val habit = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
            ?: return PerHabitStreakResult.emptyFor(habitId, todayLocal())
        val logs = habitLogRepo.observeAllActiveLogsForUser(userId).first()
            .filter { it.habitId == habitId }
        return compute(habit, logs)
    }

    private fun compute(habit: Habit, logs: List<HabitLog>): PerHabitStreakResult {
        val today = todayLocal()
        val totalLogs = logs.size
        val pointsEarned = logs.sumOf {
            PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint)
        }
        val firstLogDate = logs.minByOrNull { it.loggedAt }?.loggedAt?.toLocalDate()
        if (firstLogDate == null) {
            return PerHabitStreakResult(
                habitId = habit.id,
                totalLogs = 0,
                pointsEarned = 0,
                currentStreak = 0,
                longestStreak = 0,
                firstLogDate = null,
                last30Days = thirtyDayWindow(today, emptyMap()),
            )
        }

        // Build per-day map of "habit had ≥1 log on this day"
        val loggedDays: Set<LocalDate> = logs.map { it.loggedAt.toLocalDate() }.toSet()

        // Walk from firstLogDate (or earlier if needed) through today building per-day state.
        // Day = COMPLETE iff (date in loggedDays AND habit active on that day).
        // Else FROZEN (after COMPLETE), BROKEN (after FROZEN/BROKEN), TODAY_PENDING (today special).
        val perDay = mutableMapOf<LocalDate, StreakDayState>()
        var prev: StreakDayState? = null
        var cursor = firstLogDate
        var run = 0
        var longest = 0
        while (cursor <= today) {
            val dayStart = cursor.atStartOfDayIn(timeZone)
            val active = habitActiveOn(habit, dayStart)
            val state = when {
                cursor > today -> StreakDayState.FUTURE
                cursor == today && cursor in loggedDays && active -> StreakDayState.COMPLETE
                cursor == today && active -> StreakDayState.TODAY_PENDING
                cursor in loggedDays && active -> StreakDayState.COMPLETE
                !active -> StreakDayState.EMPTY
                prev == StreakDayState.COMPLETE -> StreakDayState.FROZEN
                prev == StreakDayState.FROZEN -> StreakDayState.BROKEN
                prev == StreakDayState.BROKEN -> StreakDayState.BROKEN
                prev == StreakDayState.TODAY_PENDING -> StreakDayState.FROZEN
                else -> StreakDayState.EMPTY
            }
            perDay[cursor] = state
            when (state) {
                StreakDayState.COMPLETE -> {
                    run += 1
                    if (run > longest) longest = run
                }
                StreakDayState.FROZEN -> Unit  // streak alive, no increment
                StreakDayState.BROKEN -> { run = 0 }
                StreakDayState.TODAY_PENDING, StreakDayState.EMPTY, StreakDayState.FUTURE -> Unit
            }
            prev = state
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }

        return PerHabitStreakResult(
            habitId = habit.id,
            totalLogs = totalLogs,
            pointsEarned = pointsEarned,
            currentStreak = run,
            longestStreak = longest,
            firstLogDate = firstLogDate,
            last30Days = thirtyDayWindow(today, perDay),
        )
    }

    private fun thirtyDayWindow(today: LocalDate, perDay: Map<LocalDate, StreakDayState>): List<PerHabitDayState> {
        val start = today.minus(29, DateTimeUnit.DAY)
        return (0 until 30).map { offset ->
            val d = start.plus(offset, DateTimeUnit.DAY)
            PerHabitDayState(d, perDay[d] ?: StreakDayState.EMPTY)
        }
    }

    private fun habitActiveOn(habit: Habit, dayStart: Instant): Boolean =
        (habit.effectiveFrom?.let { it <= dayStart } ?: true) &&
        (habit.effectiveTo?.let { it > dayStart } ?: true)

    private fun Instant.toLocalDate(): LocalDate = toLocalDateTime(timeZone).date

    private fun todayLocal(): LocalDate = clock.now().toLocalDateTime(timeZone).date
}
```

(`habitActiveOn` mirrors the helper added to `ComputeStreakUseCase` and `ComputeIdentityStatsUseCase` in 5e-1. Three copies now — extracting to a shared util is a future refactor; not blocking this phase.)

### `HabitListViewModel` + `HabitListScreen`

**Files (create):**
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListViewModel.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitListScreen.kt`

**State:**
```kotlin
sealed interface HabitListState {
    data object Loading : HabitListState
    data class Loaded(val habits: List<HabitRowItem>) : HabitListState
}

data class HabitRowItem(
    val habit: Habit,
    val identityNames: List<String>,
)
```

**ViewModel:**
- Secondary constructor `(container: AppContainer)` delegates to primary that takes `habitRepo`, `identityRepo`, `userIdProvider`.
- Observes `habitRepo.observeHabitsForUser(userId)` (filters out `effectiveTo != null` — those are deleted; in 5e-2 there are none yet because no delete UI exists).
- Joins with identity-link data: for each habit, fetch its linked identities via `identityRepo` and resolve to identity names from the seed table.
- Sort order: alphabetical by `habit.name` for predictable browse experience.
- Emits `Loaded(habits)`.

**Screen** — per canvas screens.jsx:942 (HabitCRUD) but stripped of FAB + drag:
- TopAppBar: `arrow_back` + title "Habits"
- LazyColumn of rows (no Card wrapping each — flat with horizontal divider between, matching canvas pattern)
- Each row:
  - `Surface(onClick = { onHabitClick(habit.id) }, ...)` for ripple-clip
  - Row content: `HabitGlyph(habitIcon(habit.name), hueFromFirstIdentity, 40dp)` + Column { name (titleSmall) + subtitle (bodySmall, onSurfaceVariant): "${identityNames.joinToString(" · ")} · target ${habit.dailyTarget}" } + chevron right
- Empty state when `habits.isEmpty()`: text "No habits yet. Add some via Identities." with a TextButton "Manage identities" → `IdentityList` (ship as part of empty-state polish)
- No FAB. No drag handles.

`hueFromFirstIdentity`: derive hue using `IdentityHue.forIdentityId(...)` against the first identityId in the habit's link set. If the habit has no identities (post-removal orphan), use a neutral grey hue.

### `HabitDetailViewModel` + `HabitDetailScreen`

**Files (create):**
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailViewModel.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/habit/HabitDetailScreen.kt`

**State:**
```kotlin
sealed interface HabitDetailState {
    data object Loading : HabitDetailState
    data object NotFound : HabitDetailState
    data class Loaded(
        val habit: Habit,
        val identityNames: List<String>,
        val streak: PerHabitStreakResult,
    ) : HabitDetailState
}
```

**ViewModel:**
- Secondary constructor `(container: AppContainer, habitId: String)`.
- Observe combine of:
  - `habitRepo.observeHabitsForUser(userId)` filtered to `it.id == habitId` → habit row OR null (NotFound)
  - `identityRepo.observeUserIdentities(userId)` + habit-identity links to derive `identityNames`
  - `computePerHabitStreakUseCase.observe(userId, habitId)` → PerHabitStreakResult
- Emit Loaded combining all three. Re-emits reactively when any source changes (e.g. user logs the habit elsewhere → streak updates).

**Screen** — per canvas screens.jsx:876 (HabitDetail):

**Top bar:**
- `arrow_back` icon only (back button)
- NO edit. NO more_vert. (5e-3 will add these.)

**Hero:**
- `HabitGlyph(habitIcon(habit.name), hueFromFirstIdentity, 56dp)`
- `Text(habit.name, fontSize = 40.sp, fontWeight = SemiBold, lineHeight = 1.05)` — reuse existing display-numeral style if available
- Subtitle (bodyMedium, onSurfaceVariant):
  `"${identityNames.joinToString(", ")} · ${habit.thresholdPerPoint.format()} ${habit.unit} per pt · target ${habit.dailyTarget}"`

**Stats grid** (2 columns × 2 rows, 8dp gap):

Reuse the canvas `Stat` composable shape — for each stat:
- Card: `surface` bg + `outlineVariant` 1dp border + 14dp rounded + 14dp/16dp padding
- Label (labelSmall, onSurfaceVariant): the stat name
- Value row: number (titleLarge or numeral style, 32sp, optional tint) + optional suffix (bodySmall, onSurfaceVariant)

Four stats:
1. "Per-habit streak" — value = `streak.currentStreak`, suffix "days", tint = `FlameOrange` token
2. "Total logs" — value = `streak.totalLogs` (no suffix, no tint)
3. "Longest streak" — value = `streak.longestStreak`, suffix "days"
4. "Points earned" — value = `streak.pointsEarned`, tint = `MaterialTheme.colorScheme.primary`

**Last 30 days** mini heatmap:
- Section title "Last 30 days" (titleMedium, fontWeight semibold), 8dp bottom margin
- Outer card: `surface` bg + `outlineVariant` 1dp border + 16dp rounded + 14dp padding
- Inner grid: 10 columns × 3 rows (= 30 cells), 4dp gap
- Each cell: `aspectRatio(1f)` square, 4dp rounded
- Cell color from `streak.last30Days[i].state` using existing token mapping:
  - COMPLETE → identity-hue-tinted fill (use first linked identity's hue) OR FlameOrange fallback if no identity
  - FROZEN → `StreakFrozenBg` bg + `FrozenOverlay` (canvas-spec from 5c-1)
  - BROKEN → `StreakBrokenBg` bg + `BrokenOverlay`
  - TODAY_PENDING → primary 2dp border, transparent fill
  - EMPTY → `surface-1` muted fill
  - FUTURE → faded grey
- Reuse existing `StateOverlays.kt` (`FrozenOverlay`, `BrokenOverlay`) and color tokens for visual consistency with global heatmaps.

## Nav routes + AppContainer wiring

### Nav routes (in `AppNavigation.kt`)

```kotlin
object HabitList : Screen("habit_list")
object HabitDetail : Screen("habit_detail/{habitId}") {
    const val ARG_ID = "habitId"
    fun route(id: String) = "habit_detail/$id"
}
```

### NavHost composables

```kotlin
composable(Screen.HabitList.route) {
    val vm = viewModel { HabitListViewModel(container) }
    HabitListScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        onHabitClick = { id -> navController.navigate(Screen.HabitDetail.route(id)) },
    )
}
composable(
    route = Screen.HabitDetail.route,
    arguments = listOf(navArgument(Screen.HabitDetail.ARG_ID) { type = NavType.StringType }),
) { entry ->
    val habitId = entry.arguments?.getString(Screen.HabitDetail.ARG_ID).orEmpty()
    val vm = viewModel { HabitDetailViewModel(container, habitId) }
    HabitDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
}
```

### YouHubScreen — add Habits row

Add a list row between the IdentityHubCard and the existing Settings/Account rows:

```kotlin
ListItem(
    leadingContent = { Icon(Icons.Outlined.Checklist, contentDescription = null) },
    headlineContent = { Text("Habits") },
    supportingContent = { Text("Manage what you track") },
    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
    modifier = Modifier.clickable { onHabitsClick() },
)
```

If `Icons.Outlined.Checklist` is unavailable, fall back to `Icons.Outlined.TaskAlt` or `Icons.Outlined.CheckBoxOutlineBlank`.

`onHabitsClick: () -> Unit` parameter added to YouHubScreen Composable. Wired in `AppNavigation`:

```kotlin
YouHubScreen(
    viewModel = vm,
    onOpenIdentities = { navController.navigate(Screen.IdentityList.route) },
    onHabitsClick = { navController.navigate(Screen.HabitList.route) },  // new
)
```

### `AppContainer` wiring

```kotlin
val computePerHabitStreakUseCase = ComputePerHabitStreakUseCase(habitLogRepository, habitRepository)
```

(Two existing fields `habitLogRepository` and `habitRepository` already present.)

## Data flow

### HabitList load
```
HabitListViewModel.init
  → observeHabitsForUser(userId)
  → combine identity-link + identity-name resolution
  → state.value = Loaded(rows)
  → LazyColumn renders
```

### HabitDetail load
```
HabitDetailViewModel.init
  → combine(
      habit row (from observeHabitsForUser → first { it.id == habitId }),
      identityNames (from observeUserIdentities + linkTable),
      streak result (from computePerHabitStreakUseCase.observe)
    )
  → state.value = Loaded(habit, identityNames, streak)
  → reactive updates when user logs the habit elsewhere
```

### User logs habit elsewhere → HabitDetail reflects
```
LogHabitUseCase inserts a new HabitLog row
  → habitLogRepo's SQLDelight Flow re-emits
  → ComputePerHabitStreakUseCase.observe re-emits new PerHabitStreakResult
  → HabitDetailViewModel.combine re-emits Loaded
  → screen re-renders with new totalLogs, currentStreak, last30Days
```

## Error handling

- HabitDetail with unknown habitId → `NotFound` state; screen shows "Habit not found" message + back button.
- HabitList empty → empty state with link to IdentityList.
- No new error paths beyond the existing repository observation patterns.

## Testing

### Unit tests (commonTest)

`ComputePerHabitStreakUseCaseTest` — new file:
- `totalLogs counts non-deleted logs for habit`
- `pointsEarned sums across logs respecting threshold`
- `firstLogDate equals earliest log date`
- `currentStreak counts consecutive complete days ending today`
- `currentStreak unaffected when today not yet logged (TODAY_PENDING preserves yesterday's streak)`
- `longestStreak returns max consecutive complete run across history`
- `last30Days has exactly 30 entries ending on today`
- `past day before habit existed renders as EMPTY not BROKEN` (effectiveFrom honored)
- `day after habit deleted renders as EMPTY not BROKEN` (effectiveTo honored)
- `partial-quantity log still counts as complete day` (≥1 log = complete per Q2 decision)
- `unknown habit returns emptyFor result`

### ViewModel tests (androidApp test)

`HabitListViewModelTest`:
- Loading → Loaded with habits sorted alphabetically
- `effectiveTo != null` habits filtered out
- `identityNames` per row resolved correctly via habit-identities join
- Empty user → `Loaded(emptyList())`

`HabitDetailViewModelTest`:
- habitId not found → `NotFound`
- Loaded combines habit + identityNames + streakResult
- Re-emits when log is added to that habit

### Manual smoke (pre-merge)

- [ ] YouHub → "Habits" row present → tap → HabitList opens
- [ ] HabitList shows all user habits, alphabetical, with identity-name subtitles
- [ ] Tap a habit → HabitDetail opens with hero, 4 stat tiles, 30-day mini heatmap
- [ ] Per-habit streak value = consecutive days with ≥1 log for that habit only (not user-level streak)
- [ ] Total logs / Points earned numbers match expected counts
- [ ] Log a habit from Today → revisit HabitDetail → totalLogs +1, currentStreak updated, today cell turns COMPLETE
- [ ] Multi-identity habit: subtitle shows all linked identity names comma-separated
- [ ] Habit linked to a since-removed identity: still appears in HabitList (orphan, per 5c-2 C1)
- [ ] HabitDetail with unknown habitId (manually navigate to bogus deeplink) → "not found" state, no crash
- [ ] Empty user (fresh install, skipped onboarding) → HabitList shows empty state
- [ ] Light + dark mode: hero, stat tiles, heatmap cells render correctly in both
- [ ] No regression on existing flows (habits still loggable from Today, IdentityList still works)

## Migration / rollout

No schema changes. No data migration. Single APK deploy.

## Open questions

None. Design locked.
