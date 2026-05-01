# Phase 5c-1 — Identity Management Screens (read-only)

**Goal:** ship the read-only multi-identity management surfaces. IdentityList + IdentityDetail screens with real per-identity stats. Upgrade YouHub identity card from 5b's stub to canvas-style rich card. Wire taps from Home identity strip and YouHub card. No write actions in this phase.

**Worktree:** `.worktrees/phase5c-identity-management`
**Branch:** `feature/phase5c-identity-management`
**Design source:** `/tmp/habitto-design/habitto/project/screens.jsx`
- `YouHub` lines 1553-1638 (identity card)
- `IdentityList` lines 1833-1968
- `IdentityDetail` lines 1971-2155

---

## Why

Phase 5b shipped the multi-identity data model + read-only Home strip + basic YouHub card stub. The strip chips and YouHub card are inert — they have no detail screen to navigate to. 5c-1 makes them functional.

Splits the larger 5c (full identity management) into two phases per the brainstorm scope decision:
- **5c-1 (this spec):** read-only management. IdentityList + IdentityDetail + YouHub card upgrade + tap wiring.
- **5c-2 (later):** AddIdentityFlow + custom identity creation + pin + remove (soft delete).

## Scope

**In:**
- `IdentityList` screen (read-only — list of active identities with per-identity stats)
- `IdentityDetail` screen (read-only — single identity hero, 90-day heat grid, linked habits read-only)
- YouHub identity card upgrade (canvas-style rich card)
- Tap wiring: Home strip chip → IdentityDetail; Home strip "+N more" → IdentityList; YouHub card → IdentityList; IdentityList row → IdentityDetail
- Per-identity stats (streak, daysActive, habitCount, last14Heat, last90Heat) computed from existing logs
- Refresh-on-resume on both new screens (matches 5b-fix pattern for StreakHistory)

**Out (5c-2 / later phases):**
- AddIdentityFlow + Add CTA on IdentityList
- Custom identity creation (name/description/icon input)
- Pin identity (pinned border + "X pinned" copy + Pin/Unpin button)
- Remove identity (soft delete + Past identities collapsed section + helper copy)
- "Why this identity" reflection card + Edit (text input + persist)
- IdentityList `more_vert` menu, IdentityDetail `more_vert` menu
- Linked habit row tap navigation (gated on Habit detail screen, future Habit CRUD phase)
- Add habit dashed CTA inside IdentityDetail (gated on Habit CRUD)
- Sign-in conflict 3-way dialog (deferred to last phase)
- Per-identity notifications, per-identity exchange-rate (gated on those features)
- Drag-reorder identities (undesigned)

## Architecture

### Data layer

No SQLDelight schema changes. 5b's `LocalUserIdentity` and `LocalHabitIdentity` cover read needs. No new sync surface.

### Domain model

```kotlin
data class IdentityStats(
    val identityId: String,
    val currentStreak: Int,         // consecutive days where ALL habits for this identity logged
    val daysActive: Int,            // count of distinct days where ≥1 of identity's habits logged
    val habitCount: Int,            // linked habits at compute time
    val last14Heat: List<Int>,      // 14 ints (oldest→newest), each 0..4
    val last90Heat: List<Int>,      // 90 ints (oldest→newest), each 0..4
)

data class IdentityWithStats(
    val identity: Identity,
    val stats: IdentityStats,
)
```

### Use cases

**`ComputeIdentityStatsUseCase`:**
```kotlin
class ComputeIdentityStatsUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val identityRepo: IdentityRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun observe(userId: String, identityId: String): Flow<IdentityStats>
    suspend fun computeNow(userId: String, identityId: String): IdentityStats
}
```

Reuses `IdentityRepository.observeHabitsForIdentity(userId, identityId)` + `HabitLogRepository.observeAllActiveLogsForUser` (for `observe`) / `getAllActiveLogsForUser` (for `computeNow`).

**`ObserveUserIdentitiesWithStatsUseCase`:**
```kotlin
class ObserveUserIdentitiesWithStatsUseCase(
    private val identityRepo: IdentityRepository,
    private val statsUseCase: ComputeIdentityStatsUseCase,
) {
    fun execute(userId: String): Flow<List<IdentityWithStats>>
}
```

For IdentityList. Combines `IdentityRepository.observeUserIdentities` with parallel `computeNow` for each identity. (`computeNow` rather than per-identity flow to avoid N flows in the combine.)

### Heat formula

Per-day heat for an identity:
```
ratio = sum_per_habit(min(pointsEarnedOnDay, habit.dailyTarget)) / sum(habit.dailyTarget)
```
where `habit` ∈ identity's linked habits, `pointsEarnedOnDay` from `PointCalculator.pointsEarned(quantity, thresholdPerPoint)` summed across that day's logs for the habit.

Buckets (matches existing `ComputeStreakUseCase.heatBucket`):
- 0% → level 0
- ≤ 25% → level 1
- ≤ 50% → level 2
- ≤ 75% → level 3
- > 75% → level 4

`last14Heat` / `last90Heat`: list of N day-bucket ints, oldest→newest, ending today. Length exactly 14 or 90.

**Stale-heat note:** denominator (`sum(dailyTarget)`) uses CURRENT habit set. If a future Habit CRUD phase introduces edit/delete, old days' heat would shift. That's not a concern in 5c-1 because Habit CRUD isn't shipped. When it ships, plan a follow-up: per-log `dailyTargetSnapshot` column OR habit-history rows OR day-cached heat. Decision deferred.

### Streak rule (per-identity)

Same strict rule as 5b-fix global streak: a day counts as COMPLETE for the identity when EVERY habit linked to that identity has at least one log on that day. Run = consecutive COMPLETE days ending today (or yesterday if today is partial / pending). Same FROZEN/BROKEN gap semantics as `ComputeStreakUseCase`.

### UI surfaces

#### IdentityList screen

- TopAppBar — back arrow, title "Identities", `Color.Transparent` containerColor (matches StreakHistory pattern), no `more_vert`.
- Body LazyColumn with `contentPadding = PaddingValues(bottom = 24.dp)`.
- Aggregate header: `"You're committing to N identities."` (bodySmall onSurfaceVariant, 16dp horizontal pad, 16dp bottom pad).
- For each `IdentityWithStats` (ordered by `addedAt`): rich card.
  - Hue gradient bg `Color.hsl(hue, 30%, 92%)` → `surface` 75% in light. Dark mode: `Color.hsl(hue, 30%, 18%)` → surface.
  - 1dp `outlineVariant` border, 20dp radius, 16dp internal padding, 10dp gap between cards.
  - Row: `IdentityAvatar(48dp)` + Column(weight 1f) + chevron right (20dp onSurfaceVariant).
  - Column inside, top→bottom:
    - Title row: identity name (titleMedium 700)
    - Description (bodySmall onSurfaceVariant)
    - Stats row, 12dp top pad: `🔥 ${streak} day streak | ${habitCount} habits | ${daysActive} days as`
      - Flame icon `Icons.Filled.LocalFireDepartment` 14dp `FlameOrange`
      - Numerals tabular (12sp 600), labels 12sp 400 onSurfaceVariant
      - 1dp × 12dp `outlineVariant` divider between groups
    - 14d sparkline, 12dp top pad: 14 thin bars, gap 3dp, 24dp tall, `Heat0..Heat4` color tokens.
  - Tap → `navigate(IdentityDetail.route(identity.id))`.
- Drop: `more_vert` menu, Add CTA, Past identities, pinned border highlight.

#### IdentityDetail screen

- TopAppBar — back arrow only, no title text, no `more_vert`, transparent container.
- Body LazyColumn:
  1. **Hero card** — same gradient style as List. 24dp padding, 24dp radius.
     - `IdentityAvatar(64dp)` at top
     - Display name 32sp 700, letterSpacing -0.5sp, 16dp top pad
     - Description (bodyMedium onSurfaceVariant), 6dp top pad
     - 1dp top divider, 16dp pad above + below
     - 3-stat row: Streak (`NumeralStyle.copy(fontSize = 26.sp)`, `FlameOrange` color), Total days (onSurface), Habits (onSurface). Each with uppercase label below (11sp 500, letterSpacing 0.4sp, onSurfaceVariant). Gap 20dp between stats.
  2. **Activity · 90 days** section
     - Header row: title `"Activity · 90 days"` (titleMedium 600) + sublabel `"logged a {name.lowercase()} habit"` (bodySmall onSurfaceVariant)
     - 90-cell heat grid in a card (1dp outlineVariant, 16dp radius, 14dp padding): 15-column × 6-row grid, square cells (aspectRatio 1f), 3dp gap, `Heat0..Heat4` color tokens.
  3. **Habits** section
     - Header `"Habits"` titleMedium 600 + sub `"What I do because I'm a {name.lowercase()}."` bodySmall onSurfaceVariant
     - Linked habits list (read-only): per-habit Surface card 14dp radius, 1dp outlineVariant, 12dp horizontal pad / 14dp vertical pad. Row:
       - `HabitGlyph(36dp, hue=identity.hue)`
       - Column(weight 1f): habit name titleSmall + (if other identities also link this habit) overlap chips row `"Also: {Identity1} · {Identity2}"` else `"Only this identity"` bodySmall onSurfaceVariant
       - Overlap chip: 14dp `IdentityAvatar` + first-word identity name, 10sp 500, hue-tinted text + bg
       - Trailing chevron right (inert in 5c-1)
- If user has 0 habits linked to this identity: render `"No habits linked yet."` (bodyMedium onSurfaceVariant, 14dp pad) instead of empty list. Stats render as zeros.
- Drop: `more_vert` menu, Why-this-identity card + Edit, Pin/Unpin button, Remove button + helper, Add habit dashed CTA.

#### YouHub identity card upgrade

Replace 5b stub. New card definition:
- Hue-tinted gradient bg using hue of FIRST user identity by `addedAt` (`Color.hsl(hue1, 30%, 92%)` → `surface` 75% in light; dark variant 18% lightness).
- 18dp padding, 20dp radius, 1dp outlineVariant border, 16dp horizontal margin (matches existing You list horizontal rhythm).
- Top row: `"IDENTITIES · ${count}"` label (11sp 500 uppercase, 0.4 letter-spacing, onSurfaceVariant) + chevron right (20dp).
- Spacer 12dp.
- Stacked avatars row: render up to 4 identities (more = "+N" surface), 40dp `IdentityAvatar` w/ ring (1dp outline), overlap −10dp left margin per slot, z-index decreasing left→right.
- Spacer 12dp.
- Hero copy line: `"I am a "` + first-identity name (lowercase, hue-tinted text color = `Color.hsl(identity.hue, 50%, 32%)`) + (`, a ` between middles) + (`& a ` before last) + last-identity name (hue-tinted) + `"."`. Style: titleMedium 700, lineHeight 1.3.
- Spacer 10dp.
- Footer: `"Tap to manage"` (12sp 500, onSurfaceVariant). NO "X pinned" indicator (5c-2).
- Whole card click → `navigate(IdentityList.route)`.
- If `userIdentities.isEmpty()` → hide entire card (defensive; same as 5b).

### Nav wiring

Extend `Screen` sealed class:
```kotlin
data object IdentityList : Screen("identity_list")
data object IdentityDetail : Screen("identity_detail/{identityId}") {
    const val ARG_ID = "identityId"
    fun route(id: String) = "identity_detail/$id"
}
```

`AppNavigation.NavHost` adds two `composable(...)` entries.

`IdentityDetail` reads `identityId` from `backStackEntry.arguments` via `navArgument(ARG_ID) { type = NavType.StringType }`.

Tap behaviors:
| Surface | Tap | Action |
|---|---|---|
| Home strip — chip | tap | `navController.navigate(IdentityDetail.route(id))` |
| Home strip — "+N more" pill | tap | `navController.navigate(IdentityList.route)` |
| YouHub identity card (whole) | tap | `navController.navigate(IdentityList.route)` |
| IdentityList — identity card | tap | `navController.navigate(IdentityDetail.route(id))` |
| IdentityDetail — back arrow | tap | `popBackStack()` |
| IdentityDetail — linked habit row | tap | inert in 5c-1 |

Both new routes are NOT in `BOTTOM_NAV_ROUTES`, so bottom nav auto-hides on these screens (matches Settings / Auth / Onboarding pattern).

Bottom-nav tab tap from IdentityList or IdentityDetail uses existing 5b-fix `popBackStack(route, inclusive=false)` first, falls back to `navigate`. Pops both Detail+List in one tap if needed.

### Refresh-on-resume

Match 5b-fix pattern for StreakHistory:
- `IdentityListViewModel.refresh()` re-runs `ObserveUserIdentitiesWithStatsUseCase`.
- `IdentityDetailViewModel.refresh()` re-runs `ComputeIdentityStatsUseCase.computeNow` + re-loads linked habits.
- Each screen wraps a `LifecycleEventObserver` collecting `ON_RESUME` and calls `viewModel.refresh()`.

### Sign-out / sign-in

No additional cleanup needed. Existing `clearAuthenticatedUserData(authUserId)` from 5b already wipes `LocalUserIdentity` + `LocalHabitIdentity`. Sign-in pull from server already covered by 5b's sync extension.

## File structure

### Created

| File | Responsibility |
|---|---|
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/IdentityStats.kt` | `IdentityStats` + `IdentityWithStats` data classes |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCase.kt` | Per-identity stats compute |
| `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ObserveUserIdentitiesWithStatsUseCase.kt` | List screen aggregate |
| `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ComputeIdentityStatsUseCaseTest.kt` | |
| `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ObserveUserIdentitiesWithStatsUseCaseTest.kt` | |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListScreen.kt` | List screen composable |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityListViewModel.kt` | |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailScreen.kt` | Detail screen composable |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/identity/IdentityDetailViewModel.kt` | |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentitySparkline.kt` | 14-cell sparkline used by IdentityList |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHeatGrid.kt` | 90-cell grid used by IdentityDetail |
| Test files for the two new ViewModels (Robolectric) | |

### Modified

| File | What changes |
|---|---|
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` | Wire `ComputeIdentityStatsUseCase` + `ObserveUserIdentitiesWithStatsUseCase` |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/Screen.kt` | Add `IdentityList`, `IdentityDetail` |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` | New `composable(...)` entries + nav arg parsing |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityChip.kt` | Wire chip onClick from inert → `(String) -> Unit` callback |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityStrip.kt` | Add `onChipClick: (Identity) -> Unit` + `onMoreClick: () -> Unit`; wire into chip + pill |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/components/IdentityHubCard.kt` | Replace stub with canvas-style rich card (gradient + stacked avatars + "I am a X, a Y" copy) |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt` | Pass new chip + pill click callbacks into `IdentityStrip` |
| `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt` | Pass card onClick callback (navigate to IdentityList) |

## Testing

### Domain (commonTest)

`ComputeIdentityStatsUseCase`:
- Single identity, single habit, multi-day logs → correct currentStreak, daysActive, habitCount.
- Identity with 0 linked habits → all-zero stats, last14Heat = `List(14) { 0 }`, last90Heat = `List(90) { 0 }`.
- 2 habits linked, partial-day log (only 1 of 2 logged) → daysActive ticks, currentStreak does NOT (matches strict rule).
- Heat formula bucket boundaries: ratio 0 → level 0, ratio 0.25 → level 1, ratio 0.50 → level 2, ratio 0.75 → level 3, ratio 1.0 → level 4.
- last14 / last90 list lengths exactly 14 / 90, oldest→newest order.
- Frozen / broken transitions match global streak semantics.

`ObserveUserIdentitiesWithStatsUseCase`:
- Combines user identities + per-identity stats; ordered by `addedAt`.
- Empty user identity set → emits empty list.
- Identity removed from user → drops out of subsequent emissions.

### UI (Robolectric — androidApp)

`IdentityListViewModel`:
- Initial collect emits `loading` then `loaded(list)` once stats computed.
- `refresh()` re-runs aggregate.

`IdentityDetailViewModel`:
- `init { load(id) }` populates state from `ComputeIdentityStatsUseCase` + `observeHabitsForIdentity`.
- Unknown / missing identity id → emits "not found" state (Identity matches no row in user's set).
- `refresh()` re-runs.

### Skipped

- No render / snapshot tests
- No live Supabase tests (no sync surface change)

### Coverage target

Match existing — domain compute fully exercised, ViewModels for state transitions. Manual smoke validates render.

## Acceptance

- Tap chip on Home → IdentityDetail opens for that identity. Stats correct.
- Tap "+N more" pill → IdentityList opens. All identities listed with per-identity stats.
- Tap YouHub card → IdentityList opens.
- Tap row in IdentityList → IdentityDetail opens.
- Back arrow on either screen pops correctly.
- Bottom-nav tab tap from either screen lands on the tab cleanly (uses 5b-fix popBackStack).
- Logging a habit on Home → re-entering IdentityList / IdentityDetail shows updated stats (refresh-on-resume).
- Identity with 0 habits → stats render as zeros, "No habits linked yet." in linked section.
- Build green; all phase 3/4/5a/5a-2/5b/5b-fix unit tests still pass; new tests pass.
- Manual smoke (light + dark): hue gradient backgrounds render correctly, heat grids look right, identity icons + hues match.
